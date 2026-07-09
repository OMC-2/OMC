# OMC (One More Chance) 시연 시나리오

> **시연 영상 촬영용** — 사용자 흐름 + 관리자 흐름 + API 연동 검증 결과

---

## 1. 서비스 아키텍처 개요

| 서비스 | 포트 | Swagger |
|---|---|---|
| API Gateway | 8080 | — |
| Eureka | 8761 | — |
| user-service | 8081 | `http://localhost:8081/swagger-ui.html` |
| product-service | 8082 | `http://localhost:8082/swagger-ui.html` |
| drop-service | 8083 | `http://localhost:8083/swagger-ui.html` |
| order-service | 8084 | `http://localhost:8084/swagger-ui.html` |
| payment-service | 8085 | `http://localhost:8085/swagger-ui.html` |
| coupon-service | 8086 | `http://localhost:8086/swagger-ui.html` |
| notification-service | 8087 | `http://localhost:8087/swagger-ui.html` |
| raffle-service | 8088 | `http://localhost:8088/swagger-ui.html` |
| Frontend | 5173 | `http://localhost:5173` |

---

## 2. 게이트웨이 라우트 매핑 (15개)

| Gateway Route ID | Path Pattern | 서비스 |
|---|---|---|
| user-service | `/api/v1/users/**` | user-service |
| product-service-admin | `/api/v1/admin/products/**` | product-service |
| product-service | `/api/v1/products/**` | product-service |
| drop-service-admin | `/api/v1/admin/drops/**` | drop-service |
| drop-purchase | `/api/v1/drops/*/purchase` | drop-service |
| drop-service | `/api/v1/drops/**` | drop-service |
| raffle-service | `/api/v1/raffles/**` | raffle-service |
| raffle-service-admin | `/api/v1/admin/raffles/**` | raffle-service |
| payment-service-admin | `/api/v1/admin/payments/**` | payment-service |
| order-service-admin | `/api/v1/admin/dlq/**` | order-service |
| product-service-outbox | `/api/v1/admin/outbox-events/**` | product-service |
| order-service | `/api/v1/orders/**` | order-service |
| payment-service | `/api/v1/payments/**` | payment-service |
| notification-service | `/api/v1/notifications/**` | notification-service |
| coupon-service | `/api/v1/coupons/**` | coupon-service |

---

## 3. 프론트엔드 ↔ 백엔드 API 연동 검증표

### 3.1 인증 (user-service)

| 기능 | Frontend 호출 | Backend 엔드포인트 | 상태 |
|---|---|---|---|
| 회원가입 | `POST /api/v1/users/signup` | `UserController.signup()` | ✅ |
| 로그인 | `POST /api/v1/users/login` | `UserController.login()` | ✅ |
| 토큰 갱신 | `POST /api/v1/users/token/refresh` | `UserController.refresh()` | ✅ |
| 내 정보 조회 | `GET /api/v1/users/me` | `UserController.getProfile()` | ✅ |
| 프로필 수정 | `PATCH /api/v1/users/me` | `UserController.updateProfile()` | ✅ |
| 회원 탈퇴 | `DELETE /api/v1/users/me` | `UserController.deleteAccount()` | ✅ |
| 관리자 회원가입 | `POST /api/v1/users/admin/signup` | `UserController.adminSignup()` | ✅ |

### 3.2 배송지 (user-service)

| 기능 | Frontend 호출 | Backend 엔드포인트 | 상태 |
|---|---|---|---|
| 배송지 목록 | `GET /api/v1/users/addresses` | `AddressController.getAll()` | ✅ |
| 배송지 등록 | `POST /api/v1/users/addresses` | `AddressController.create()` | ✅ |
| 배송지 수정 | `PATCH /api/v1/users/addresses/{id}` | `AddressController.update()` | ✅ |
| 배송지 삭제 | `DELETE /api/v1/users/addresses/{id}` | `AddressController.delete()` | ✅ |
| 기본 배송지 설정 | `PATCH /api/v1/users/addresses/{id}/default` | `AddressController.setDefault()` | ✅ |

### 3.3 상품 (product-service)

