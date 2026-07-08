# OMC (Only My Clothes) — 시연 시나리오 & API 검증 가이드

> 작성일: 2026-07-09  
> 대상: 시연 영상 촬영 / PR 코드 리뷰

---

## 1. 사전 준비 (시연 시작 전)

### 1-1. 인프라 기동
```bash
# 루트 디렉토리에서
docker-compose up -d          # PostgreSQL, Redis, Kafka, Keycloak, Zookeeper
docker-compose -f docker-compose.services.yml up -d  # 마이크로서비스 전체
```

### 1-2. 시드 데이터 초기화
```bash
node scripts/seed-data.js
```

**생성 결과:**
| 구분 | 항목 | 상태 |
|------|------|------|
| 상품 | Nike Air Jordan 4 Retro | 드롭용 |
| 상품 | Adidas Yeezy Boost 350 V2 | 드롭용 |
| 상품 | Supreme Box Logo Hoodie | 드롭용 |
| 상품 | Stone Island Ghost Piece Crewneck | 드롭용 |
| 상품 | New Balance 990v6 Made in USA | 래플용 |
| 상품 | Carhartt WIP Detroit Jacket | 래플용 |
| 상품 | Porter-Yoshida Tanker Shoulder Bag | 래플용 |
| 상품 | G-SHOCK DW-5600BB All Black | 래플용 |
| 드롭 | Jordan / Yeezy | ⚡ LIVE (시딩 5초 후) |
| 드롭 | Supreme / Stone Island | 🕐 UPCOMING (2시간 후) |
| 래플 | New Balance / Carhartt | ✅ OPEN (강제 상태변경) |
| 래플 | Porter / G-SHOCK | 🕐 UPCOMING (7일 후) |
| 쿠폰 | 신규 가입 5,000원 할인 | 발급 가능 |
| 쿠폰 | 드롭 구매 10,000원 할인 | 발급 가능 |
| 쿠폰 | 래플 10% 할인 (최대 30,000원) | 발급 가능 |
| 쿠폰 | 프리미엄 멤버 20,000원 할인 | 발급 가능 |

### 1-3. 테스트 계정
| 역할 | 이메일 | 비밀번호 |
|------|--------|----------|
| 관리자 | admin@omc.kr | Admin1234! |
| 일반 사용자 | (회원가입 후 사용) | - |

---

## 2. 사용자 시나리오 (User Flow)

### 🎬 Scene 1: 회원가입 & 로그인

**브라우저**: `http://localhost:5173`

1. `/signup` 접속 → 이메일/비밀번호/닉네임 입력 → 회원가입
2. `/login` → 로그인
3. 헤더에 사용자 이름 표시 확인

**검증 API:**
```
POST /api/v1/users/signup      ← user-service
POST /api/v1/users/login       ← user-service (JWT 발급)
GET  /api/v1/users/me          ← user-service (프로필 조회)
```

---

### 🎬 Scene 2: 상품 목록 & 드롭 구매

1. `/` 홈 접속 → 상품 목록 확인 (8개 상품)
2. `/drops` → 드롭 목록 (LIVE 2개, UPCOMING 2개)
3. **LIVE 드롭 클릭** (Jordan 또는 Yeezy)
4. 구매 버튼 클릭 → 빌링키 없을 경우 "결제 정보 없음" 안내
5. 잔여 수량 실시간 표시 확인

**검증 API:**
```
GET  /api/v1/products          ← product-service
GET  /api/v1/drops             ← drop-service
GET  /api/v1/drops/{dropId}    ← drop-service (상세 + 잔여수량)
POST /api/v1/drops/{dropId}/purchase  ← drop-service
```

---

### 🎬 Scene 3: 래플 응모

1. `/raffles` → 래플 목록 (OPEN 2개, UPCOMING 2개)
2. **OPEN 래플 클릭** (New Balance 또는 Carhartt)
3. 응모자 수 실시간 표시 확인
4. 응모 버튼 클릭 → 금액 입력 → 쿠폰 선택 (선택 사항)
5. 응모 완료 후 "응모 완료" 배지 표시

**검증 API:**
```
GET  /api/v1/raffles                          ← raffle-service
GET  /api/v1/raffles/{raffleId}               ← raffle-service
GET  /api/v1/raffles/{raffleId}/participants-count  ← raffle-service (Redis)
POST /api/v1/raffles/{raffleId}/entries       ← raffle-service
GET  /api/v1/raffles/{raffleId}/winners/me    ← raffle-service (결과 조회)
```

