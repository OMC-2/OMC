Feature: [시나리오] 드롭 구매 → 결제 자동 승인 → 재고 차감 → 주문 확정

  # ================================================================
  # 드롭 구매 → 내부 API 우회 없이 Kafka 자동 흐름만으로 결제~주문확정 시나리오
  # 실행: bash e2e/run.sh scenario/01_drop_purchase/03_auto_payment_chain
  #
  # 검증 흐름:
  #   어드민: 상품 등록 → 드롭 생성
  #   유저: 구매 선점 → orderId 발급
  #   (내부 API 호출 없음 — 여기서부터는 전부 Kafka 자동 흐름)
  #   Order: purchase.confirmed 소비 → 주문 생성 → order.created 발행
  #   Payment: order.created 소비 → PaymentEventService.handleOrderCreated()
  #            → confirmPayment() 자동 실행 → payment.completed 발행
  #   Product: payment.completed 소비 → 재고 차감 → stock.deducted 발행
  #   Order: stock.deducted 소비 → 주문 CONFIRMED → order.confirmed 발행
  #   Notification: order.confirmed 소비 → ORDER_CONFIRMED 알림 생성
  #
  # 검증 포인트:
  #   1. 구매 선점 → 202, orderId 발급
  #   2. payment/me에서 해당 orderId의 결제가 자동으로 PAID 되는지 확인
  #      (내부 confirm API를 전혀 호출하지 않았는데도 PAID여야 함)
  #   3. ORDER_CONFIRMED 알림 수신 확인 (전체 Kafka 체인이 안 끊겼다는 증거)
  #
  # 주의:
  #   - 이 시나리오는 결제를 명시적으로 승인시키는 API 호출이 전혀 없다.
  #     order.created 이벤트 페이로드에 필요한 필드(특히 productId)가
  #     하나라도 빠지면, payment-service의 검증 단계에서 조용히 실패하고
  #     이 시나리오는 타임아웃(재시도 횟수 소진)으로 실패한다.
  #   - Kafka 체인 완료까지 최대 60초(30회 × 2초) 폴링한다.
  # ================================================================

  Background:
    * url baseUrl
    * def fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    * def adminEmail = 'e2e-drop-autochain-admin-' + java.util.UUID.randomUUID() + '@example.com'
    * def userEmail  = 'e2e-drop-autochain-user-'  + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'

    # ── 1. ADMIN 계정 생성 + 로그인 ──────────────────────────────────
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: #(adminEmail), password: #(testPassword), nickname: 'dropautochainadmin' }
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
    And request { email: #(userEmail), password: #(testPassword), nickname: 'dropautochainuser' }
    When method post
    Then status 201
    * def userId = response.data.userId

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def userToken = response.data.accessToken

    # ── 3. 상품 등록 (쿠폰 없이 최소 구성) ────────────────────────────
    Given path '/api/v1/admin/products'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '자동결제체인 테스트 상품', description: '내부 API 우회 없는 E2E용', price: 10000, brand: 'TestBrand', category: 'Sneakers', imageUrl: 'http://img.test/2.jpg', initialQuantity: 10 }
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

    # ── 5. 드롭 OPEN 전이 대기 (DropStatusScheduler 5초 주기) ────────
    * configure retry = { count: 15, interval: 1000 }
    * retry until response.data.status == 'OPEN'
    Given path '/api/v1/drops/' + dropId
    And header X-Gateway-Secret = gatewaySecret
    When method get

  # ----------------------------------------------------------------
  # 시나리오: 구매 선점만 하고, 결제는 order.created 자동 소비로만 완료돼야 함
  #   (내부 confirm API 호출 없음 — payment 자동 승인 → 재고 차감 → 주문 확정 → 알림)
  # ----------------------------------------------------------------
  Scenario: [드롭] 결제 승인 API 호출 없이 order.created 자동 소비만으로 결제~주문확정까지 완주
    # STEP 1: 구매 선점 (여기서 끝 — 이후 결제 관련 API는 전혀 호출하지 않음)
    Given path '/api/v1/drops/' + dropId + '/purchase'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method post
    Then status 202
    And match response.data.orderId == '#uuid'
    * def orderId = response.data.orderId

    # STEP 2: payment/me 폴링 — 내부 API를 호출한 적이 없는데도 PAID가 되는지 확인
    #   order.created(Order) → handleOrderCreated(Payment, 자동 승인) → payment.completed
    * configure retry = { count: 30, interval: 2000 }
    Given path '/api/v1/payments/me'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    And retry until karate.filter(response.data.content, function(p){ return p.orderId == orderId && p.paymentStatus == 'PAID' }).length > 0
    When method get
    Then status 200
    * def paidPayments = karate.filter(response.data.content, function(p){ return p.orderId == orderId && p.paymentStatus == 'PAID' })
    And assert paidPayments.length > 0
    And match paidPayments[0].finalAmount == 10000

    # STEP 3: ORDER_CONFIRMED 알림 수신 확인
    #   payment.completed → Product(재고차감) → stock.deducted → Order(CONFIRMED) → order.confirmed → notification
    * configure retry = { count: 30, interval: 2000 }
    Given path '/api/v1/notifications'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    And retry until karate.filter(response.data.content, function(n){ return n.notificationType == 'ORDER_CONFIRMED' }).length > 0
    When method get
    Then status 200
    * def confirmedNotifications = karate.filter(response.data.content, function(n){ return n.notificationType == 'ORDER_CONFIRMED' })
    And assert confirmedNotifications.length > 0