| 기능 | Frontend 호출 | Backend 엔드포인트 | 상태 |
|---|---|---|---|
| 상품 목록 | `GET /api/v1/products` | `ProductController.getAll()` | ✅ |
| 상품 상세 | `GET /api/v1/products/{id}` | `ProductController.getById()` | ✅ |
| 상품 등록 (관리자) | `POST /api/v1/admin/products` | `AdminProductController.create()` | ✅ |
| 상품 수정 (관리자) | `PUT /api/v1/admin/products/{id}` | `AdminProductController.update()` | ✅ |
| 재고 수정 (관리자) | `PATCH /api/v1/admin/products/{id}/inventories` | `AdminProductController.updateInventory()` | ✅ |
| Outbox 재시도 | `POST /api/v1/admin/outbox-events/{id}/retry` | `OutboxEventAdminController.retry()` | ✅ |
| Outbox 전체 재시도 | `POST /api/v1/admin/outbox-events/retry-all` | `OutboxEventAdminController.retryAll()` | ✅ |

### 3.4 드롭 (drop-service)

| 기능 | Frontend 호출 | Backend 엔드포인트 | 상태 |
|---|---|---|---|
| 드롭 목록 | `GET /api/v1/drops` | `DropController.getAll()` | ✅ |
| 드롭 상세 | `GET /api/v1/drops/{id}` | `DropController.getById()` | ✅ |
| 드롭 구매 | `POST /api/v1/drops/{id}/purchase` | `DropController.purchase()` | ✅ |
| 드롭 생성 (관리자) | `POST /api/v1/admin/drops` | `AdminDropController.create()` | ✅ |
| 드롭 수정 (관리자) | `PUT /api/v1/admin/drops/{id}` | `AdminDropController.update()` | ✅ |
| 드롭 종료 (관리자) | `POST /api/v1/admin/drops/{id}/close` | `AdminDropController.close()` | ✅ |

### 3.5 래플 (raffle-service)

| 기능 | Frontend 호출 | Backend 엔드포인트 | 상태 |
|---|---|---|---|
| 래플 목록 | `GET /api/v1/raffles` | `RaffleController.getAll()` | ✅ |
| 래플 상세 | `GET /api/v1/raffles/{id}` | `RaffleController.getById()` | ✅ |
| 래플 응모 | `POST /api/v1/raffles/{id}/entries` | `RaffleController.enter()` | ✅ |
| 내 응모 목록 | `GET /api/v1/raffles/entries/me` | `RaffleController.getMyEntries()` | ✅ |
| 내 결과 조회 | `GET /api/v1/raffles/{id}/winners/me` | `RaffleController.getMyResult()` | ✅ |
| 공개 당첨자 목록 | `GET /api/v1/raffles/{id}/winners` | `RaffleController.getPublicWinners()` | ✅ |
| 참가자 수 | `GET /api/v1/raffles/{id}/participants-count` | `RaffleController.getParticipantsCount()` | ✅ |
| 래플 생성 (관리자) | `POST /api/v1/admin/raffles` | `AdminRaffleController.createRaffle()` | ✅ |
| 래플 수정 (관리자) | `PUT /api/v1/admin/raffles/{id}` | `AdminRaffleController.updateRaffle()` | ✅ |
| 래플 삭제 (관리자) | `DELETE /api/v1/admin/raffles/{id}` | `AdminRaffleController.deleteRaffle()` | ✅ |
| 상태 변경 (관리자) | `POST /api/v1/admin/raffles/{id}/status` | `AdminRaffleController.updateRaffleStatus()` | ✅ |
| 추첨 실행 (관리자) | `POST /api/v1/admin/raffles/{id}/draw` | `AdminRaffleController.drawRaffle()` | ✅ |
| 응모자 목록 (관리자) | `GET /api/v1/admin/raffles/{id}/entries` | `AdminRaffleController.getRaffleEntries()` | ✅ |
| 패널티 부여 (관리자) | `POST /api/v1/admin/raffles/{id}/entries/{userId}/penalty` | `AdminRaffleController.penalizeUser()` | ✅ |

### 3.6 주문/결제 (order-service, payment-service)

