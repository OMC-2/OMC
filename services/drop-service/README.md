# drop-service

> 한정판 드롭 플랫폼의 **선착순 진입 관문**.
> 드롭 오픈 순간 폭주하는 트래픽을 Redis Lua 원자 연산으로 통제하고,
> 동기 응답(202) 이후의 처리는 Kafka로 비동기 위임합니다.

---

## 1. 책임 범위

| 담당 | 비담당 (다른 서비스) |
| --- | --- |
| 드롭 생성·일정·상태 전이 (`SCHEDULED → OPEN → CLOSED`) | 응모·추첨 → `raffle-service` |
| 선착순 진입 판정 (재고 선점, 1인 1구매, 대기 순번) | DB 확정 차감 → `product-service` |
| Redis 재고·hold 생명주기 (워밍 → 선점 → 회수) | 주문 상태 머신 → `order-service` |
| `drop.*`, `purchase.confirmed`, `hold.expired` 발행 | 결제·환불 → `payment-service` |
| (재고 복구만 담당, 쿠폰에 관여 안 함) | 쿠폰 상태·복구 → `coupon-service` |

> 설계 원칙: **드롭이 열려 있는 동안 재고의 진실은 Redis 하나.** DB는 사후 정산 원장이며, 드롭 진행 중 DB 재고를 실시간 조회하지 않는다.

---

## 2. 핵심 흐름

### 2-1. 드롭 생명주기

```mermaid
stateDiagram-v2
    [*] --> SCHEDULED : 관리자 생성
    SCHEDULED --> OPEN : 스케줄러 (5초 폴링)
    OPEN --> CLOSED : 스케줄러 (endAt 도달)
    CLOSED --> [*] : 정산 후 Redis 키 정리
```

OPEN 전이는 반드시 다음 순서를 지킨다. DB 업데이트를 먼저 수행해 멀티 인스턴스 중복 전이를 차단하고, Redis 워밍은 이후에 진행한다.

```
① product-service 재고 스냅샷 조회 (GET /internal/v1/products/{productId}/inventories/snapshot)
   → availableQuantity 사용 / product-service 장애 시 드롭 생성 시 저장한 totalQty로 폴백
   → 폴백 발생 시 drop.open.inventory.fallback 메트릭 기록
② 조건부 UPDATE (WHERE status='SCHEDULED')  ← 인스턴스가 여러 대여도 전이는 1회
③ Redis 워밍 — warmup.lua로 4개 키를 원자적으로 초기화
   status 키 EXISTS 체크 → 이미 OPEN이면 덮어쓰지 않음 (멱등, 멀티 인스턴스 재고 보호)
   stock:{dropId}      = availableQuantity
   drop:{dropId}:status = "OPEN"
   hold_ttl:{dropId}   = holdTtlSec   ← 진입 경로 DB 무접촉을 위한 캐싱
   product_id:{dropId} = productId    ← purchase.confirmed 이벤트 조립용 캐싱
   → 워밍 실패 시 DB는 이미 OPEN — HoldExpireScheduler.recoverFromDb()가 다음 주기에 Redis 복구
④ open_drops SADD {dropId}  ← HoldExpireScheduler가 DB 조회 없이 참조
⑤ drop.opened 발행
```

### 2-2. 선착순 진입 (`POST /drops/{id}/purchase`)

```mermaid
sequenceDiagram
    participant U as 사용자
    participant G as Gateway
    participant D as drop-service
    participant R as Redis
    participant S as Redis Stream
    participant K as Kafka

    U->>G: POST /drops/{id}/purchase
    G->>G: Rate Limiter (dropId 기준 200 req/s)
    alt 한도 초과
        G-->>U: 429 Too Many Requests
    end
    G->>D: 전달
    D->>R: Lua 원자 처리 1회 (OPEN 확인·holdTtlSec·productId 조회·중복·재고·hold·순번·XADD 통합)
    alt OK
        Note over R,S: 선점과 Stream 기록이 Lua 한 블록 안에서 원자적으로 처리됨
        D-->>U: 202 Accepted (orderId, queueNumber)
        Note over S,K: PurchaseStreamWorker(100ms)가 XREADGROUP → Kafka 발행 → XACK
        S->>K: purchase.confirmed 발행 (key=orderId)
    else SOLD_OUT / DUPLICATE / NOT_OPEN
        D-->>U: 409
    end
    Note over K: 주문 생성·DB 차감은 비동기 (order / product)
    Note over S: Kafka 장애 시 XACK 안 함 → 재시작·다른 인스턴스가 pending 재처리
```

