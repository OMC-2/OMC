# OMC (ONE MORE CHANCE)

한정판 드롭·래플 커머스 플랫폼 — Spring Boot 기반 MSA 프로젝트

---

## 기술 스택

| 분류 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.4.5, Spring Cloud 2024.0.1 |
| Service Mesh | Eureka (서비스 디스커버리), Spring Cloud Gateway, Spring Cloud Config |
| Auth | Spring Security 6, Keycloak 26, JWT (jjwt 0.12.3) |
| Messaging | Apache Kafka (Spring Kafka) |
| Persistence | Spring Data JPA, PostgreSQL 18, Flyway |
| Cache / 재고 | Spring Data Redis |
| Monitoring | Prometheus, Grafana |
| Build | Gradle 9 (Groovy DSL) |
| Test | Karate DSL (E2E), JUnit 5 |

---

## 아키텍처

```
Client
  │
  ▼
[Gateway :8080]  ─── JWT 검증, 라우팅, X-Gateway-Secret 필터
  │
  ├── [user-service         :8081]  회원가입, 로그인, 주소 관리
  ├── [drop-service         :8082]  드롭 생성/스케줄링, 구매 선점 (Redis 재고)
  ├── [order-service        :8083]  주문 상태 관리, DLQ 운영
  ├── [product-service      :8084]  상품 정보, DB 재고 차감
  ├── [payment-service      :8085]  결제 승인/취소, SAGA Orchestration
  ├── [raffle-service       :8086]  래플 응모, 추첨, 당첨자 결제 연동
  ├── [coupon-service       :8087]  쿠폰 발급, 선점(RESERVED), 사용(USED)
  └── [notification-service :8088]  Kafka 이벤트 기반 알림 발송

[eureka-server :8761]  서비스 디스커버리
[config-server :8888]  중앙 설정 (classpath:/config/*.yml)
[Kafka         :9092]  비동기 이벤트 버스
[Redis         :6379]  재고 선점, 캐시, 멱등성 키
[PostgreSQL    :5432]  스키마별 영속성 (omc DB 단일 인스턴스)
[Keycloak      :8180]  OAuth2 / JWT 발급
```

---

## 모듈 구성

| 모듈 | 포트 | 역할 |
|---|---|---|
| `common` | — | 공통 응답 DTO, 이벤트 정의, 예외 코드 |
| `arch-rules` | — | ArchUnit 아키텍처 규칙 |
| `e2e` | — | Karate DSL E2E 테스트 |
| `eureka-server` | 8761 | 서비스 레지스트리 |
| `config-server` | 8888 | 중앙 설정 서버 |
| `gateway` | 8080 | API 게이트웨이, JWT 필터 |
| `user-service` | 8081 | 회원, 인증, 주소 |
| `drop-service` | 8082 | 드롭 이벤트, 구매 선점 |
| `order-service` | 8083 | 주문 상태 FSM, DLQ |
| `product-service` | 8084 | 상품, 재고 차감 |
| `payment-service` | 8085 | 결제, 환불, SAGA |
| `raffle-service` | 8086 | 래플 응모, 추첨 |
| `coupon-service` | 8087 | 쿠폰 발급·선점·확정 |
| `notification-service` | 8088 | 알림 |

---

## 핵심 플로우

### 드롭 구매 SAGA

```
유저: 구매 선점 (POST /drops/{id}/purchase)
  └─ drop-service:
       ① Lua 원자 처리 — OPEN 확인 + 재고 차감 + orderId hold 등록 (Redis 1 round-trip)
       ② DB 트랜잭션 — p_drop_purchase_reservations + p_drop_outbox_events 동시 저장
          └─ 저장 실패 시 Redis 선점 보상(최대 3회 retry) → 실패 지속 시 CRITICAL 메트릭
       └─ 202 Accepted (orderId, queueNumber) 반환
  └─ DropOutboxPoller (2초 주기, FOR UPDATE SKIP LOCKED)
       └─ p_drop_outbox_events INIT 레코드 → publish: purchase.confirmed

유저: 결제 승인 (POST /internal/v1/payments/confirm)
  └─ payment-service:
       ├─ coupon-service Feign: POST /internal/v1/coupons/reserve (AVAILABLE→RESERVED)
       ├─ PG 결제 승인
       └─ publish: payment.completed

payment.completed 구독
  ├─ product-service: DB 재고 차감 → publish: stock.deducted
  ├─ coupon-service: RESERVED → USED
  └─ order-service: stock.deducted 수신 → 주문 확정 → publish: order.confirmed
       └─ notification-service: ORDER_CONFIRMED 알림 발송
```