| 기능 | Frontend 호출 | Backend 엔드포인트 | 상태 |
|---|---|---|---|
| 주문 상세 | `GET /api/v1/orders/{id}` | `OrderController.getOrder()` | ✅ |
| 환불 요청 | `POST /api/v1/orders/{id}/refund` | `OrderRefundController.requestRefund()` | ✅ |
| 내 결제 내역 | `GET /api/v1/payments/me` | `PaymentController.getMyPayments()` | ✅ |
| 결제 취소 | `POST /api/v1/payments/{id}/cancel` | `PaymentController.cancelPayment()` | ✅ |
| 전체 결제 (관리자) | `GET /api/v1/admin/payments` | `PaymentController.getAllPayments()` | ✅ |
| DLQ 목록 (관리자) | `GET /api/v1/admin/dlq` | `OrderDlqAdminController.getMessages()` | ✅ |
| DLQ 재발행 (관리자) | `POST /api/v1/admin/dlq/{id}/republish` | `OrderDlqAdminController.republish()` | ✅ |

### 3.7 쿠폰 (coupon-service)

| 기능 | Frontend 호출 | Backend 엔드포인트 | 상태 |
|---|---|---|---|
| 쿠폰 목록 (관리자) | `GET /api/v1/coupons` | `CouponController.getAll()` | ✅ |
| 내 쿠폰 | `GET /api/v1/coupons/me` | `CouponController.getMyCoupons()` | ✅ |
| 쿠폰 발급 | `POST /api/v1/coupons/{id}` | `CouponController.issue()` | ✅ |
| 쿠폰 생성 (관리자) | `POST /api/v1/coupons` | `CouponController.create()` | ✅ |

### 3.8 알림 (notification-service)

| 기능 | Frontend 호출 | Backend 엔드포인트 | 상태 |
|---|---|---|---|
| 알림 목록 | `GET /api/v1/notifications` | `NotificationController.getAll()` | ✅ |
| 알림 읽음 처리 | `PATCH /api/v1/notifications/{id}/read` | `NotificationController.markRead()` | ✅ |

---

## 4. 시연 시나리오

### 시나리오 A — 사용자 플로우 (드롭 구매)

#### A-1. 회원가입 & 로그인
1. `http://localhost:5173` 접속 → **SIGNUP** 클릭
2. 이메일 / 비밀번호 / 닉네임 입력 후 회원가입
3. 자동으로 로그인 처리

**검증 API:**
- `POST /api/v1/users/signup` → 201 Created
- `POST /api/v1/users/login` → 200 OK, `accessToken` 반환

---

#### A-2. 마이페이지 — 결제 수단 등록
1. 우상단 MY 클릭 → 마이페이지 이동
2. **결제 수단** 섹션 → `+ 등록` 클릭
3. 테스트 카드번호 `4242 4242 4242 4242` / 유효기간 `12/26` / CVC `123` 입력
4. 등록 완료 → 빌링키 ID 자동 생성 (localStorage 저장)

> ※ 실제 PG 연동 없이 localStorage mock으로 빌링키 ID 시뮬레이션

---

#### A-3. 드롭 상세 & 구매
1. 상단 nav → **DROPS** 클릭
2. `ON DROP` 배지가 붙은 드롭 선택
3. 실시간 잔여 수량 확인 (5초 자동 갱신)
4. **구매하기** 버튼 클릭 → 배송지 선택 → 결제 완료

**검증 API:**
- `GET /api/v1/drops` → drops 목록
- `GET /api/v1/drops/{id}` → 잔여 수량(`remainingQuantity`)
- `POST /api/v1/drops/{id}/purchase` → 201, 주문 생성

---

#### A-4. 주문 내역 확인
1. MY PAGE → **RECENT ORDERS** 또는 nav → **ORDERS**
2. 결제 상태 확인 (`PAID` / `CONFIRMING`)
3. 주문 상세 클릭 → 주문 ID / 금액 확인

**검증 API:**
- `GET /api/v1/payments/me` → 내 결제 내역 페이지
- `GET /api/v1/orders/{id}` → 주문 상세

---

### 시나리오 B — 사용자 플로우 (래플 응모)

#### B-1. 쿠폰 발급
1. nav → **COUPONS** 클릭
2. 발급 가능한 쿠폰 `발급하기` 클릭 → 선착순 쿠폰 발급

**검증 API:**
- `GET /api/v1/coupons` → 발급 가능 쿠폰 목록
- `POST /api/v1/coupons/{id}` → 발급 완료

---

