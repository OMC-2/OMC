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
bash e2e/run.sh payment       # payment 서비스만
bash e2e/run.sh user/signup   # 특정 시나리오만
```

---

## 실행 가능한 대상 목록

```bash
# 서비스 그룹
bash e2e/run.sh user              # user/ 폴더 전체
bash e2e/run.sh coupon            # coupon/ 폴더 전체
bash e2e/run.sh saga              # saga/ 폴더 전체 (서비스 연계 시나리오)
bash e2e/run.sh payment           # payment/ 폴더 전체
bash e2e/run.sh scenario          # scenario/ 폴더 전체 (핵심 시연 시나리오)

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

# payment 개별 시나리오
bash e2e/run.sh payment/payment_flow     # 결제 승인·조회·취소
bash e2e/run.sh payment/payment_security # 결제 인증·인가 보안 검증

# 핵심 결제 SAGA 시나리오
bash e2e/run.sh scenario/04_payment_saga/01_payment_failure # 결제 수단 오류 보상
bash e2e/run.sh scenario/04_payment_saga/02_stock_failure   # 재고 차감 실패 결제 취소
bash e2e/run.sh scenario/04_payment_saga/03_hold_expire     # 구매 hold 만료
bash e2e/run.sh scenario/04_payment_saga/04_idempotency     # PG 오류 결제 멱등성

# 시연 시나리오 그룹
bash e2e/run.sh scenario/03_coupon_concurrency  # 쿠폰 동시 발급 시나리오 전체
bash e2e/run.sh scenario/06_auth_errors         # 권한 오류 시나리오 전체

# 시연 시나리오 개별
bash e2e/run.sh scenario/01_drop_purchase/01_normal                       # 드롭 정상 구매 해피패쓰
bash e2e/run.sh scenario/01_drop_purchase/02_refund.feature               # 환불 요청 → 결제 취소 → 환불 알림
bash e2e/run.sh scenario/02_raffle_purchase/01_entry_with_coupon.feature  # 응모 (쿠폰 적용 + 선결제)
bash e2e/run.sh scenario/02_raffle_purchase/02_draw_and_notify.feature    # 추첨 → 당첨 알림 / 낙첨 자동 환불 + 알림
bash e2e/run.sh scenario/03_coupon_concurrency/01_concurrent_issue        # 동시 발급 → 수량만 성공
bash e2e/run.sh scenario/03_coupon_concurrency/02_duplicate_issue         # 중복 발급 차단
bash e2e/run.sh scenario/04_payment_saga/01_payment_failure.feature       # 결제 수단 오류 → 재고/쿠폰 롤백 + 실패 알림
bash e2e/run.sh scenario/04_payment_saga/02_stock_failure.feature         # 재고 차감 실패 → 결제 승인 취소 보상
bash e2e/run.sh scenario/04_payment_saga/03_hold_expire.feature           # 구매 hold 만료 → 선점 자동 해제
bash e2e/run.sh scenario/04_payment_saga/04_idempotency.feature           # 외부 결제 오류 → 멱등성 키로 중복 결제 방지
bash e2e/run.sh scenario/05_admin/01_drop_modify.feature                  # 오픈 전 드롭 수정/삭제
bash e2e/run.sh scenario/05_admin/02_raffle_modify.feature                # 오픈 전 래플 수정/삭제
bash e2e/run.sh scenario/05_admin/03_raffle_status.feature                # 래플 상태 강제 변경 (SCHEDULED→OPEN→CLOSED)
bash e2e/run.sh scenario/05_admin/04_raffle_draw.feature                  # 응모자 목록 조회 + 수동 추첨
bash e2e/run.sh scenario/06_auth_errors/00_signup_happy                   # 회원가입 해피패쓰
bash e2e/run.sh scenario/06_auth_errors/01_unauthenticated                # 비로그인 접근 차단
bash e2e/run.sh scenario/06_auth_errors/02_unauthorized                   # 권한 오류
bash e2e/run.sh scenario/06_auth_errors/03_token_refresh                  # 토큰 갱신
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
| `PAYMENT_SERVICE_URL` | `http://localhost:8085` | 결제 내부 API 테스트 준비 주소 |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | E2E Java 헬퍼의 Kafka 접속 주소 |

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
        ├── payment/                    ← payment-service 시나리오
        │   ├── payment_flow.feature
        │   └── payment_security.feature
        ├── saga/                       ← 분산 트랜잭션 검증 시나리오
        └── scenario/                   ← 핵심 시연 시나리오
            ├── 01_drop_purchase/
            │   └── 01_normal.feature
            ├── 02_raffle_purchase/
            │   ├── 01_entry_with_coupon.feature
            │   └── 02_draw_and_notify.feature
            ├── 03_coupon_concurrency/
            │   ├── _create_user.feature        헬퍼: 유저 생성 (karate.parallel 내부용)
            │   ├── _issue_one.feature          헬퍼: 쿠폰 발급 1건 (karate.parallel 내부용)
            │   ├── 01_concurrent_issue.feature 동시 발급 → 수량만 성공 + 알림 검증
            │   └── 02_duplicate_issue.feature  중복 발급 차단
            ├── 04_payment_saga/
            │   ├── 01_payment_failure.feature
            │   ├── 02_stock_failure.feature
            │   ├── 03_hold_expire.feature
            │   └── 04_idempotency.feature
            └── 05_admin/
            │   ├── 01_drop_modify.feature
            │   ├── 02_raffle_modify.feature
            │   ├── 03_raffle_status.feature
            │   └── 04_raffle_draw.feature
            └── 06_auth_errors/
                ├── 00_signup_happy.feature     회원가입 해피패쓰
                ├── 01_unauthenticated.feature  비로그인 접근 차단 → 401
                ├── 02_unauthorized.feature     권한 오류 → 403
                └── 03_token_refresh.feature    토큰 갱신 + 새 토큰 검증
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
   - 핵심 시연 시나리오: `src/test/resources/scenario/{시나리오명}/`
2. 기존 `.feature` 파일을 참고해 시나리오 작성
3. `E2ERunner.java`에 `classpath:{서비스명}` 추가
