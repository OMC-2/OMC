# Raffle Service

## 개요
드롭 응모, 추첨, 당첨자 선정을 담당하는 마이크로서비스입니다.
Redis를 활용한 중복 응모 방지 및 Kafka 이벤트 기반 추첨 처리를 수행합니다.

## 포트
- `8084`

## 주요 기능
- 드롭 응모 (`POST /api/raffles/{dropId}/apply`)
- 중복 응모 방지 (Redis Set 활용)
- 추첨 및 당첨자 선정
- 당첨/낙첨 결과 Kafka 이벤트 발행

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
| `drop.closed` | Consumer | 드롭 마감 시 추첨 트리거 |
| `raffle.winner` | Producer | 당첨자 선정 결과 |