#### B-2. 래플 응모
1. nav → **RAFFLES** 클릭
2. `RAFFLE LIVE` 배지가 붙은 래플 선택
3. 참가자 수 / 당첨 인원 / 마감 카운트다운 확인
4. **APPLY** 폼:
   - 빌링키 ID 입력 (마이페이지에서 복사)
   - 쿠폰 선택 → 할인 금액 자동 계산
   - **APPLY NOW** 버튼 클릭

**검증 API:**
- `GET /api/v1/raffles` → 래플 목록
- `GET /api/v1/raffles/{id}` → 래플 상세
- `GET /api/v1/raffles/{id}/participants-count` → 참가자 수
- `GET /api/v1/coupons/me` → 보유 쿠폰 (AVAILABLE 필터)
- `POST /api/v1/raffles/{id}/entries` → 응모 완료

---

#### B-3. 응모 결과 확인
1. 래플 상세 페이지 → **응모 완료** 상태 표시
2. 하단 **당첨자 발표 확인 →** 링크 클릭
3. Winners 페이지 → 당첨자 목록 또는 "추첨 전" 안내

**검증 API:**
- `GET /api/v1/raffles/{id}/winners/me` → 내 결과 (`status: PENDING/WIN/LOSE`)
- `GET /api/v1/raffles/{id}/winners` → 공개 당첨자 목록

---

### 시나리오 C — 관리자 플로우

> **관리자 계정** 로그인 필요 (ROLE_ADMIN)

#### C-1. 관리자 계정 생성
1. `http://localhost:5173/admin` 접속
2. 대시보드 우상단 **관리자 생성** 버튼
3. 이메일 / 비밀번호 / 닉네임 입력 후 생성

**검증 API:**
- `POST /api/v1/users/admin/signup` → 201

---

#### C-2. 상품 관리
1. 좌측 nav → **상품 관리**
2. **+ 상품 등록** → 이름/가격/재고/이미지URL 입력 후 등록
3. 등록된 상품 클릭 → **수정** 모달에서 필드 변경
4. **재고 수정** 버튼 → 수량 변경 후 저장

**검증 API:**
- `GET /api/v1/admin/products` → 상품 목록
- `POST /api/v1/admin/products` → 상품 등록
- `PUT /api/v1/admin/products/{id}` → 상품 수정
- `PATCH /api/v1/admin/products/{id}/inventories` → 재고 수정

---

#### C-3. 드롭 관리
1. 좌측 nav → **드롭 관리**
2. **+ 드롭 등록** → 상품 선택 / 시작일 / 종료일 / 수량 입력
3. 드롭 수정 (연필 아이콘) → 종료일 변경 후 저장
4. 드롭 종료 (X 아이콘) → 즉시 종료

**검증 API:**
- `GET /api/v1/admin/drops` → 드롭 목록
- `POST /api/v1/admin/drops` → 드롭 등록
- `PUT /api/v1/admin/drops/{id}` → 수정
- `POST /api/v1/admin/drops/{id}/close` → 종료

---

#### C-4. 래플 관리 (전체 플로우)
1. 좌측 nav → **래플 관리**
2. **+ 래플 생성** → 상품 선택 / 래플명 / 당첨 인원 / 시작일시 / 종료일시 입력
3. 상태 드롭다운 → `SCHEDULED → OPEN` 변경 (응모 시작)
4. **응모자 목록** (People 아이콘) → 응모자 확인 및 패널티 부여 테스트
5. **추첨 실행** (Shuffle 아이콘) → 당첨자 자동 선정 확인
6. 상태가 `CLOSED`로 자동 변경되는지 확인

**검증 API:**
- `POST /api/v1/admin/raffles` → 생성
- `POST /api/v1/admin/raffles/{id}/status` → 상태 변경
- `GET /api/v1/admin/raffles/{id}/entries` → 응모자 목록
- `POST /api/v1/admin/raffles/{id}/draw` → 추첨
- `POST /api/v1/admin/raffles/{id}/entries/{userId}/penalty` → 패널티

---

#### C-5. 결제 내역 관리
1. 좌측 nav → **결제 내역**
2. 상태 필터 (PAID / CANCELED 등) + 판매유형 필터 (DROP / RAFFLE)
3. 결제 행 클릭 → 상세 모달 (원가/할인/최종금액, ID 정보, 일시)

**검증 API:**
- `GET /api/v1/admin/payments` → 결제 목록 (필터링)

---

