Feature: [시나리오] 선착순 드롭 정상 구매 (쿠폰 적용 + 결제 완료 + 알림)

  # ================================================================
  # 선착순(INSTANT) 드롭 + 쿠폰 적용 전체 플로우 시나리오
  # 실행: bash e2e/run.sh scenario/01_drop_purchase/01_normal
  #
  # 검증 흐름:
  #   어드민: 쿠폰 생성 → 상품 등록 → 드롭 생성
  #   유저: 쿠폰 발급 (AVAILABLE)
  #   스케줄러: SCHEDULED → OPEN 전이
  #   유저: 구매 선점 → orderId 발급
  #   유저: 쿠폰 적용 결제 승인 → 쿠폰 RESERVED (동기), payment PAID
  #   Kafka: payment.completed → coupon USED 확정
  #   Kafka: payment.completed → stock.deducted → order.confirmed → ORDER_CONFIRMED 알림
  #
  # 검증 포인트:
  #   1. 구매 선점 → 202, orderId + queueNumber
  #   2. 결제 승인 (쿠폰 적용) → 201, paymentStatus=PAID, finalAmount=9000
  #   3. 쿠폰 상태 → RESERVED (결제 직후 동기 처리)
  #   4. payment/me → PAID, finalAmount=9000 내역 존재
  #   5. ORDER_CONFIRMED 알림 수신 (전체 Kafka 체인 증명)
  #   6. 쿠폰 최종 상태 → USED (payment.completed → coupon-service 확정)
  #
  # 주의:
  #   - payment 내부 API는 paymentServiceUrl(8085)로 직접 호출한다.
  #   - Kafka 체인 완료까지 최대 60초(30회 × 2초) 폴링한다.
  #   - 쿠폰: AMOUNT 1000원 할인, 상품가격 10000원 → finalAmount 9000원
  # ================================================================

  Background:
    * url baseUrl
    * def fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    * def adminEmail = 'e2e-drop-normal-admin-' + java.util.UUID.randomUUID() + '@example.com'
    * def userEmail  = 'e2e-drop-normal-user-'  + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'

    # ── 1. ADMIN 계정 생성 + 로그인 ──────────────────────────────────
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: #(adminEmail), password: #(testPassword), nickname: 'dropnormaladmin' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(adminEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def adminToken = response.data.accessToken

    # ── 2. USER 계정 생성 + 로그인 ───────────────────────────────────
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword), nickname: 'dropnormaluser' }
    When method post
    Then status 201
    * def userId = response.data.userId

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def userToken = response.data.accessToken

    # ── 3. 쿠폰 생성 (AMOUNT 1000원 할인) ───────────────────────────
    Given path '/api/v1/coupons'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '드롭 정상구매 할인 쿠폰', discountType: 'AMOUNT', discountValue: 1000, totalQuantity: 100, startedAt: '2020-01-01T00:00:00', expiredAt: '2099-12-31T23:59:59' }
    When method post
    Then status 201
    * def couponId = response.data.couponId

    # ── 4. 유저 쿠폰 발급 (비동기 202) → Consumer 처리 대기 → userCouponId 확보 ─
    Given path '/api/v1/coupons/' + couponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method post
    Then status 202
    * java.lang.Thread.sleep(3000)
    Given path '/api/v1/coupons/me'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method get
    Then status 200
    * def userCouponId = response.data.content[0].userCouponId

    # ── 5. 상품 등록 ─────────────────────────────────────────────────
    Given path '/api/v1/admin/products'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '드롭 정상구매 테스트 상품', description: '드롭 E2E용', price: 10000, brand: 'TestBrand', category: 'Sneakers', imageUrl: 'http://img.test/1.jpg', initialQuantity: 100 }
    When method post
    Then status 201
    * def productId = response.data.productId

    # ── 6. 드롭 생성 (startAt +5초 → 스케줄러가 곧 OPEN) ────────────
    * def startAt = java.time.LocalDateTime.now().plusSeconds(5).format(fmt)
    * def endAt   = java.time.LocalDateTime.now().plusDays(1).format(fmt)

    Given path '/api/v1/admin/drops'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { productId: #(productId), startAt: #(startAt), endAt: #(endAt), totalQty: 10, holdTtlSec: 300 }
    When method post
    Then status 201
    * def dropId = response.data.dropId

    # ── 7. 드롭 OPEN 전이 대기 (DropStatusScheduler 5초 주기) ────────
    * configure retry = { count: 15, interval: 1000 }
    * retry until response.data.status == 'OPEN'
    Given path '/api/v1/drops/' + dropId
    And header X-Gateway-Secret = gatewaySecret
    When method get

  # ----------------------------------------------------------------
  # 시나리오: 쿠폰 적용 선착순 드롭 전체 구매 해피패스
  #   구매 선점 → 쿠폰 적용 결제 → 쿠폰 RESERVED 확인
  #   → payment/me 확인 → ORDER_CONFIRMED 알림 → 쿠폰 USED 확인
  # ----------------------------------------------------------------
  Scenario: [드롭] 쿠폰 적용 드롭 구매 → 결제 → 쿠폰 USED → 주문 확정 → 알림 수신
    # STEP 1: 구매 선점
    Given path '/api/v1/drops/' + dropId + '/purchase'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method post
    Then status 202
    And match response.data.orderId == '#uuid'
    And match response.data.queueNumber == '#number'
    * def orderId = response.data.orderId

    # STEP 2: 쿠폰 적용 결제 승인 (payment internal API 직접 호출)
    #   쿠폰 1000원 할인: originalAmount=10000, discountAmount=1000, finalAmount=9000
    #   payment-service가 coupon internal API를 동기 호출 → 쿠폰 AVAILABLE → RESERVED
    * url paymentServiceUrl
    Given path '/internal/v1/payments/confirm'
    And header X-User-Id = userId
    And request { orderID: #(orderId), dropId: #(dropId), productId: #(productId), providerPaymentId: 'E2E-DROP-NORMAL-001', couponID: #(userCouponId), originalAmount: 10000, discountAmount: 1000, finalAmount: 9000 }
    When method post
    Then status 201
    And match response.paymentStatus == 'PAID'
    And match response.finalAmount == 9000
    * def paymentId = response.paymentId

    # STEP 3: 쿠폰 상태 → RESERVED 확인 (결제 직후 동기 처리)
    * url baseUrl
    Given path '/api/v1/coupons/me/' + userCouponId
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method get
    Then status 200
    And match response.data.status == 'RESERVED'

    # STEP 4: payment/me 에서 PAID + 할인가 확인 (gateway 경유)
    Given path '/api/v1/payments/me'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method get
    Then status 200
    * def paidPayments = karate.filter(response.data.content, function(p){ return p.orderId == orderId && p.paymentStatus == 'PAID' })
    And assert paidPayments.length > 0
    And match paidPayments[0].finalAmount == 9000
    And match paidPayments[0].discountAmount == 1000

    # STEP 5: ORDER_CONFIRMED 알림 수신 확인
    #   Kafka 체인: payment.completed → stock.deducted → order.confirmed → notification
    #   최대 60초 대기 (30회 × 2초)
    * configure retry = { count: 30, interval: 2000 }
    * retry until karate.filter(response.data.content, function(n){ return n.notificationType == 'ORDER_CONFIRMED' }).length > 0
    Given path '/api/v1/notifications'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method get
    Then status 200
    * def confirmedNotifications = karate.filter(response.data.content, function(n){ return n.notificationType == 'ORDER_CONFIRMED' })
    And assert confirmedNotifications.length > 0
    And match confirmedNotifications[0].title == '주문 확정 알림'

    # STEP 6: 쿠폰 최종 상태 → USED 확인
    #   Kafka: payment.completed → coupon-service.confirmCoupon → RESERVED → USED
    * configure retry = { count: 15, interval: 2000 }
    * retry until response.data.status == 'USED'
    Given path '/api/v1/coupons/me/' + userCouponId
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method get
    Then status 200
    And match response.data.status == 'USED'