---

### 🎬 Scene 4: 쿠폰 발급

1. `/coupons` → 사용 가능한 쿠폰 목록
2. "발급받기" 버튼 클릭 → 선착순 발급
3. 마이페이지에서 내 쿠폰 목록 확인

**검증 API:**
```
GET  /api/v1/coupons               ← coupon-service (공개)
POST /api/v1/coupons/{couponId}/issue  ← coupon-service (발급)
GET  /api/v1/coupons/me            ← coupon-service (내 쿠폰)
```

---

### 🎬 Scene 5: 마이페이지

1. `/mypage` 접속
2. 내 정보 (닉네임, 이메일) 확인
3. 최근 래플 응모 내역 확인
4. 배송 주소 추가 / 수정
5. 알림 목록 확인

**검증 API:**
```
GET   /api/v1/users/me                ← user-service
GET   /api/v1/raffles/entries/me      ← raffle-service
GET   /api/v1/users/addresses         ← user-service
POST  /api/v1/users/addresses         ← user-service
GET   /api/v1/notifications           ← notification-service
PATCH /api/v1/notifications/{id}/read ← notification-service
```

---

## 3. 관리자 시나리오 (Admin Flow)

**브라우저**: `http://localhost:5173/admin`  
**계정**: admin@omc.kr / Admin1234!

---

### 🎬 Admin Scene 1: 대시보드

1. `/admin` → 대시보드 접속
2. 상품/드롭/래플/결제 통계 카드 확인
3. 최근 래플 상태 (OPEN / SCHEDULED) 확인
4. 최근 결제 내역 실시간 확인

**검증 API:**
```
GET /api/v1/products          ← product-service
GET /api/v1/admin/drops       ← drop-service
GET /api/v1/raffles           ← raffle-service
GET /api/v1/admin/payments    ← payment-service
```

---

### 🎬 Admin Scene 2: 상품 관리

1. `/admin/products` → 상품 목록 (8개)
2. **상품 수정** → 가격/이름 변경 후 저장
3. **재고 관리** → 재고 수량 변경

**검증 API:**
```
GET   /api/v1/products                          ← product-service
PATCH /api/v1/admin/products/{productId}        ← product-service
GET   /api/v1/admin/products/{productId}/inventories  ← product-service
PATCH /api/v1/admin/products/{productId}/inventories  ← product-service
```

---

### 🎬 Admin Scene 3: 드롭 관리

1. `/admin/drops` → 드롭 목록
2. **드롭 생성** → 상품 선택 + 시작/종료 시간 입력
3. **드롭 강제 종료** → CLOSE 버튼

**검증 API:**
```
GET    /api/v1/admin/drops             ← drop-service
POST   /api/v1/admin/drops             ← drop-service
PUT    /api/v1/admin/drops/{dropId}    ← drop-service
POST   /api/v1/admin/drops/{dropId}/close  ← drop-service
DELETE /api/v1/admin/drops/{dropId}   ← drop-service
```

---

### 🎬 Admin Scene 4: 래플 관리 (핵심)

1. `/admin/raffles` → 래플 목록 확인
2. **래플 생성** → 상품/기간/당첨자 수 입력
3. **상태 변경** → SCHEDULED → OPEN → CLOSED
4. **응모자 목록** → 상세 팝업에서 응모자 확인
5. **추첨 실행** → 당첨자 자동 선정
6. **당첨자 목록** → `/raffles/{raffleId}/winners`에서 확인
7. **패널티 부여** → 악성 당첨자 패널티 처리

**검증 API:**
```
GET    /api/v1/raffles                                    ← raffle-service
POST   /api/v1/admin/raffles                              ← raffle-service
PUT    /api/v1/admin/raffles/{raffleId}                   ← raffle-service
POST   /api/v1/admin/raffles/{raffleId}/status            ← raffle-service
GET    /api/v1/admin/raffles/{raffleId}/entries           ← raffle-service
POST   /api/v1/admin/raffles/{raffleId}/draw              ← raffle-service
GET    /api/v1/raffles/{raffleId}/winners                 ← raffle-service
POST   /api/v1/admin/raffles/{raffleId}/entries/{userId}/penalty  ← raffle-service
DELETE /api/v1/admin/raffles/{raffleId}                   ← raffle-service
```

---

### 🎬 Admin Scene 5: 결제 관리

1. `/admin/payments` → 전체 결제 내역
2. 상태 필터 (PAID / CANCELED / FAILED)
3. 결제 상세 모달 → 결제 정보 확인
4. 결제 취소 처리