#### C-6. DLQ & Outbox 관리
1. 좌측 nav → **DLQ 관리**
2. FAILED 상태 메시지 → **재발행** 버튼으로 복구
3. 좌측 nav → **Outbox 이벤트**
4. **전체 재시도** 버튼 → 실패한 이벤트 일괄 재처리

**검증 API:**
- `GET /api/v1/admin/dlq` → DLQ 목록
- `POST /api/v1/admin/dlq/{id}/republish` → 재발행
- `GET /api/v1/admin/outbox-events` → Outbox 목록
- `POST /api/v1/admin/outbox-events/retry-all` → 전체 재시도

---

#### C-7. 쿠폰 관리
1. 좌측 nav → **쿠폰 관리**
2. **+ 쿠폰 생성** → 이름/할인유형/할인값/최대수량/만료일 입력
3. 생성된 쿠폰 목록 확인

**검증 API:**
- `GET /api/v1/coupons` → 쿠폰 목록
- `POST /api/v1/coupons` → 쿠폰 생성

---

## 5. 테스트 데이터 시딩

서버 실행 후 샘플 데이터 주입:

```bash
cd services
node scripts/seed-data.js
```

시딩 내용:
- 상품 5개 (Nike, Jordan 등)
- 드롭 3개 (LIVE 1개 포함)
- 래플 3개 (OPEN 1개, SCHEDULED 1개, CLOSED + 당첨자 1개)
- 쿠폰 3개 (AMOUNT / RATE 혼합)

---

## 6. 주요 구현 사항 (PR 포인트)

### 6.1 백엔드 — raffle-service 신규 구현

| 파일 | 내용 |
|---|---|
| `RaffleController.java` | `GET /winners/me`, `GET /winners`, `GET /participants-count` 추가 |
| `AdminRaffleController.java` | `DELETE`, `POST /status`, `GET /entries`, `POST /entries/{userId}/penalty` 추가 |
| `AdminRaffleAppService.java` | `deleteRaffle()`, `updateRaffleStatus()`, `getRaffleEntries()`, `penalizeUser()` 구현 |
| `RaffleDrawService.java` | 래플 추첨 로직 — WIN/LOSE 결과 저장 |
| `RaffleResultService.java` | `getResult()`, `getPublicResults()` 구현 |

### 6.2 백엔드 — 기존 서비스 보완

| 파일 | 내용 |
|---|---|
| `CouponController.java` | `GET /me` (내 쿠폰 목록) 엔드포인트 추가 |
| `OutboxEventAdminController.java` | `POST /retry-all` 엔드포인트 추가 |
| `UserInternalController.java` | Slack ID 조회 메서드명 수정 |
| `gateway.yml` | `payment-service-admin`, `order-service-admin`, `product-service-outbox` 라우트 3개 추가 |

### 6.3 프론트엔드 — 주요 수정

| 파일 | 수정 내용 |
|---|---|
| `RaffleDetailPage.tsx` | `myResult.result` → `myResult.status`, `WIN`/`LOSE` enum 수정, 응모 폼 완성 |
| `RaffleWinnersPage.tsx` | `WINNER`/`LOSER` → `WIN`/`LOSE`, 실제 winners API 연동 |
| `AdminRafflesPage.tsx` | `STATUS_OPTIONS` 수정(`SCHEDULED/OPEN/CLOSED`), 추첨/삭제 버튼 완성 |
| `AdminDashboard.tsx` | 잘린 파일 완성, 관리자 생성 기능 추가 |
| `AdminPaymentsPage.tsx` | 잘린 파일 완성, 페이지네이션 추가 |
| `MyPage.tsx` | 잘린 파일 완성, 주소 수정 모달 완성 |
| `payments.ts` | binary null byte 제거, `cancelPayment()` 완성 |

---

## 7. 알려진 제약 사항

| 항목 | 내용 |
|---|---|
| 결제 PG | 실제 TossPayments 연동 없음 → 빌링키 localStorage mock 처리 |
| 이미지 업로드 | 외부 URL 입력 방식 (S3 미연동) |
| 알림 실시간 | polling 방식 (WebSocket 미사용) |
| 래플 자동 추첨 | 현재 수동 추첨 방식 (스케줄러 미적용) |

---

*Last Updated: 2026-07-09*  
*OMC Backend + Frontend Integration Verification Complete*
