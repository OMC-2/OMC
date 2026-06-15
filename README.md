# OMC (ONE MORE CHANCE)

한정판 드롭 커머스 플랫폼 - Spring Boot MSA 프로젝트

## 아키텍처

```
Client
  │
  ▼
[Gateway :8080]  ←── JWT 필터, 라우팅
  │
  ├── [user-service     :8081]  회원가입, 로그인, JWT
  ├── [product-service  :8082]  상품 정보, 재고 관리
  ├── [drop-service     :8083]  드롭 생성, 스케줄링
  ├── [raffle-service   :8084]  응모, 추첨, 당첨자 선정
  ├── [order-service    :8085]  주문 생성, 상태 관리
  ├── [payment-service  :8086]  결제, 환불, SAGA
  └── [notification-service :8087]  알림 발송

[eureka-server :8761]  ← 서비스 디스커버리
[Kafka :9092]          ← 비동기 이벤트
[Redis :6379]          ← 캐시, 재고
[MySQL :3306]          ← 영속성
```

## 모듈 구성

| 모듈 | 포트 | 설명 |
|------|------|------|
| `common` | - | 공통 DTO, 이벤트, 예외 |
| `eureka-server` | 8761 | 서비스 레지스트리 |
| `gateway` | 8080 | API 게이트웨이, JWT 필터 |
| `user-service` | 8081 | 회원 관리, 인증 |
| `product-service` | 8082 | 상품, 재고 |
| `drop-service` | 8083 | 드롭 이벤트 |
| `raffle-service` | 8084 | 응모, 추첨 |
| `order-service` | 8085 | 주문 |
| `payment-service` | 8086 | 결제 (SAGA) |
| `notification-service` | 8087 | 알림 |

## 시작하기

### 인프라 실행 (Docker)
```bash
docker-compose up -d
```

### 서비스 실행 순서
1. `eureka-server` 먼저 실행
2. 나머지 서비스 순서 무관

```bash
./gradlew :eureka-server:bootRun
./gradlew :gateway:bootRun
./gradlew :user-service:bootRun
# ...
```

### 전체 빌드
```bash
./gradlew build
```

## 기술 스택

- **Java 17** (Amazon Corretto)
- **Spring Boot 3.2.5**
- **Spring Cloud 2023.0.1** (Eureka, Gateway)
- **Spring Security 6** + **jjwt 0.12.3**
- **Spring Kafka**
- **Spring Data JPA** + **MySQL 8**
- **Spring Data Redis**
- **Gradle 8** (Groovy DSL)
- **Docker Compose**
