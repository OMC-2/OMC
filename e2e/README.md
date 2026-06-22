# E2E 테스트 (Karate DSL)

로컬에서 전체 인프라를 띄운 상태에서 실제 HTTP 요청으로 API 흐름을 검증한다.
JUnit 5 기반으로 `./gradlew :e2e:test`로 실행하며, npm 설치가 필요 없다.

---

## 실행 순서

### 1단계: 서버 기동 (코드가 바뀐 경우 반드시 먼저 실행)

```bash
bash docker-up.sh
```

이 스크립트가 하는 일:
1. 기존 컨테이너 전체 종료 (`down`)
2. Gradle bootJar 빌드 (변경된 코드 반영)
3. Docker 이미지 빌드
4. 인프라 기동 (PostgreSQL, Redis, Kafka, Keycloak 등)
5. 서비스 기동 (Eureka, Config, Gateway, user-service, coupon-service 등)
6. 전체 헬스체크 통과까지 대기

> 코드 변경 없이 서버가 이미 떠 있으면 이 단계를 건너뛰어도 된다.

### 2단계: E2E 테스트 실행

```bash
bash e2e/run.sh               # 전체 실행
bash e2e/run.sh user          # user 서비스만
bash e2e/run.sh coupon        # coupon 서비스만
bash e2e/run.sh user/signup   # 특정 시나리오만
```

---

## 실행 가능한 대상 목록

```bash
# 서비스 그룹
bash e2e/run.sh user              # user/ 폴더 전체
bash e2e/run.sh coupon            # coupon/ 폴더 전체
bash e2e/run.sh saga              # saga/ 폴더 전체 (서비스 연계 시나리오)

# user 개별 시나리오
bash e2e/run.sh user/signup              # 회원가입
bash e2e/run.sh user/login               # 로그인
bash e2e/run.sh user/profile             # 프로필 조회
bash e2e/run.sh user/token_refresh       # 토큰 갱신
bash e2e/run.sh user/profile_update      # 프로필 수정 및 탈퇴
bash e2e/run.sh user/address             # 주소 CRUD + 기본 주소 설정
bash e2e/run.sh user/security            # 인증·인가·경로 보안 검증

# coupon 개별 시나리오
bash e2e/run.sh coupon/coupon_create     # 쿠폰 생성
bash e2e/run.sh coupon/coupon_issue      # 쿠폰 발급
bash e2e/run.sh coupon/coupon_my         # 내 쿠폰 조회
bash e2e/run.sh coupon/coupon_security   # 쿠폰 인증·인가 보안 검증
```

---

## 환경변수

`run.sh`에 기본값이 하드코딩되어 있으므로 별도 설정 불필요.
외부에서 오버라이드할 경우 아래 변수를 사용한다.

| 변수 | 기본값 | 설명 |
|---|---|---|
| `GATEWAY_SECRET` | `local-secret` | X-Gateway-Secret 헤더 값 |
| `ADMIN_SECRET` | `local-admin-secret` | X-Admin-Secret 헤더 값 |
| `BASE_URL` | `http://localhost:8080` | Gateway 주소 |

---

## 폴더 구조

```
e2e/
├── build.gradle
├── README.md
└── src/test/
    ├── java/e2e/
    │   └── E2ERunner.java              ← JUnit 5 실행 진입점
    └── resources/
        ├── karate-config.js            ← 환경변수 설정
        ├── user/                       ← user-service 시나리오
        │   ├── signup.feature
        │   ├── login.feature
        │   ├── profile.feature
        │   ├── profile_update.feature
        │   ├── token_refresh.feature
        │   ├── address.feature
        │   └── security.feature
        ├── coupon/                     ← coupon-service 시나리오
        │   ├── coupon_create.feature
        │   ├── coupon_issue.feature
        │   ├── coupon_my.feature
        │   └── coupon_security.feature
        └── saga/                       ← 서비스 연계 시나리오 (추후 추가)
```

---

## 테스트 리포트 확인

실행 후 `e2e/build/karate-reports/`에 HTML 리포트가 생성된다.

```bash
open e2e/build/karate-reports/karate-summary.html
```

---

## 새 시나리오 추가하는 방법

1. 해당 서비스 폴더에 `.feature` 파일 생성
   - 단일 서비스: `src/test/resources/{서비스명}/`
   - 연계 시나리오: `src/test/resources/saga/`
2. 기존 `.feature` 파일을 참고해 시나리오 작성
3. `E2ERunner.java`에 `classpath:{서비스명}` 추가
