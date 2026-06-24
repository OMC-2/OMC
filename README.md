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
  ├── [drop-service     :8082]  드롭 생성, 스케줄링
  ├── [order-service    :8083]  주문 생성, 상태 관리
  ├── [product-service  :8084]  상품 정보, 재고 관리
  ├── [payment-service  :8085]  결제, 환불, SAGA
  ├── [raffle-service   :8086]  응모, 추첨, 당첨자 선정
  ├── [coupon-service   :8087]  쿠폰 발급 및 관리
  └── [notification-service :8088]  알림 발송

[eureka-server :8761]  ← 서비스 디스커버리
[config-server :8888]  ← 중앙 설정 관리
[Kafka :9092]          ← 비동기 이벤트
[Redis :6379]          ← 캐시, 재고
[PostgreSQL :5432]     ← 영속성
```

## 모듈 구성

| 모듈 | 포트 | 설명 |
|------|------|------|
| `common` | - | 공통 DTO, 이벤트, 예외 |
| `eureka-server` | 8761 | 서비스 레지스트리 |
| `config-server` | 8888 | 중앙 설정 관리 |
| `gateway` | 8080 | API 게이트웨이, JWT 필터 |
| `user-service` | 8081 | 회원 관리, 인증 |
| `drop-service` | 8082 | 드롭 이벤트 |
| `order-service` | 8083 | 주문 |
| `product-service` | 8084 | 상품, 재고 |
| `payment-service` | 8085 | 결제 (SAGA) |
| `raffle-service` | 8086 | 응모, 추첨 |
| `coupon-service` | 8087 | 쿠폰 발급 |
| `notification-service` | 8088 | 알림 |

## 시작하기

### 사전 요구사항

- Docker Desktop
- Java 21
- Gradle

### 인프라만 띄우기 (평소 로컬 개발 시)

```bash
docker compose up -d
```

| 서비스 | 포트 |
|--------|------|
| PostgreSQL | 5432 |
| Redis | 6379 |
| Kafka | 9092 |
| Kafka UI | 9000 |

### Spring Boot 서비스 로컬 실행

인프라 기동 후 아래 순서대로 실행:

```bash
./gradlew :services:eureka-server:bootRun
./gradlew :services:config-server:bootRun
./gradlew :services:gateway:bootRun
./gradlew :services:user-service:bootRun
./gradlew :services:drop-service:bootRun
```

### 전체 Docker로 한 번에 띄우기 (통합 테스트 / EC2 배포)

```bash
# 1. jar 빌드
./gradlew bootJar -x test

# 2. 전체 기동 (depends_on으로 순서 자동 제어)
docker compose -f docker-compose.yml -f docker-compose.services.yml up --build -d

# 3. 로그 확인
docker compose -f docker-compose.yml -f docker-compose.services.yml logs -f
```

### 종료

```bash
# 인프라만 내리기
docker compose down

# 전체 내리기
docker compose -f docker-compose.yml -f docker-compose.services.yml down
```

## 배포

EC2 배포 전체 절차는 [docs/09.deployment.md](./docs/09.deployment.md) 참조.

```
Phase 1. Terraform으로 EC2 띄우기
Phase 2. docker-compose.prod.yml 작성
Phase 3. GitHub Actions 워크플로 작성 + Secrets 등록
Phase 4. scripts/init-ec2.sh 최초 1회 실행
Phase 5. feature → main 머지 → CD 파이프라인 첫 배포
```

## 기술 스택

- **Java 21** (Amazon Corretto) - 가상 스레드(Virtual Threads) 활용
- **Spring Boot 3.4.5**
- **Spring Cloud 2024.0.1** (Eureka, Gateway, Config Server)
- **Spring Security 6** + **jjwt 0.12.3**
- **Spring Kafka**
- **Spring Data JPA** + **PostgreSQL 18** + **Flyway**
- **Spring Data Redis**
- **Gradle 8** (Groovy DSL)
- **Docker Compose**
