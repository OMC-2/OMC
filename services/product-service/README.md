# Product Service

## 개요
상품 정보 관리 및 재고 확정 차감을 담당하는 마이크로서비스입니다.

재고 선점(Redis)은 Drop Service가 직접 담당하며,
Product Service는 결제 완료(payment.completed) 이벤트 수신 후 DB 확정 차감만 처리합니다.

MVP 기간 동안 Inventory는 Product Service 내 패키지로 통합 관리하며,
트러블슈팅 기간에 Inventory Service 분리 예정입니다.

## 포트
- `8084`

## 주요 기능
- 상품 등록/수정/삭제/조회 (`/api/v1/products`)
- 재고 확정 차감 (payment.completed 이벤트 Consumer)
- 재고 스냅샷 조회 (Drop Service용 Internal API)
- 재고 수동 수정 (관리자)
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