**검증 API:**
```
GET  /api/v1/admin/payments             ← payment-service
POST /api/v1/payments/{paymentId}/cancel ← payment-service
```

---

### 🎬 Admin Scene 6: 쿠폰 관리

1. `/admin/coupons` → 쿠폰 목록 (4개)
2. 쿠폰 생성 (정액 / 정률)
3. 쿠폰 삭제

**검증 API:**
```
GET    /api/v1/coupons               ← coupon-service
POST   /api/v1/coupons               ← coupon-service
DELETE /api/v1/coupons/{couponId}    ← coupon-service
```

---

### 🎬 Admin Scene 7: 운영 도구

1. `/admin/dlq` → Dead Letter Queue 조회 + 재처리
2. `/admin/outbox` → Outbox 이벤트 + 전체 재처리

**검증 API:**
```
GET  /api/v1/admin/dlq                              ← order-service
POST /api/v1/admin/dlq/{dlqId}/republish            ← order-service
POST /api/v1/admin/outbox-events/{eventId}/retry    ← product-service
POST /api/v1/admin/outbox-events/retry-all          ← product-service
```

---

## 4. 전체 API ↔ 서비스 라우팅 매핑

| Gateway Path Pattern | 서비스 | 포트 |
|---------------------|--------|------|
| `/api/v1/users/**` | user-service | - |
| `/api/v1/products/**` | product-service | - |
| `/api/v1/admin/products/**` | product-service | - |
| `/api/v1/admin/outbox-events/**` | product-service | - |
| `/api/v1/drops/**` | drop-service | - |
| `/api/v1/admin/drops/**` | drop-service | - |
| `/api/v1/raffles/**` | raffle-service | - |
| `/api/v1/admin/raffles/**` | raffle-service | - |
| `/api/v1/orders/**` | order-service | - |
| `/api/v1/admin/dlq/**` | order-service | - |
| `/api/v1/payments/**` | payment-service | - |
| `/api/v1/admin/payments/**` | payment-service | - |
| `/api/v1/coupons/**` | coupon-service | - |
| `/api/v1/notifications/**` | notification-service | - |

> Gateway: `http://localhost:8080`  
> Swagger UI: `http://localhost:8080/swagger-ui.html`

---

## 5. 이번 세션에서 수정된 내용 요약 (PR 설명용)

### Backend (raffle-service)
- **RaffleController**: `winners/me`, `winners`, `participants-count` 엔드포인트 추가
- **AdminRaffleController**: `status`, `entries`, `delete`, `penalty` 엔드포인트 추가
- **AdminRaffleAppService**: `penalizeUser()` 메서드 + `RafflePenaltyRepository` 주입

### Backend (coupon-service)
- **CouponController**: `GET /me` (내 쿠폰 목록) 엔드포인트 추가

### Backend (product-service)
- **OutboxEventAdminController**: `POST /retry-all` 전체 재처리 엔드포인트 추가

### Backend (user-service)
- **UserInternalController**: 잘린 파일 복구 및 slack batch 엔드포인트 추가

### Gateway (config-server)
- `payment-service-admin`: `POST /api/v1/admin/payments/**` 라우트 추가
- `order-service-admin`: `GET /api/v1/admin/dlq/**` 라우트 추가
- `product-service-outbox`: `POST /api/v1/admin/outbox-events/**` 라우트 추가

### Frontend
- `RaffleDetailPage`: `myResult.status` 필드명 수정 (`WIN`/`LOSE` enum 사용)
- `RaffleWinnersPage`: `RESULT_COLOR` 키 `WIN`/`LOSE`로 수정, retry 제거
- `AdminRafflesPage`: enum 불일치 (`WINNER`→`WIN`, `LOSER`→`LOSE`) 수정
- `MyPage`: enum 불일치 (`WINNER`→`WIN`, `LOSER`→`LOSE`) 수정

---

## 6. 알려진 제한 사항

| 항목 | 상태 | 비고 |
|------|------|------|
| 빌링키 없는 드롭/래플 구매 | ❌ 불가 | Toss Payments 연동 필요 |
| Keycloak SSO | ✅ 로컬 구동 | `localhost:8180` |
| 래플 자동 추첨 | 수동 가능 | 어드민에서 Draw 버튼 사용 |
| 이메일 알림 | ❌ 비활성 | SMTP 미설정 |
| Slack 알림 | 설정 시 가능 | SLACK_BOT_TOKEN 필요 |
