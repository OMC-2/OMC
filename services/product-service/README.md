# Product Service

## 개요
상품 정보 관리 및 재고 확정 차감을 담당하는 마이크로서비스입니다.

재고 선점(Redis)은 Drop Service가 직접 담당하며,
Product Service는 결제 완료(payment.completed) 이벤트 수신 후 DB 확정 차감만 처리합니다.

MVP 기간 동안 Inventory는 Product Service 내 패키지로 통합 관리합니다.
트러블슈팅 기간 중 서비스 분리 타당성을 재검토했으나, 현재 트래픽 패턴에서는 CPU/Kafka Lag 모두
여유가 있어 분리를 보류했습니다. 다만 재고 확정 차감 동시성 결함의 근본 원인(HikariCP 커넥션
미반납)은 미해결 상태라 다음 스프린트에서 재논의 예정입니다. (상세: SA 문서 16.3)

## 포트
- `8084`

## 주요 기능
- 상품 등록(`POST /api/v1/admin/products`) / 수정·삭제(`/api/v1/admin/products/{productId}`) / 목록·상세 조회(`/api/v1/products`)
- 상품 가격 조회 (Order Service용 Internal API, `GET /internal/v1/products/{productId}`)
- 재고 확정 차감 (payment.completed 이벤트 Consumer, concurrency=3)
  - `@Version` 낙관적 락 + Semaphore(3) 동시성 제한 + Kafka DLT로 3중 방어
  - Semaphore 미획득(2초 초과) 시 `DeductionBusyException` → Kafka 레벨 재시도/DLT (SAGA 보상과는 별개 경로)
- 재고 스냅샷 조회 (Drop Service용 Internal API)
- 재고 수동 수정 (관리자)
- Outbox 이벤트 관리자 수동 재처리 (`/api/v1/admin/outbox-events/{eventId}/retry`, `/retry-all`)
- Transactional Outbox 패턴 기반 이벤트 발행 보장

## 기술 스택
- Spring Boot 3.4.5
- Spring Data JPA
- Spring Kafka
- PostgreSQL 18
- Flyway
- Eureka Client

## DB 스키마
- `product_db` (단일 DB omc 내 스키마)

## DB 테이블
- `p_products`: 상품 정보
- `p_inventories`: 재고 정보 (available_quantity GENERATED, @Version 낙관적 락)
- `p_outbox_events`: Transactional Outbox
- `p_processed_events`: Kafka Consumer 멱등성 방어
- `p_failed_event_logs`: 이벤트 처리 실패 기록 (SAGA 케이스 B)

## SAGA 이벤트
| 구분 | 이벤트 | 방향 |
|------|--------|------|
| 소비 | `payment.completed` | Kafka Consumer |
| 발행 | `stock.deducted` | Outbox → Kafka |
| 발행 | `stock.failed` | Outbox → Kafka (케이스 B) |
