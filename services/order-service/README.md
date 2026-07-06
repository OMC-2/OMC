# Order Service

## 개요
OMC 플랫폼의 주문 라이프사이클 관리 및 SAGA 패턴의 핵심 상태 머신(State Machine) 역할을 담당하는 마이크로서비스입니다.

드롭(선착순) 및 래플(추첨) 도메인의 특성에 맞춰 외부 이벤트를 통해 주문(PENDING_PAYMENT)을 생성하며, 이후 결제 및 재고 도메인과의 비동기 이벤트 통신을 통해 주문 확정(CONFIRMED) 또는 보상 트랜잭션(CANCELLED)을 처리합니다.

이벤트 발행 유실을 막기 위한 **Transactional Outbox 패턴**과 수신 장애 복구를 위한 **RDBMS 기반 DLQ(Dead Letter Queue)** 시스템을 독자적으로 구축하여 운영합니다.

## 포트
- `8083`

## 주요 기능
- **주문 상태 머신 관리:** 생성(PENDING_PAYMENT) → 결제 완료(PAID) → 확정(CONFIRMED) / 배송 / 환불 및 취소(CANCELLED)
- **SAGA 보상 트랜잭션 처리:** 결제 실패, 재고 차감 실패, 드롭 홀드 만료 시 주문 상태를 롤백(CANCELLED)하고 알림 트리거 발행
- **내부 서비스 연동 (Internal API):** OpenFeign을 이용한 Product Service 가격 조회 (Gateway Secret 헤더 자동 주입으로 보안 우회)
- **RDBMS DLQ & 어드민 API:** 컨슈머 장애(Feign 5xx, 역직렬화 실패, DB 제약조건 등) 발생 시 실패 메시지를 DB에 격리(`p_order_dlq_messages`)하고, 어드민 API를 통해 원본 토픽으로 재발행(RepublishRaw)
- **이벤트 발행 원자성 보장 (Transactional Outbox Pattern):**
    - **`OutboxEventRecorder`:** 비즈니스 데이터(주문) 저장과 아웃박스(`p_order_outbox_events`) 저장을 단일 `@Transactional`로 묶어 원자성을 보장합니다.
    - **`OrderOutboxPoller` (발행기):** - **동작 방식:** 짧은 주기(`@Scheduled`)로 `INIT` 상태의 아웃박스를 오래된 순으로 조회하여 원본 토픽으로 Kafka에 발행합니다. 발행 성공(ACK) 시 `PUBLISHED`로 변경하고, 실패 시 `retryCount`를 증가시키며 최대치 초과 시 `FAILED` 상태로 격리합니다. (`KafkaTemplate.send`의 비동기 호출을 `.get()`으로 대기하여 성공/실패를 명확히 가릅니다.)
        - **멱등성 보장:** 페이로드 내부의 `eventId`(=아웃박스 PK)가 그대로 전송되므로, 폴러가 같은 레코드를 중복 발행하더라도 컨슈머 단에서 `eventId`를 통해 중복을 필터링합니다. *(At-least-once 발행 + 컨슈머 멱등성 = 사실상 정확히 한 번 처리)*
        - **확장성 고려:** 현재는 단일 인스턴스를 가정하며 멱등성으로 중복 발행을 흡수하고 있습니다. 향후 서버 다중 인스턴스로 확장 시, 중복 폴링을 막기 위해 비관적 락(`SELECT ... FOR UPDATE SKIP LOCKED`)을 적용할 수 있도록 설계되었습니다.
- **분산 환경 최적화 PK 생성(`UuidV7Generator`) 적용:**
    - `java.util.UUID` + `SecureRandom`을 조합하여 **시간순 정렬이 가능한 UUID v7**을 직접 생성합니다.
    - 생성된 `UUID v7`은 인덱스 단편화를 방지하고, 이벤트 발행 순서를 보장하는 식별자로 사용됩니다.

## 기술 스택
- Spring Boot 3.4.5
- Spring Data JPA
- Spring Kafka
- Spring Cloud OpenFeign
- PostgreSQL 18
- Flyway
- Eureka Client

## DB 스키마
- 단일 DB `omc` 내 스키마 (또는 `order_db`)

## DB 테이블
- `p_orders`: 주문 기본 정보 및 상태 (UUID v7 PK 적용, Optimistic Lock `@Version` 적용)
- `p_order_outbox_events`: Transactional Outbox (발행 대기 이벤트 저장, `status`와 `created_at` 복합 인덱스 적용)
- `p_order_processed_events`: Kafka Consumer 멱등성 방어 (event_id 기반 중복 수신 차단)
- `p_order_dlq_messages`: DLQ (처리 실패 메시지 보관 및 재처리 대기열)

## SAGA 이벤트

| 구분 | 이벤트 | 방향 | 설명 |
|:---|:---|:---|:---|
| 소비 | `purchase.confirmed` | Kafka Consumer | 드롭 선점 완료 수신 → Feign 상품 조회 → PENDING 주문 생성 |
| 소비 | `raffle.winner.selected` | Kafka Consumer | 래플 당첨 수신 → PENDING 주문 생성 |
| 소비 | `payment.completed` | Kafka Consumer | 결제 완료 수신 → PAID 상태 전이 |
| 소비 | `stock.deducted` | Kafka Consumer | DB 재고 확정 차감 완료 수신 → CONFIRMED 전이 (INSTANT 전용, 래플은 미발행) |
| 소비 | `payment.failed` | Kafka Consumer | (보상) 결제 실패 수신 → CANCELLED 전이 |
| 소비 | `stock.failed` | Kafka Consumer | (보상) 재고 차감 실패 수신 → CANCELLED 전이 |
| 소비 | `hold.expired` | Kafka Consumer | (보상) 드롭 홀드 만료 수신 → CANCELLED 전이 |
| 발행 | `order.created` | Outbox → Kafka | 결제 서비스로 결제 요청 (드롭/래플 공통) |
| 발행 | `order.cancelled` | Outbox → Kafka | 주문 취소 전파 (래플 재추첨 및 알림 트리거용) |
| 발행 | `order.confirmed` | Outbox → Kafka | 주문 최종 확정 알림 |
| 발행 | `order.shipped` | Outbox → Kafka | 배송 시작 알림 |
| 발행 | `refund.requested` | Outbox → Kafka | 사용자 직접 환불 요청 알림 |

---
*참고: 일반 도메인 이벤트의 카프카 직접 발행(`KafkaTemplate.send`)은 폐기되었으며, 모든 이벤트 발행은 `OutboxEventRecorder`를 거쳐 비동기로 처리됩니다. (`OrderEventProducer`는 DLQ 어드민 재발행 역할만 수행합니다.)*