# Raffle Service

## 개요

한정판 상품 래플(추첨) 응모·추첨·결과 통보를 전담하는 마이크로서비스입니다.

- Redis `SADD` 원자적 연산으로 대규모 동시 응모의 중복 방지
- ShedLock 기반 분산 스케줄러로 SCHEDULED→OPEN 자동 전환 및 종료 시 자동 추첨
- Transactional Outbox 패턴으로 Kafka 이벤트의 at-least-once 전송 보장
- 결제 실패 시 낙첨자 풀에서 자동 재추첨 + 노쇼 패널티 부여

---

## 포트

| 환경 | 포트 |
|------|------|
| 로컬 | **8088** |
| Swagger UI | `http://localhost:8088/swagger-ui.html` |

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Framework | Spring Boot 3.x, Spring Data JPA |
| DB | PostgreSQL 18 (Flyway 마이그레이션) |
| Cache / 중복방지 | Spring Data Redis (`SADD` Set 연산) |
| 메시지 | Apache Kafka (Transactional Outbox 패턴) |
| 분산 스케줄링 | ShedLock 5.x + Redis Provider |
| 서비스 디스커버리 | Spring Cloud Eureka Client |
| 서비스 간 통신 | OpenFeign (`payment-service` pre-auth) |
| 보안 | Spring Security (X-User-Id 헤더 기반) |
| 문서 | SpringDoc OpenAPI 2.8.x |
| 테스트 | JUnit 5, Embedded Redis, WireMock, Testcontainers |

---

## 도메인 모델

### 엔티티

| 엔티티 | 테이블 | 설명 |
|--------|--------|------|
| `Raffle` | `p_raffles` | 래플 메타 정보 (상품 ID, 기간, 당첨 인원, 상태, draw seed) |
| `RaffleEntry` | `p_raffle_entries` | 응모 기록 (billingKeyId, 쿠폰, 금액 3종) |
| `RaffleResult` | `p_raffle_results` | 추첨 결과 (WIN / LOSE / CANCELED) |
| `RafflePenalty` | `p_raffle_penalties` | 패널티 기록 (결제 불이행·어뷰징) |
| `OutboxEvent` | `p_raffle_outbox_events` | Kafka 발행 전 임시 저장 이벤트 |

### 열거형

```java
// 래플 진행 상태
enum RaffleStatus { SCHEDULED, OPEN, CLOSED }

// 추첨 결과 상태
enum RaffleResultStatus { PENDING, WIN, LOSE, CANCELED }

// Outbox 발행 상태
enum OutboxStatus { INIT, PUBLISHED, FAILED, DEAD }
```

### 에러 코드

| 코드 | HTTP | 설명 |
|------|------|------|
| RAFFLE-001 | 404 | 래플을 찾을 수 없음 |
| RAFFLE-002 | 400 | 중복 응모 |
| RAFFLE-003 | 400 | 래플이 OPEN 상태가 아님 |
| RAFFLE-004 | 400 | 잘못된 요청 |
| RAFFLE-005 | 400 | 추첨 Seed 미생성 (추첨 전) |
| RAFFLE-006 | 500 | 내부 처리 오류 |
| RAFFLE-007 | 403 | 패널티 활성 중 (응모 불가) |
| RAFFLE-008 | 500 | Redis 연산 실패 |
| RAFFLE-009 | 500 | Outbox 이벤트 직렬화 실패 |
| RAFFLE-010 | 500 | 기타 내부 오류 |

---

## API 엔드포인트

### 사용자 API (`/api/v1/raffles`)

| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| `GET` | `/api/v1/raffles` | 래플 목록 조회 (페이지) | 불필요 |
| `GET` | `/api/v1/raffles/{raffleId}` | 래플 상세 조회 | 불필요 |
| `POST` | `/api/v1/raffles/{raffleId}/entries` | 래플 응모 | `X-User-Id` 헤더 |
| `GET` | `/api/v1/raffles/entries/me` | 내 응모 목록 | `X-User-Id` 헤더 |
| `GET` | `/api/v1/raffles/{raffleId}/winners/me` | 내 추첨 결과 조회 | `X-User-Id` 헤더 |
| `GET` | `/api/v1/raffles/{raffleId}/winners` | 공개 당첨자 목록 | 불필요 |
| `GET` | `/api/v1/raffles/{raffleId}/participants-count` | 실시간 응모자 수 | 불필요 |

#### 응모 요청 Body

```json
{
  "billingKeyId": "test-billing-xxxxxxxx",
  "couponId": "uuid (optional)",
  "originalAmount": 250000,
  "discountAmount": 25000,
  "finalAmount": 225000
}
```

#### 응모 응답 Body

```json
{
  "entryId": "uuid",
  "raffleId": "uuid",
  "userId": "uuid",
  "billingKeyId": "test-billing-xxxxxxxx",
  "couponId": "uuid",
  "originalAmount": 250000,
  "discountAmount": 25000,
  "finalAmount": 225000,
  "enteredAt": "2026-07-09T10:00:00"
}
```

