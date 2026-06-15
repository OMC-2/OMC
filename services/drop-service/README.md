# Drop Service

## 개요
한정판 드롭(Drop) 이벤트의 생성, 스케줄링, 상태 관리를 담당하는 마이크로서비스입니다.
Spring Scheduler로 드롭 오픈/마감을 자동 처리하고 Kafka로 이벤트를 발행합니다.

## 포트
- `8083`

## 주요 기능
- 드롭 생성/수정/조회 (`/api/drops`)
- 드롭 상태 관리 (`SCHEDULED` → `OPEN` → `CLOSED`)
- 스케줄링 기반 자동 상태 전환 (`@Scheduled`)
- Kafka 이벤트 발행 (`drop.opened`, `drop.closed`)

## 기술 스택
- Spring Boot 3.2.x
- Spring Data JPA
- Spring Kafka (Producer)
- Spring Scheduler
- MySQL
- Eureka Client

## Kafka Topics
| Topic | 설명 |
|-------|------|
| `drop.opened` | 드롭 오픈 이벤트 |
| `drop.closed` | 드롭 마감 이벤트 |
