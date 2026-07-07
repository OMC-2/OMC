Feature: 주문 서비스 자체 시나리오 (조회 · 환불 상태검증 · DLQ 관리)

  # ================================================================
  # order-service 자체 API 시나리오 (REST 레벨 검증)
  # 실행: bash e2e/run.sh order/order_service
  #
  # 시나리오 목록:
  #   1. [정상] 구매 선점 후 주문 단건 조회 → 200, PENDING_PAYMENT
  #   2. [예외] 존재하지 않는 orderId 조회 → 404 + ORDER-001
  #   3. [예외] CONFIRMED 아닌 주문 환불 요청 → 403 + ORDER-004 (REFUND_NOT_ALLOWED)
  #   4. [예외] 토큰 없이 주문 조회 → 401
  #   5. [정상] 관리자 DLQ 목록 조회 → 200, 페이징 구조
  #
  # 설계 메모:
  #   - order 는 이벤트 소비 기반 서비스라 "상품 조회 실패 → DLQ 적재",
  #     "중복 이벤트 멱등성"은 Kafka 주입 기반이라 별도 통합/단위 테스트로 검증한다.
  #     (멱등성: OrderEventConsumerIdempotencyTest 단위 테스트로 검증 완료)
  #   - 본 시나리오는 order 의 REST API(조회/환불/DLQ) 레벨 계약을 검증한다.
  #   - 환불은 CONFIRMED 상태에서만 허용되므로, 선점 직후(PENDING_PAYMENT)
  #     환불 시도는 403(ORDER-004)이 되는 것을 검증한다.
  # ================================================================

  Background:
    * url baseUrl
    * def fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    * def adminEmail = 'e2e-order-admin-' + java.util.UUID.randomUUID() + '@example.com'
    * def userEmail  = 'e2e-order-user-'  + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'

    # ── ADMIN 생성 + 로그인 ──────────────────────────────────────────
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: #(adminEmail), password: #(testPassword), nickname: 'e2eorderadmin' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(adminEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def adminToken = response.data.accessToken

    # ── USER 생성 + 로그인 ───────────────────────────────────────────
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword), nickname: 'e2eorderuser' }
    When method post
    Then status 201
    * def userId = response.data.userId

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def userToken = response.data.accessToken

    # ── 상품 등록 + 드롭 생성 + OPEN 대기 (주문 생성용 사전 준비) ──────
    Given path '/api/v1/admin/products'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '주문 시나리오 상품', description: 'order E2E용', price: 10000, brand: 'TestBrand', category: 'Sneakers', imageUrl: 'http://img.test/order.jpg', initialQuantity: 100 }
    When method post
    Then status 201
    * def productId = response.data.productId

    * def startAt = java.time.LocalDateTime.now().plusSeconds(5).format(fmt)
    * def endAt   = java.time.LocalDateTime.now().plusDays(1).format(fmt)

    Given path '/api/v1/admin/drops'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { productId: #(productId), startAt: #(startAt), endAt: #(endAt), totalQty: 10, holdTtlSec: 300 }
    When method post
    Then status 201
    * def dropId = response.data.dropId

    * configure retry = { count: 15, interval: 1000 }
    * retry until response.data.status == 'OPEN'
    Given path '/api/v1/drops/' + dropId
    And header X-Gateway-Secret = gatewaySecret
    When method get

  # ----------------------------------------------------------------
  # 시나리오 1: 구매 선점 후 주문 단건 조회 → 200, PENDING_PAYMENT
  # ----------------------------------------------------------------
  Scenario: [정상] 구매 선점 후 주문 조회 → 200, PENDING_PAYMENT + productId 포함
    Given path '/api/v1/drops/' + dropId + '/purchase'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method post
    Then status 202
    * def orderId = response.data.orderId

    # 주문이 PENDING_PAYMENT 로 생성될 때까지 잠깐 대기 (이벤트 소비 반영)
    * configure retry = { count: 15, interval: 1000 }
    * retry until responseStatus == 200 && response.data.status == 'PENDING_PAYMENT'
    Given path '/api/v1/orders/' + orderId
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method get
    Then status 200
    And match response.data.orderId == orderId
    And match response.data.status == 'PENDING_PAYMENT'
    And match response.data.orderType == 'DROP'
    # productId 가 이벤트/주문에 정상 반영되는지 (계약 누락 회귀 방지)
    And match response.data.productId == productId

  # ----------------------------------------------------------------
  # 시나리오 2: 존재하지 않는 orderId 조회 → 404 + ORDER-001
  # ----------------------------------------------------------------
  Scenario: [예외] 존재하지 않는 orderId 조회 → 404 + ORDER-001
    Given path '/api/v1/orders/' + java.util.UUID.randomUUID()
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method get
    Then status 404
    And match response.errorCode == 'ORDER-001'

  # ----------------------------------------------------------------
  # 시나리오 3: CONFIRMED 아닌 주문 환불 요청 → 403 + ORDER-004
  #   선점 직후 주문은 PENDING_PAYMENT 이므로 환불 불가여야 한다.
  # ----------------------------------------------------------------
  Scenario: [예외] PENDING_PAYMENT 주문 환불 요청 → 403 + ORDER-004
    Given path '/api/v1/drops/' + dropId + '/purchase'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method post
    Then status 202
    * def orderId = response.data.orderId

    # PENDING_PAYMENT 생성 대기
    * configure retry = { count: 15, interval: 1000 }
    * retry until responseStatus == 200 && response.data.status == 'PENDING_PAYMENT'
    Given path '/api/v1/orders/' + orderId
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method get

    # 환불 요청 → CONFIRMED 아니므로 403
    Given path '/api/v1/orders/' + orderId + '/refund'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method post
    Then status 403
    And match response.errorCode == 'ORDER-004'

  # ----------------------------------------------------------------
  # 시나리오 4: 토큰 없이 주문 조회 → 401
  # ----------------------------------------------------------------
  Scenario: [예외] 토큰 없이 주문 조회 → 401
    Given path '/api/v1/orders/' + java.util.UUID.randomUUID()
    And header X-Gateway-Secret = gatewaySecret
    When method get
    Then status 401

  # ----------------------------------------------------------------
  # 시나리오 5: 관리자 DLQ 목록 조회 → 200, 페이징 구조 검증
  #   (장애 격리된 메시지를 운영자가 조회하는 Admin API 계약 검증)
  # ----------------------------------------------------------------
  Scenario: [정상] 관리자 DLQ 목록 조회 → 200, 페이징 구조
    * url orderServiceUrl
    Given path '/api/v1/admin/dlq'
    And param status = 'FAILED'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    # 페이징 응답 구조 확인 (content 배열 + totalElements)
    And match response.data.content == '#array'
    And match response.data.totalElements == '#number'