### 2-3. Redis 키 정리 (`DropRedisCleanupScheduler`)

CLOSE 직후 바로 삭제하지 않는다. hold TTL(10분) + 늦은 결제 이벤트까지 고려해 충분한 시간이 지난 뒤 정리한다.

```
조건 (AND):
① status == CLOSED
② endAt >= now - 2일  ← 2일 이내 종료된 드롭만 조회 (무제한 증가 방지)
③ endAt + 1시간 < now ← hold TTL 10분 + 여유 버퍼
④ ZCARD holds:{dropId} == 0  ← 대기 중인 hold 없음

→ 모두 만족하면 DEL:
   stock, purchased, holds, queue, hold_ttl, product_id (6개)
   (status는 CLOSE 시 이미 삭제)
```

> 멀티 인스턴스 환경에서 중복 실행되더라도 `DEL`은 멱등이라 안전하다.  
> 실제 삭제된 키 수가 0이면 로그를 찍지 않아 이미 정리된 드롭의 로그 노이즈를 방지한다.

### 2-4. hold 생명주기 — 결제 vs TTL 만료 경합

선점 후 `holdTtlSec`(기본 600초 / 10분) 내 미결제 시 선점을 회수한다.
심판은 `ZREM` 반환값 — **먼저 지운 쪽이 승리**하며 별도 락이 필요 없다.

| 케이스 | 트리거 | 처리 |
| --- | --- | --- |
| 정상 결제 | `payment.completed` 수신 → 즉시 ZREM | 선점 확정 (정상 구매) |
| **결제 실패** | **`payment.failed` 수신 → 즉시 ZREM + INCR + SREM (Lua)** | **재고 즉시 복구 → 다음 사용자 선점 가능** |
| TTL 만료 (미결제) | TTL 폴링이 먼저 ZREM | 재고 INCR 복구 + `hold.expired` 발행 |
| 만료 후 결제 뒤늦게 완료 | ZREM=0 감지 | `refund.requested` 발행 → 자동 환불 |

> `payment.failed` 즉시 복구 근거: TTL(600초) 만료까지 대기하면 그 시간 동안 재고가 불필요하게 묶여 다른 사용자의 선점이 차단됨. 실패 이벤트 수신 즉시 복구하여 재고 가용성을 최대화한다.

---

## 3. API 명세

| Method | Path | 권한 | 설명 | 응답 |
| --- | --- | --- | --- | --- |
| GET | `/admin/drops` | ADMIN | 전체 목록 조회 — 소프트딜리트 포함, 최신순 20개 | 200 |
| GET | `/admin/drops/{dropId}` | ADMIN | 단건 조회 — 소프트딜리트 포함 | 200 |
| POST | `/admin/drops` | ADMIN | 드롭 생성 (선착순 DROP 전용) | 201 |
| PUT | `/admin/drops/{dropId}` | ADMIN | 수정 — `SCHEDULED` 상태에서만 | 200 |
| POST | `/admin/drops/{dropId}/close` | ADMIN | 강제 종료 — `OPEN` 상태에서만 | 204 |
| DELETE | `/admin/drops/{dropId}` | ADMIN | 삭제 — `SCHEDULED` 상태에서만 (소프트딜리트) | 204 |
| GET | `/drops?status=&page=` | GUEST | 목록 (Look-aside 캐싱) | 200 |
| GET | `/drops/{dropId}` | GUEST | 상세 — 잔여 수량은 Redis 카운터로 응답 | 200 |
| POST | `/drops/{dropId}/purchase` | USER | **선착순 진입 (부하 테스트 대상)** | 202 |
| GET | `/drops/{dropId}/purchase/me` | USER | 내 접수 상태·대기 순번 (P2) | 200 |

진입 API 실패 코드: `DROP_NOT_OPEN`(409) · `SOLD_OUT`(409) · `DUPLICATE_PURCHASE`(409) · `DROP_NOT_FOUND`(404)

### Internal API (서비스 간 전용, `/internal/**` 인증 제외)

