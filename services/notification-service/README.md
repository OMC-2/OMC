# Notification Service

## 개요
Kafka Consumer로 다른 서비스의 이벤트를 수신하여 사용자에게 알림을 발송하는 마이크로서비스입니다.
이메일, 푸시 알림 등 다양한 채널을 통한 알림 발송을 담당합니다.

## 포트
- `8087`

## 주요 기능
- Kafka 이벤트 소비 및 알림 발송
- 알림 이력 저장
- 알림 채널: 이메일, 앱 푸시 (확장 가능)

## 기술 스택
- Spring Boot 3.2.x
- Spring Data JPA
- Spring Kafka (Consumer)
- MySQL
- Eureka Client

## 구독 Kafka Topics
| Topic | 설명 |
|-------|------|
| `raffle.winner` | 드롭 당첨 알림 발송 |
| `payment.completed` | 결제 완료 알림 발송 |
| `payment.failed` | 결제 실패 알림 발송 |
| `order.created` | 주문 접수 알림 발송 |