### 래플 SAGA

```
유저: 래플 응모 (POST /raffles/{id}/entries)
  └─ raffle-service: 응모 등록, 빌링키 결제

어드민: 추첨 실행 (POST /admin/raffles/{id}/draw)
  └─ publish: raffle.winner.selected / raffle.loser.notified
       ├─ 당첨: order-service, product-service, notification-service
       └─ 낙첨: payment-service(환불), notification-service
```

### 보상 트랜잭션 (SAGA Compensation)

| 실패 지점 | 보상 |
|---|---|
| DB 저장 실패 (구매 선점 후) | Redis 선점 보상 최대 3회 retry → 전부 실패 시 `drop.purchase.compensation.failed` CRITICAL 메트릭 |
| PG 결제 실패 | `payment.failed` → coupon RESERVED→AVAILABLE, order CANCELLED |
| 재고 차감 실패 | `stock.failed` → payment 자동 환불 → `refund.done` → coupon 복구 |
| 구매 선점 만료 (hold TTL) | `hold.expired` → coupon RESERVED→AVAILABLE (no-op if AVAILABLE) |
| 래플 낙첨 | `raffle.loser.notified` → payment 환불 |

---

## 로컬 개발 환경

### 사전 요구사항

- Docker Desktop
- Java 21
- Gradle

### 1. 인프라 실행 (Docker)

```bash
docker compose up -d
```

| 서비스 | 포트 |
|---|---|
| PostgreSQL | 5432 |
| Redis | 6379 |
| Kafka | 9092 |
| Kafka UI | 9000 |
| Keycloak | 8180 |
| Prometheus | 19090 |
| Grafana | 13000 |

### 2. 서비스 실행 (IntelliJ 또는 터미널)

인프라 기동 후 아래 순서대로 실행:

```
1. eureka-server  :8761
2. config-server  :8888
3. gateway        :8080
4. 나머지 서비스들 (순서 무관)
```

각 서비스 `application.yml`에 localhost 기본값이 설정되어 있어 별도 환경 변수 없이 실행 가능.

---

## 전체 Docker 실행 (통합 테스트)

```bash
# 1. JAR 빌드
./gradlew bootJar -x test

# 2. 전체 기동
docker compose -f docker-compose.yml -f docker-compose.services.yml up --build -d

# 3. 로그 확인
docker compose -f docker-compose.yml -f docker-compose.services.yml logs -f

# 4. 종료
docker compose -f docker-compose.yml -f docker-compose.services.yml down
```

---

## E2E 테스트 (Karate DSL)

서비스가 실행 중인 상태에서 실제 HTTP 요청으로 전체 API 흐름을 검증한다.

```bash
bash e2e/run.sh                                    # 전체 실행
bash e2e/run.sh user                               # user 서비스 전체
bash e2e/run.sh coupon                             # coupon 서비스 전체
bash e2e/run.sh drop                               # drop 서비스 전체
bash e2e/run.sh payment                            # payment 서비스 전체
bash e2e/run.sh scenario/01_drop_purchase/01_normal  # 드롭 구매 해피패스 시나리오
```

테스트 리포트:

```bash
open e2e/build/karate-reports/karate-summary.html
```

E2E 테스트 상세 안내 → [e2e/README.md](./e2e/README.md)

---

## 문서

| 문서 | 내용 |
|---|---|
| [docs/00.overview.md](./docs/00.overview.md) | 프로젝트 개요 |
| [docs/01.business-policy.md](./docs/01.business-policy.md) | 비즈니스 정책 |
| [docs/02.conventions.md](./docs/02.conventions.md) | 코드 컨벤션 |
| [docs/04.schema.md](./docs/04.schema.md) | DB 스키마 |
| [docs/05.api.md](./docs/05.api.md) | API 명세 |
| [docs/06.kafka-topics.md](./docs/06.kafka-topics.md) | Kafka 토픽 명세 |
| [docs/07.service-flow.md](./docs/07.service-flow.md) | 서비스 흐름 |
| [docs/08.sequence.md](./docs/08.sequence.md) | 시퀀스 다이어그램 |
| [docs/09.deployment.md](./docs/09.deployment.md) | 배포 가이드 |