| Method | Path | 제공처 | 설명 | 응답 |
| --- | --- | --- | --- | --- |
| GET | `/internal/v1/drops/products/{productId}/active` | drop-service | 해당 상품의 활성 드롭(SCHEDULED·OPEN) 존재 여부 | `{ hasActiveDrop: true }` |

> product-service가 상품 삭제·수정 전 활성 드롭 여부를 확인하기 위해 호출한다.

---

## 4. 데이터

### PostgreSQL (`drop_db` 스키마)

```sql
drops (
  drop_id      UUID         NOT NULL DEFAULT gen_random_uuid(),  -- PK
  product_id   UUID         NOT NULL,  -- FK 없는 ID 참조 (서비스 경계)
  status       VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',  -- SCHEDULED | OPEN | CLOSED
  start_at     TIMESTAMPTZ  NOT NULL,
  end_at       TIMESTAMPTZ  NOT NULL,
  total_qty    INT          NOT NULL,
  hold_ttl_sec INT          NOT NULL DEFAULT 600,  -- 선점 유지 시간 (기본 10분)
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  created_by   UUID         NOT NULL,  -- 생성자 userId
  updated_at   TIMESTAMPTZ,
  updated_by   UUID,                   -- 수정자 userId
  deleted_at   TIMESTAMPTZ,            -- 소프트딜리트 일시 (NULL = 정상)
  deleted_by   UUID,                   -- 소프트딜리트 처리자 userId

  CONSTRAINT pk_drops          PRIMARY KEY (drop_id),
  CONSTRAINT chk_drops_status  CHECK (status IN ('SCHEDULED', 'OPEN', 'CLOSED')),
  CONSTRAINT chk_drops_qty     CHECK (total_qty > 0),
  CONSTRAINT chk_drops_period  CHECK (end_at > start_at)
)
-- INDEX (status, start_at), (status, end_at) : 스케줄러 폴링용
-- 래플은 raffle-service의 p_raffles 테이블로 완전 분리
-- BaseEntity 상속 + @SQLRestriction("deleted_at IS NULL"): JPA 쿼리에서 소프트딜리트 행 자동 제외

processed_events (
  event_id     VARCHAR(100) PK,  -- Consumer 멱등
  topic        VARCHAR(100) NOT NULL,
  processed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
)
```

### Redis

| 키 | 타입 | 용도 | 생성 / 소멸 |
| --- | --- | --- | --- |
| `stock:{dropId}` | String | 남은 수량 카운터 | 워밍 Lua SET (status EXISTS 가드) / 정산 후 DEL |
| `purchased:{dropId}` | Set | 1인 1구매 차단 (userId) | Lua SADD / 정산 후 DEL |
| `holds:{dropId}` | ZSet | orderId → 만료 epoch | Lua ZADD / 결제·만료 시 ZREM |
| `queue:{dropId}` | String | 접수 순번 카운터 | Lua INCR / 정산 후 DEL |
| `drop:{dropId}:status` | String | OPEN 플래그 (fail-fast) | 전이 시 Lua SET (EXISTS 가드) / 종료 시 즉시 DEL |
| `hold_ttl:{dropId}` | String | 선점 유지 시간(초) 캐시 | 워밍 Lua SET (status EXISTS 가드) / 정산 후 DEL |
| `product_id:{dropId}` | String | productId 캐시 (이벤트 조립용) | 워밍 Lua SET (status EXISTS 가드) / 정산 후 DEL |
| `sold_out:{dropId}` | String | 품절 플래그 (Gateway 조기 차단용) | Lua DECR 후 재고 0 도달 시 SET / hold 만료·복구 시 DEL |
| `open_drops` | Set | OPEN 드롭 ID 목록 (HoldExpireScheduler DB 조회 대체) | OPEN 전이 시 SADD / holds 소진 후 SREM |

---

## 5. Kafka

### 전체 이벤트 흐름 (프로젝트 공통)

