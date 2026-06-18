# Order Service

## 개요
주문 생성 및 주문 상태 관리를 담당하는 마이크로서비스입니다.
Kafka 이벤트로 결제 서비스와 연동하고, SAGA 패턴으로 분산 트랜잭션을 처리합니다.

## 포트
- `8085`

## 주요 기능
- 주문 생성 (`POST /api/orders`)
- 주문 상태 조회/변경
- 주문 상태: `PENDING` → `PAID` → `SHIPPING` → `COMPLETED` / `CANCELLED`
- Kafka 이벤트 기반 결제 요청/취소 연동

## 기술 스택
- Spring Boot 3.2.x
- Spring Data JPA
- Spring Data Redis
- Spring Kafka (Consumer/Producer)
- PostgreSQL 18
- Eureka Client

## Kafka Topics
| Topic | 역할 | 설명 |
|-------|------|------|
| `raffle.winner` | Consumer | 당첨자 주문 생성 트리거 |
| `order.created` | Producer | 주문 생성 이벤트 |
| `payment.completed` | Consumer | 결제 완료 처리 |
| `payment.failed` | Consumer | 결제 실패 → 주문 취소 |