#### 내 결과 응답 (`RaffleResultResponse`)

```json
{
  "resultId": "uuid",
  "raffleId": "uuid",
  "userId": "uuid",
  "status": "WIN",       // PENDING | WIN | LOSE | CANCELED
  "decidedAt": "2026-07-09T18:00:00"
}
```

#### 공개 당첨자 응답 (`PublicRaffleResultResponse`)

```json
[
  {
    "userId": "uuid",
    "result": "WIN",
    "decidedAt": "2026-07-09T18:00:00"
  }
]
```

---

### 관리자 API (`/api/v1/admin/raffles`) — `ROLE_ADMIN` 필요

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `POST` | `/api/v1/admin/raffles` | 래플 생성 |
| `PUT` | `/api/v1/admin/raffles/{raffleId}` | 래플 수정 (이름, 당첨 인원) |
| `DELETE` | `/api/v1/admin/raffles/{raffleId}` | 래플 소프트 삭제 |
| `POST` | `/api/v1/admin/raffles/{raffleId}/status` | 상태 강제 변경 (SCHEDULED/OPEN/CLOSED) |
| `POST` | `/api/v1/admin/raffles/{raffleId}/draw` | 수동 추첨 실행 |
| `GET` | `/api/v1/admin/raffles/{raffleId}/entries` | 응모자 목록 조회 (페이지) |
| `POST` | `/api/v1/admin/raffles/{raffleId}/entries/{userId}/penalty` | 사용자 패널티 부여 (30일) |

#### 래플 생성 요청 Body

```json
{
  "productId": "uuid",
  "name": "Nike Dunk Low 래플",
  "winnerCount": 10,
  "startedAt": "2026-07-10T10:00:00",
  "endedAt": "2026-07-12T23:59:59"
}
```

---

## 서비스 레이어 구조

```
application/service/
├── RaffleAppService       — 사용자 응모 처리 (Redis 중복 검증 → Feign pre-auth → DB 저장)
├── AdminRaffleAppService  — CRUD, 상태 변경, 응모자 조회, 패널티 부여
├── RaffleDrawService      — 추첨 로직 (셔플 → 결과 Bulk Insert → Outbox 이벤트 발행)
└── RaffleResultService    — 개인 결과 조회, 공개 당첨자 목록 조회
```

---

## 응모 플로우

```
사용자 POST /entries
    │
    ├─ 1. 래플 상태 검증 (OPEN 여부)
    ├─ 2. 패널티 활성 여부 검증
    ├─ 3. Redis SADD → 중복 응모 방지 (원자적)
    ├─ 4. RaffleEntry 객체 생성 (in-memory)
    └─ 5. CompletableFuture (비동기)
            ├─ payment-service pre-auth (Feign, 100원 예비 인증)
            ├─ DB INSERT (raffleEntryRepository.save)
            └─ 실패 시 Redis SREM (롤백)
```

> **설계 의도**: Redis 선행 체크로 RDBMS Race Condition을 방지하고, DB 저장은 비동기로 처리해 응답 지연을 최소화합니다.

---

## 추첨 플로우

```
관리자 POST /draw  또는  RaffleScheduler (자동)
    │
    ├─ 1. 래플 조회 + 낙관적 상태 변경 (OPEN → CLOSED, updateStatusIfOpen)
    ├─ 2. 응모 목록 Projection 조회 (OOM 방지)
    ├─ 3. Random Seed 생성 → Collections.shuffle
    ├─ 4. winnerCount 기준으로 WIN / LOSE 분류
    ├─ 5. RaffleResult Bulk Insert
    ├─ 6. WIN → RaffleWinnerSelectedEvent 발행 (Outbox)
    └─ 7. LOSE → RaffleLoserNotifiedEvent 발행 (Outbox)
```

---

## 결제 실패 재추첨 플로우

```
payment-failure Kafka 이벤트 수신 (PaymentFailureEventConsumer)
    │
    ├─ 1. 해당 유저 RaffleResult → CANCELED
    ├─ 2. 노쇼 패널티 7일 부여
    ├─ 3. LOSE 풀에서 랜덤 후보자 1명 선택 (Native Query)
    ├─ 4. 해당 RaffleResult → WIN 변경
    └─ 5. RaffleWinnerSelectedEvent 재발행 (Outbox)
```

---

## 스케줄러

| 스케줄러 | 주기 | 역할 |
|----------|------|------|
| `RaffleScheduler.scheduleRaffleOpen` | 매 분 | `SCHEDULED` → `OPEN` 자동 전환 |
| `RaffleScheduler.scheduleRaffleDraw` | 매 분 | 종료된 `OPEN` 래플 자동 추첨 |
| `OutboxPollerScheduler.pollAndPublishOutboxEvents` | 5초 | `INIT/FAILED` Outbox 이벤트 Kafka 발행 |

