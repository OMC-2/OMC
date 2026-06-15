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

| 경로 | 대상 서비스 | 포트 |
|------|------------|------|
| `/api/users/**` | user-service | 8081 |
| `/api/products/**` | product-service | 8082 |
| `/api/drops/**` | drop-service | 8083 |
| `/api/raffles/**` | raffle-service | 8084 |
| `/api/orders/**` | order-service | 8085 |
| `/api/payments/**` | payment-service | 8086 |
| `/api/notifications/**` | notification-service | 8087 |

## 실행 방법
```bash
./gradlew :gateway:bootRun
```