| 토픽 | 발행 서비스 | 구독 서비스 | 트리거 |
| --- | --- | --- | --- |
| `drop.opened` | drop-service | notification-service | SCHEDULED → OPEN 전이 |
| `drop.closed` | drop-service | — | OPEN → CLOSED 전이 (raffle-service 자체 스케줄러로 독립 처리) |
| `purchase.confirmed` | drop-service | order-service | 선점 성공 |
| `hold.expired` | drop-service | order-service, coupon-service | TTL 만료 회수 |
| `refund.requested` | drop-service | payment-service | ZREM=0 감지 (만료 후 결제) |
| `payment.completed` | payment-service | drop-service, product-service, order-service, coupon-service | 결제 완료 (coupon: RESERVED→USED) |
| `payment.failed` | payment-service | drop-service, order-service, coupon-service | 결제 실패 (coupon: RESERVED→AVAILABLE) |
| `refund.done` | payment-service | coupon-service, notification-service | 환불 완료 (refundReason 분기: SYSTEM_STOCK_FAILED만 쿠폰 복구) |
| `stock.deducted` | product-service | order-service | DB 재고 확정 차감 완료 |
| `stock.failed` | product-service | drop-service, payment-service, order-service | DB 재고 차감 실패 |
| `order.created` | order-service | — | 주문 생성 |
| `order.confirmed` | order-service | notification-service | 주문 확정 |
| `order.cancelled` | order-service | notification-service | 주문 취소 |
| `order.shipped` | order-service | notification-service | 배송 시작 |
| `raffle.winner.selected` | raffle-service | order-service, product-service, notification-service | 추첨 당첨 |
| `raffle.loser.notified` | raffle-service | payment-service, notification-service | 추첨 낙첨 |
| `coupon.issued` | coupon-service | notification-service | 쿠폰 발급 |
| `coupon.used` | coupon-service | — | 쿠폰 사용 (RESERVED→USED 결과, 통계용) |

---

### drop-service 발행

| 토픽 | 파티션 키 (수) | 트리거 | payload |
| --- | --- | --- | --- |
| `drop.opened` | dropId (1) | 상태 전이 SCHEDULED → OPEN | `eventId`, `dropId`, `startAt`, `endAt`, `totalQty` |
| `drop.closed` | dropId (1) | 상태 전이 OPEN → CLOSED | `eventId`, `dropId` |
| `purchase.confirmed` | **orderId (3)** | 선점 성공 → 주문 생성 트리거 | `eventId`, `orderId`, `dropId`, `userId`, `productId`, `holdExpiresAt` |
| `hold.expired` | orderId (1) | TTL 만료 → 주문 취소 트리거 | `eventId`, `orderId`, `dropId` |
| `refund.requested` | orderId (1) | ZREM=0 감지 (만료 후 결제) → 자동 환불 트리거 | `eventId`, `orderId`, `userId`, `reason` |

> 모든 이벤트 payload 첫 필드에 `eventId` 포함 — Consumer가 `processed_events`로 멱등성 체크

### drop-service 구독

| 토픽 | 처리 | 멱등 |
| --- | --- | --- |
| `payment.completed` | ① `salesType != DROP` 스킵 ② ZREM holds → 반환값 1=정상·0=LATE_PAYMENT ③ ZREM=0이면 `refund.requested` 발행 | ZREM 자체가 멱등 |
| `payment.failed` | ① `salesType != DROP` 스킵 ② **즉시** ZREM + INCR + SREM 복구 (Lua) — TTL 대기 없이 재고 즉시 반환 | `processed_events` 필수 — INCR은 멱등이 아님 |
| `stock.failed` | 동일 복구 Lua (ZREM + INCR + SREM) — payment, order와 병렬 구독 | `processed_events` 필수 |

> `dropId` 전달 경로: `purchase.confirmed`(drop) → `order.created`(order) → `payment.completed` / `payment.failed`(payment) → drop 복구. RAFFLE은 `dropId`가 null이나 `salesType` 분기로 스킵.

---

## 6. 핵심 설계 결정 (ADR 요약)

