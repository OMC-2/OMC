Feature: [시나리오] 재고 차감 실패 이벤트 시작 결제 승인 취소 보상

  # ================================================================
  # stock.failed 이벤트 기반 보상 트랜잭션 시나리오
  # 실행: bash e2e/run.sh scenario/04_payment_saga/02-1_stock_failure_event_start
  #
  # 검증 흐름:
  #   유저: 드롭 구매 선점 → 주문 생성
  #   Payment: 결제 승인 → PAID
  #   E2E Helper: stock.failed 이벤트 직접 발행
  #   Payment: 결제 승인 취소 → CANCELED
  #   Order: 주문 취소 → order.cancelled 발행
  #   Notification: ORDER_CANCELLED 알림 생성
  #
  # 검증 범위:
  #   Product Service가 실제로 stock.failed를 판단하고 발행하는 과정은 제외
  #   stock.failed 수신 이후 서비스 간 보상 흐름을 검증
  # ================================================================

  Background:
    * url baseUrl
    * def fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    * def uniqueId = java.util.UUID.randomUUID().toString()
    * def testPassword = 'password123'
    * def adminEmail = 'e2e-sfe-a-' + uniqueId + '@example.com'
    * def userEmail = 'e2e-sfe-u-' + uniqueId + '@example.com'

    # User Service에 테스트용 관리자 계정을 생성
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: '#(adminEmail)', password: '#(testPassword)', nickname: 'stockfailureeventadmin' }
    When method post
    Then status 201

    # User Service에서 관리자 계정으로 로그인해 JWT를 발급
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: '#(adminEmail)', password: '#(testPassword)' }
    When method post
    Then status 200
    * def adminToken = response.data.accessToken

    # User Service에 구매자 계정을 생성
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: '#(userEmail)', password: '#(testPassword)', nickname: 'stockfailureeventuser' }
    When method post
    Then status 201
    * def userId = response.data.userId

    # User Service에서 구매자 계정으로 로그인해 JWT를 발급
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: '#(userEmail)', password: '#(testPassword)' }
    When method post
    Then status 200
    * def userToken = response.data.accessToken

  Scenario: stock.failed 이벤트 발행 후 결제 취소와 주문 취소 알림 처리
    # Product Service에 재고가 있는 테스트 상품을 생성
    Given path '/api/v1/admin/products'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '재고 실패 이벤트 보상 테스트 상품', description: '재고 실패 이벤트 보상 E2E용', price: 10000, brand: 'TestBrand', category: 'Sneakers', imageUrl: 'http://img.test/stock-failure-event.jpg', initialQuantity: 1 }
    When method post
    Then status 201
    * def productId = response.data.productId

    * def startAt = java.time.LocalDateTime.now().plusSeconds(5).format(fmt)
    * def endAt = java.time.LocalDateTime.now().plusDays(1).format(fmt)

    # Drop Service에 판매 수량이 1개인 테스트 드롭을 생성
    Given path '/api/v1/admin/drops'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { productId: '#(productId)', startAt: '#(startAt)', endAt: '#(endAt)', totalQty: 1, holdTtlSec: 60 }
    When method post
    Then status 201
    * def dropId = response.data.dropId

    # Drop Service에서 드롭이 구매 가능한 OPEN 상태가 될 때까지 조회
    * configure retry = { count: 20, interval: 1000 }
    * retry until response.data.status == 'OPEN'
    Given path '/api/v1/drops/' + dropId
    And header X-Gateway-Secret = gatewaySecret
    When method get
    Then status 200

    # Drop Service에서 구매자가 상품을 선점하고 주문 ID를 발급
    Given path '/api/v1/drops/' + dropId + '/purchase'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method post
    Then status 202
    * def orderId = response.data.orderId

    # Payment Service 내부 API를 호출해 보상 전 정상 결제를 승인
    * url paymentServiceUrl
    Given path '/internal/v1/payments/confirm'
    And header X-User-Id = userId
    And request { orderID: '#(orderId)', dropId: '#(dropId)', productId: '#(productId)', providerPaymentId: 'E2E_STOCK_FAILURE_EVENT_PAID', originalAmount: 10000, discountAmount: 0, finalAmount: 10000 }
    When method post
    Then status 201
    And match response.paymentStatus == 'PAID'

    # Payment Service에서 결제가 PAID로 저장됐는지 조회
    * url baseUrl
    * configure retry = { count: 20, interval: 1000 }
    * retry until karate.filter(response.data.content, function(p){ return p.orderId == orderId && p.paymentStatus == 'PAID' }).length > 0
    Given path '/api/v1/payments/me'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method get
    Then status 200

    # Java Kafka 헬퍼로 Product Service의 재고 차감 실패 이벤트를 직접 발행
    * def KafkaEventPublisher = Java.type('e2e.support.KafkaEventPublisher')
    * def stockFailedEventId = java.util.UUID.randomUUID().toString()
    * eval KafkaEventPublisher.publishStockFailed(kafkaBootstrapServers, stockFailedEventId, orderId, productId, dropId, userId)

    # Payment Service가 stock.failed를 소비해 결제를 CANCELED로 변경할 때까지 조회
    * configure retry = { count: 30, interval: 2000 }
    * retry until karate.filter(response.data.content, function(p){ return p.orderId == orderId && p.paymentStatus == 'CANCELED' }).length > 0
    Given path '/api/v1/payments/me'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method get
    Then status 200
    * def canceledPayments = karate.filter(response.data.content, function(p){ return p.orderId == orderId && p.paymentStatus == 'CANCELED' })
    And assert canceledPayments.length == 1
    And match canceledPayments[0].cancellationCode == 'STOCK_DEDUCT_FAILED'
    And match canceledPayments[0].providerCancellationId == '#string'

    # Notification Service가 주문 취소 이벤트를 소비해 알림을 생성할 때까지 조회
    * configure retry = { count: 30, interval: 2000 }
    * retry until karate.filter(response.data.content, function(n){ return n.referenceId == orderId && n.notificationType == 'ORDER_CANCELLED' }).length > 0
    Given path '/api/v1/notifications'
    And param size = 30
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method get
    Then status 200
    * def canceledNotifications = karate.filter(response.data.content, function(n){ return n.referenceId == orderId && n.notificationType == 'ORDER_CANCELLED' })
    And assert canceledNotifications.length > 0

    * print '재고 차감 실패 이벤트 시작 보상 결과', { eventId: stockFailedEventId, orderId: orderId, paymentStatus: canceledPayments[0].paymentStatus, cancellationCode: canceledPayments[0].cancellationCode, providerCancellationId: canceledPayments[0].providerCancellationId, notificationType: canceledNotifications[0].notificationType }
