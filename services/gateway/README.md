# Gateway Service

## 개요
모든 클라이언트 요청의 단일 진입점(API Gateway)입니다.
Spring Cloud Gateway(WebFlux 기반)를 사용하며, JWT 인증 필터를 통해 인증/인가를 처리합니다.

## 포트
- `8080`

## 주요 기능
- 요청 라우팅 (각 마이크로서비스로 프록시)
- JWT 인증 필터 (`JwtAuthenticationFilter`)
- Eureka 기반 로드밸런싱 (`lb://service-name`)
- CORS 설정

## 라우팅 테이블

Eureka 로드밸런싱(`lb://service-name`)으로 라우팅되므로 포트는 게이트웨이 설정에 나타나지 않지만, 참고용으로 각 서비스의 실제 포트를 함께 표기합니다.

| 경로 | 대상 서비스 | 포트 |
|------|------------|------|
| `/api/v1/users/**` | user-service | 8081 |
| `/api/v1/products/**` | product-service | 8084 |
| `/api/v1/admin/products/**` | product-service | 8084 |
| `/api/v1/drops/*/purchase` (POST, 전용 rate limiter) | drop-service | 8082 |
| `/api/v1/drops/**` | drop-service | 8082 |
| `/api/v1/admin/drops/**` | drop-service | 8082 |
| `/api/v1/orders/**` | order-service | 8083 |
| `/api/v1/payments/**` | payment-service | 8085 |
| `/api/v1/raffles/**` | raffle-service | 8086 |
| `/api/v1/admin/raffles/**` | raffle-service | 8086 |
| `/api/v1/coupons/**` | coupon-service | 8087 |
| `/api/v1/notifications/**` | notification-service | 8088 |

## 실행 방법
```bash
./gradlew :gateway:bootRun
```