| # | 결정 | 근거 |
| --- | --- | --- |
| 1 | 동시성 제어를 분산 락(Redisson)이 아닌 **Lua Script**로 | 락은 대기·재시도 오버헤드 발생. 단일 스레드 Redis에서 Lua는 락 없이 원자성 확보 |
| 2 | `purchase.confirmed` 파티션 키를 dropId가 아닌 **orderId**로 | 트래픽이 단일 인기 드롭에 집중 → dropId 키는 핫 파티션(병렬성 0). 순서 보장은 포기하되 정합성은 멱등 처리 + DB가 담당 |
| 3 | 진입 API에 **DB Outbox 미적용 → Redis Stream Outbox** 채택 | 진입 경로가 DB 무접촉이라 DB Outbox는 오히려 DB I/O 추가 및 병목 재발생. Redis Stream을 Outbox로 사용하면 purchase.lua의 선점 연산과 XADD가 같은 Lua 블록 안에서 원자적으로 처리되어 DB 무접촉 원칙을 유지하면서 이벤트 유실도 방지. PurchaseStreamWorker가 XREADGROUP → Kafka 발행 → XACK 흐름으로 at-least-once 보장. 서버 재시작 시 pending 메시지를 자동 재처리하고, 멀티 인스턴스 환경에서는 XCLAIM으로 dead consumer의 메시지를 인수. 재시도 한도(5회) 초과 메시지는 `stream:purchase:failed`에 보관 — Kafka 장애가 원인일 수 있어 Kafka DLT 대신 Redis Stream 선택 |
| 4 | TTL 감지를 Keyspace Notification이 아닌 **폴링**으로 | 만료 이벤트는 유실 가능성 존재. 폴링은 재실행 가능해 견고하며, hold.expired Consumer 멱등이 전제 |
| 5 | 상태 전이를 **조건부 UPDATE**로 멱등화 | 스케줄러 다중 인스턴스 환경에서도 전이·워밍·발행이 정확히 1회 |
| 6 | `payment.failed` 수신 시 **TTL 대기 없이 즉시 복구** | TTL(600초) 대기 시 그 시간 동안 재고 불필요하게 차단. 즉시 ZREM+INCR+SREM으로 재고 반환 → 다음 사용자 선점 가능 시간 최소화 |
| 7 | order-service에 **no-op 처리 협의** | 인메모리 아웃박스 사용으로 극히 드문 경우 purchase.confirmed 유실 가능. order-service가 ① purchase.confirmed 멱등 처리 ② hold.expired 수신 시 주문 없으면 no-op 처리하도록 협의 완료 |
| 8 | **DROP / RAFFLE 테이블 분리** | drop_type 컬럼으로 통합 시 RAFFLE 전용 컬럼(winner_count 등)이 DROP 행에 NULL로 쌓이고, 래플 담당자(raffle-service)와 스키마 소유권이 충돌. 드롭서비스는 DROP 전용 `drops` 테이블만 소유하고, 래플은 raffle-service의 `raffles` 테이블로 완전 분리 |
| 9 | **product-service 호출에 Feign + Resilience4j + FallbackFactory** 적용 | 드롭 오픈 스케줄러가 재고 스냅샷 조회 시 product-service 장애가 드롭 오픈을 막지 않도록 설계. 서킷 오픈 시 FallbackFactory가 예외 throw → 스케줄러 catch → totalQty 폴백으로 드롭 오픈 유지 |
| 10 | **진입 API Redis 호출을 Lua 1회로 통합** | 기존 isOpen GET + holdTtlSec GET + productId GET + EVALSHA = 4 round-trips. Redis 단일 스레드 특성상 400 스레드 동시 접근 시 직렬화 대기가 avg 200ms까지 누적됨. 3개의 개별 GET을 Lua 내부로 이동해 1 round-trip으로 축소 |
| 11 | **Gateway Rate Limiter (dropId 기준 200 req/s)** 추가 | 1000 VU 동시 유입 시 Tomcat thread pool(200) 포화로 p99가 30~37s까지 치솟는 문제 확인. Redis Token Bucket 기반 Rate Limiter로 유입량을 thread pool 이내로 억제. 초과 요청은 Tomcat 도달 전 게이트웨이에서 즉시 429 반환 |
| 12 | **Gateway SoldOutCheckFilter — 품절 후 즉시 409 차단** | 재고 소진 후에도 요청이 Tomcat 큐까지 도달해 ~28s 대기 후 409를 받는 문제 확인. purchase.lua에서 DECR 후 재고 0 도달 시 `sold_out:{dropId}` 플래그 SET. Gateway GlobalFilter(order=-10)가 플래그 확인 → drop-service 호출 없이 56ms 즉시 409 반환. hold 만료·결제 취소 시 재고 복구와 함께 플래그 DEL |

---

## 7. 부하 테스트

- 대상: `POST /drops/{dropId}/purchase`
- 측정 환경: 로컬 Docker (MacBook, DB·Redis·Kafka 동일 머신)
- 도구: JMeter (초기 검증) → k6 (시나리오 자동화)

