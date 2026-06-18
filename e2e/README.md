# E2E 테스트 (Karate DSL)

로컬에서 전체 인프라를 띄운 상태에서 실제 HTTP 요청으로 API 흐름을 검증한다.
JUnit 5 기반으로 `./gradlew :e2e:test`로 실행하며, npm 설치가 필요 없다.

---

## 사전 준비

### 1. 인프라 + 서비스 기동

```bash
# 인프라 (PostgreSQL, Keycloak, Kafka 등)
docker compose up -d

# 서비스 (Eureka, Config, Gateway, user-service 등)
docker compose -f docker-compose.services.yml up -d
```

서비스가 모두 뜨는 데 시간이 걸리므로 Gateway 헬스체크 후 실행한다.

```bash
curl http://localhost:8080/actuator/health
```

### 2. 환경변수 설정

시크릿 값은 환경변수로 주입한다. 파일을 별도로 만들 필요 없다.

| 변수 | 설명 |
|---|---|
| `GATEWAY_SECRET` | X-Gateway-Secret 헤더 값 |
| `ADMIN_SECRET` | X-Admin-Secret 헤더 값 |
| `BASE_URL` | Gateway 주소 (기본값: `http://localhost:8080`) |

---

## 실행 방법

### 전체 실행

```bash
GATEWAY_SECRET=xxx ADMIN_SECRET=yyy ./gradlew :e2e:test -PrunE2E
```

### 특정 feature만 실행

```bash
GATEWAY_SECRET=xxx ./gradlew :e2e:test -PrunE2E -Dkarate.options="classpath:user/signup.feature"
```

---

## 폴더 구조

```
e2e/
├── build.gradle
├── README.md
└── src/test/
    ├── java/e2e/
    │   └── E2ERunner.java          ← JUnit 5 실행 진입점
    └── resources/
        ├── karate-config.js        ← 환경변수 설정
        ├── user/                   ← user-service 단독 시나리오
        │   ├── signup.feature
        │   ├── login.feature
        │   └── profile.feature
        └── saga/                   ← 서비스 연계 시나리오 (추후 추가)
```

- **user/, product/ 등**: 단일 서비스 기능 검증
- **saga/**: 여러 서비스가 연계되는 흐름 검증 (회원가입→주문→결제 등)

---

## 테스트 리포트 확인

실행 후 `e2e/build/karate-reports/`에 HTML 리포트가 생성된다.
각 시나리오별 실제 요청/응답 내용을 상세히 확인할 수 있다.

```bash
# 전체 요약
open e2e/build/karate-reports/karate-summary.html

# 시나리오별 상세
open e2e/build/karate-reports/user.signup.html
open e2e/build/karate-reports/user.login.html
open e2e/build/karate-reports/user.profile.html
```

---

## 새 시나리오 추가하는 방법

1. 해당 서비스 폴더에 `.feature` 파일 생성
   - 단일 서비스: `src/test/resources/{서비스명}/`
   - 연계 시나리오: `src/test/resources/saga/`
2. 기존 `.feature` 파일을 참고해 시나리오 작성
3. `E2ERunner.java`는 수정 불필요 — `relativeTo(getClass())`가 자동으로 모든 `.feature`를 탐색