> 모든 스케줄러는 **ShedLock** (Redis Provider) 으로 분산 환경 중복 실행을 방지합니다.

---

## Kafka 토픽

| 토픽 | 방향 | 설명 |
|------|------|------|
| `raffle.winner.selected` | Producer (Outbox) | 당첨자 선정 → order-service, notification-service |
| `raffle.loser.notified` | Producer (Outbox) | 낙첨자 통보 → notification-service |
| `payment.failed` | Consumer | 결제 실패 → 재추첨 트리거 |

---

## Redis 키 구조

| 키 | 자료구조 | TTL | 용도 |
|----|----------|-----|------|
| `raffle:{raffleId}:entries` | Set | 7일 | 응모자 userId Set (SADD 중복 방지 + SCARD 참가자 수) |

---

## 외부 서비스 연동

| 서비스 | 방식 | 엔드포인트 | 용도 |
|--------|------|-----------|------|
| `payment-service` | OpenFeign (Fallback 포함) | `POST /internal/v1/payments/pre-auth` | 응모 시 100원 예비 결제 인증 |

---

## 패키지 구조

```
raffle-service/
├── application/
│   ├── event/
│   │   ├── consumer/         — PaymentFailureEventConsumer (Kafka)
│   │   └── producer/         — RaffleWinnerSelectedEvent, RaffleLoserNotifiedEvent
│   │                            RaffleWinnerSelectedEventListener (Outbox 저장)
│   ├── port/out/             — EventProducerPort (인터페이스)
│   ├── scheduler/            — RaffleScheduler, OutboxPollerScheduler
│   └── service/              — RaffleAppService, AdminRaffleAppService
│                               RaffleDrawService, RaffleResultService
├── domain/
│   ├── entity/               — Raffle, RaffleEntry, RaffleResult, RafflePenalty, OutboxEvent
│   ├── enums/                — RaffleStatus, RaffleResultStatus, OutboxStatus, RaffleErrorCode
│   ├── exception/            — RaffleNotFoundException, DuplicateEntryException 등
│   ├── projection/           — RaffleEntryProjection (추첨 시 OOM 방지용)
│   └── repository/           — JPA 인터페이스 5종
├── infrastructure/
│   ├── client/               — PaymentFeignClient, PaymentFeignClientFallback
│   ├── config/               — RedisConfig, FeignConfig, SecurityConfig, ShedLockConfig
│   ├── kafka/                — KafkaEventProducerAdapter
│   └── redis/                — RaffleEntryRedisRepository
└── presentation/
    ├── controller/           — RaffleController (사용자)
    │   └── admin/            — AdminRaffleController (관리자)
    └── dto/
        ├── request/          — RaffleEnterRequest, RaffleApplyRequest
        │   └── admin/        — AdminRaffleCreateRequest, AdminRaffleUpdateRequest, AdminRaffleStatusUpdateRequest
        └── response/         — RaffleResponse, RaffleApplyResponse, RaffleEntryResponse
                                RaffleResultResponse, PublicRaffleResultResponse
```

---

## 테스트

```bash
# 단위 테스트 + 통합 테스트
./gradlew :raffle-service:test

# 주요 테스트 파일
RaffleAppServiceTest          — 응모 단위 테스트
RaffleDrawServiceTest         — 추첨 로직 단위 테스트
RaffleResultServiceTest       — 결과 조회 단위 테스트
RaffleConcurrencyTest         — 동시 응모 동시성 테스트
RaffleSchedulerTest           — 스케줄러 단위 테스트
OutboxPollerSchedulerTest     — Outbox 폴러 단위 테스트
RaffleControllerTest          — 컨트롤러 슬라이스 테스트
AdminRaffleControllerTest     — 관리자 컨트롤러 슬라이스 테스트
RaffleServiceArchTest         — ArchUnit 레이어 의존성 테스트
PaymentClientTest             — WireMock Feign 테스트
KafkaEventProducerAdapterTest — EmbeddedKafka 통합 테스트
RaffleEntryRedisRepositoryTest— EmbeddedRedis 통합 테스트
```

---

## 핵심 설계 원칙

1. **Redis 선행 → RDBMS 후처리**: 중복 응모는 Redis `SADD` 원자적 연산으로 1차 방어, DB Unique 제약은 최후 방어선으로 사용
2. **Transactional Outbox**: Kafka 직접 발행 대신 DB Outbox 테이블에 저장 후 폴러가 발행 — 메시지 유실 없는 at-least-once 보장
3. **ShedLock 분산 잠금**: 다중 인스턴스 환경에서 스케줄러 중복 실행 방지
4. **Soft Delete 전역 적용**: `@SQLRestriction("deleted_at IS NULL")` + `@SQLDelete`로 논리 삭제 일관성 유지
5. **SRP 서비스 분리**: 응모(`RaffleAppService`) / 추첨(`RaffleDrawService`) / 결과(`RaffleResultService`) 책임 분리