### 7-1. JMeter (초기 검증)

JWT 대량 발급이 비현실적이므로 X-User-Id 헤더로 userId를 직접 주입하는 방식으로 인증을 우회한다.

**사전 준비**
```bash
# 매 테스트 전 Redis 리셋
redis-cli DEL stock:{dropId} purchased:{dropId} holds:{dropId} queue:{dropId}
redis-cli SET stock:{dropId} 100
redis-cli SET drop:{dropId}:status OPEN
```

**시나리오**

| 시나리오 | 스레드 | 검증 |
| --- | --- | --- |
| 재고 정확성 | 1,000 (Ramp-up 0초) | 202 == 100, 409 == 900 |
| 중복 방지 | 10스레드 × 10루프 | userId당 202 == 1 |

**성능 측정 결과**

| 버전 | 구현 | TPS | p99 (ms) |
| --- | --- | --- | --- |
| v1 | DB 비관적 락 | `(측정)` | `(측정)` |
| v2 | Redis Lua | `(측정)` | `(측정)` |

### 7-2. k6 (시나리오 자동화)

실제 JWT 토큰을 사용하며 시나리오별 검증 지표가 자동으로 출력된다.

**파일 구조**
```
k6/
├── users.json              # 테스트 유저 토큰 목록
├── refresh-tokens.js       # 토큰 갱신
├── 02-product-inventory.js # A(구매 선점) · B(재고 차감) · C(중복 방지) · D(캐시) 시나리오
└── 03-hold-expire.js       # Hold TTL 만료 후 재고 복구 2-Phase 검증
```

**실행**
```bash
# 시나리오 A — 구매 선점 (1,000 VU)
k6 run -e DROP_ID=<id> k6/02-product-inventory.js

# 시나리오 C — 중복 방지
k6 run -e SCENARIO=duplicate -e DROP_ID=<id> k6/02-product-inventory.js

# 시나리오 E — Hold 만료 재고 복구
k6 run -e DROP_ID=<id> -e HOLD_TTL_SEC=15 -e HOLD_COUNT=50 k6/03-hold-expire.js
```

**성능 측정 결과 (1,000 VU · 재고 100개)**

| 최적화 단계 | 성공 avg | 전체 p(90) | req/s |
| --- | --- | --- | --- |
| Redis Lua 기본 | 12s | 37s | 26 |
| Lua 4→1 round-trip 통합 | 5s | 30s | 48 |
| + Gateway Rate Limiter (200 req/s) | 4~5s | 23~28s | 48~168 |
| + Gateway 품절 조기 차단 (SoldOutCheckFilter) | 1s | 8.5s | 297 |

---

## 8. 실행

```bash
# 인프라 (프로젝트 루트)
docker compose up -d        # kafka(9092), redis, postgresql, kafka-ui(8989)

# 서비스
./gradlew :drop-service:bootRun
```

| 환경 변수 | 기본값 |
| --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/drop_db` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `SPRING_DATA_REDIS_HOST` | `localhost` |

---

## 9. 패키지 구조

```
drop-service
└── src/main/java/com/omc/drop
    ├── presentation
    │   ├── controller       # DropController, DropAdminController, DropInternalController
    │   └── dto              # request/, response/
    ├── application
    │   ├── service          # DropAdminService, DropQueryService, HoldService, PurchaseService
    │   ├── scheduler        # DropStatusScheduler, HoldExpireScheduler, DropRedisCleanupScheduler
    │   └── event
    │       ├── producer     # DropEventProducer (Kafka 이벤트 발행)
    │       ├── consumer     # DropEventConsumer (payment.completed/failed, stock.failed 수신)
    │       └── stream       # PurchaseStreamWorker (Redis Stream → Kafka 발행)
    ├── domain
    │   ├── entity           # Drop, DropProcessedEvent
    │   ├── enums            # DropStatus
    │   ├── repository       # DropRepository, DropProcessedEventRepository (JPA 인터페이스)
    │   └── exception        # 도메인 예외, DropErrorCode
    └── infrastructure
        ├── redis            # DropRedisStore (상태·hold·warmup), PurchaseStreamStore (Redis Stream)
        ├── kafka            # KafkaConfig (DLT 에러 핸들러)
        ├── client           # ProductServiceClient (Feign), dto/
        └── config           # SecurityConfig, JpaConfig, RedisConfig
```
