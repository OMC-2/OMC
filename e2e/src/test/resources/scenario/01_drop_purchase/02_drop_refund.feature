Feature: [시나리오] 선착순 드롭 환불 (구매→결제→확정→환불→알림)

  # ================================================================
  # 선착순(INSTANT) 드롭 구매 후 환불 전체 플로우 시나리오
  # 실행: bash e2e/run.sh scenario/01_drop_purchase/02_refund
  #
  # 검증 흐름 (1-B 환불):
  #   [사전] 어드민: 상품 등록 → 드롭 생성 / 유저: 구매 선점 → 결제 → 주문 CONFIRMED 까지
  #   [본문] 유저: 환불 요청 → order REFUND_REQUESTED
  #          Kafka: refund.requested → payment 취소 → refund.done → order REFUNDED
  #          유저: 환불 알림(ORDER_REFUNDED) 수신
  #
  # 검증 포인트:
  #   1. 구매 선점 → 202, orderId 발급
  #   2. 결제 승인 → 201, PAID
  #   3. 주문 CONFIRMED 도달 (Kafka 체인: payment.completed → stock.deducted → order.confirmed)
  #   4. 환불 요청 → 200 (CONFIRMED 상태이므로 허용)
  #   5. 주문 상태 → REFUND_REQUESTED (동기)
  #   6. 주문 상태 → REFUNDED (Kafka: refund.requested → payment 취소 → refund.done)
  #   7. 환불 알림(ORDER_REFUNDED) 수신
  #
  # 주의:
  #   - 환불은 order 가 CONFIRMED 여야 허용된다(아니면 403 REFUND_NOT_ALLOWED).
  #     따라서 결제+재고차감까지 완료(CONFIRMED)를 먼저 대기한 뒤 환불한다.
  #   - payment 내부 API 는 paymentServiceUrl(8085)로 직접 호출한다.
  #   - Kafka 체인 완료까지 폴링(최대 60초)한다.
  # ================================================================

  Background:
    * url baseUrl
    * def fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    * def adminEmail = 'e2e-drop-refund-admin-' + java.util.UUID.randomUUID() + '@example.com'
    * def userEmail  = 'e2e-drop-refund-user-'  + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'

    # ── 1. ADMIN 계정 생성 + 로그인 ──────────────────────────────────
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: #(adminEmail), password: #(testPassword), nickname: 'droprefundadmin' }
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
    And request { email: #(userEmail), password: #(testPassword), nickname: 'droprefunduser' }
    When method post
    Then status 201
    * def userId = response.data.userId

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def userToken = response.data.accessToken

    # ── 3. 상품 등록 ─────────────────────────────────────────────────
    Given path '/api/v1/admin/products'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '드롭 환불 테스트 상품', description: '드롭 환불 E2E용', price: 10000, brand: 'TestBrand', category: 'Sneakers', imageUrl: 'http://img.test/refund.jpg', initialQuantity: 100 }
    When method post
    Then status 201
    * def productId = response.data.productId

    # ── 4. 드롭 생성 (startAt +5초 → 스케줄러가 곧 OPEN) ────────────
    * def startAt = java.time.LocalDateTime.now().plusSeconds(5).format(fmt)
    * def endAt   = java.time.LocalDateTime.now().plusDays(1).format(fmt)

    Given path '/api/v1/admin/drops'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { productId: #(productId), startAt: #(startAt), endAt: #(endAt), totalQty: 10, holdTtlSec: 300 }
    When method post
    Then status 201
    * def dropId = response.data.dropId

    # ── 5. 드롭 OPEN 전이 대기 ───────────────────────────────────────
    * configure retry = { count: 15, interval: 1000 }
    * retry until response.data.status == 'OPEN'
    Given path '/api/v1/drops/' + dropId
    And header X-Gateway-Secret = gatewaySecret
    When method get

  # ----------------------------------------------------------------
  # 시나리오: 드롭 구매 → 결제 → CONFIRMED → 환불 요청 → REFUNDED → 환불 알림
  # ----------------------------------------------------------------
  Scenario: [드롭] 구매 확정 후 환불 요청 → REFUNDED → 환불 알림 수신
    # STEP 1: 구매 선점
    Given path '/api/v1/drops/' + dropId + '/purchase'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method post
    Then status 202
    And match response.data.orderId == '#uuid'
    * def orderId = response.data.orderId

    # STEP 2: 결제 승인 (쿠폰 미적용, 정가 결제)
    * url paymentServiceUrl
    Given path '/internal/v1/payments/confirm'
    And header X-User-Id = userId
    And request { orderID: #(orderId), dropId: #(dropId), productId: #(productId), providerPaymentId: 'E2E-DROP-REFUND-001', couponID: null, originalAmount: 10000, discountAmount: 0, finalAmount: 10000 }
    When method post
    Then status 201
    And match response.paymentStatus == 'PAID'

    # STEP 3: 주문이 CONFIRMED 에 도달할 때까지 대기
    #   Kafka 체인: payment.completed → stock.deducted → order.confirmed
    #   환불은 CONFIRMED 상태여야 허용되므로 반드시 선행되어야 한다.
    * url baseUrl
    * configure retry = { count: 30, interval: 2000 }
    * retry until responseStatus == 200 && response.data.status == 'CONFIRMED'
    Given path '/api/v1/orders/' + orderId
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method get
    Then status 200
    And match response.data.status == 'CONFIRMED'

    # STEP 4: 환불 요청 (CONFIRMED 상태이므로 허용 → 200)
    Given path '/api/v1/orders/' + orderId + '/refund'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method post
    Then status 200

    # STEP 5: 주문 상태 → REFUND_REQUESTED (환불 요청 직후 동기 전이)
    Given path '/api/v1/orders/' + orderId
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method get
    Then status 200
    And match response.data.status == 'REFUND_REQUESTED'

    # STEP 6: 주문 상태 → REFUNDED 도달 대기
    #   Kafka: refund.requested → payment PG 취소 → refund.done → order REFUNDED
    * configure retry = { count: 30, interval: 2000 }
    * retry until responseStatus == 200 && response.data.status == 'REFUNDED'
    Given path '/api/v1/orders/' + orderId
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method get
    Then status 200
    And match response.data.status == 'REFUNDED'

    # STEP 7: 환불 알림(ORDER_REFUNDED) 수신 확인
    * configure retry = { count: 30, interval: 2000 }
    * retry until karate.filter(response.data.content, function(n){ return n.notificationType == 'ORDER_REFUNDED' }).length > 0
    Given path '/api/v1/notifications'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method get
    Then status 200
    * def refundNotifications = karate.filter(response.data.content, function(n){ return n.notificationType == 'ORDER_REFUNDED' })
    And assert refundNotifications.length > 0
