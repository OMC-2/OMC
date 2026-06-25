Feature: [시나리오] 재고 차감 실패 결제 승인 취소 보상

  # ================================================================
  # 재고 차감 실패 이후 결제 승인 취소 보상 시나리오
  # 실행: bash e2e/run.sh scenario/04_payment_saga/02_stock_failure
  #
  # 검증 흐름:
  #   Product: 재고 1개 상품 생성
  #   Drop: 판매 수량 2개 드롭 생성 → 사용자 두 명 구매
  #   Payment: 결제 승인
  #   Product: 두 번째 재고 차감 실패 → stock.failed 발행
  #   Payment: stock.failed 소비 → 결제 승인 취소
  #
  # 현재 제약:
  #   Drop이 Product의 실제 재고로 Redis를 워밍하므로 두 번째 구매가 Drop에서 차단될 수 있음
  # ================================================================

  Background:
    * url baseUrl
    * def fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    * def uniqueId = java.util.UUID.randomUUID().toString()
    * def testPassword = 'password123'
    * def adminEmail = 'e2e-stock-failure-admin-' + uniqueId + '@example.com'
    * def user1Email = 'e2e-stock-failure-user1-' + uniqueId + '@example.com'
    * def user2Email = 'e2e-stock-failure-user2-' + uniqueId + '@example.com'

    # User Service에 테스트용 관리자 계정을 생성
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: '#(adminEmail)', password: '#(testPassword)', nickname: 'stockfailureadmin' }
    When method post
    Then status 201

    # User Service에서 관리자 계정으로 로그인해 JWT를 발급
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: '#(adminEmail)', password: '#(testPassword)' }
    When method post
    Then status 200
    * def adminToken = response.data.accessToken

    # User Service에 첫 번째 구매자 계정을 생성
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: '#(user1Email)', password: '#(testPassword)', nickname: 'stockfailureuser1' }
    When method post
    Then status 201

    # User Service에서 첫 번째 구매자로 로그인해 JWT를 발급
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: '#(user1Email)', password: '#(testPassword)' }
    When method post
    Then status 200
    * def user1Token = response.data.accessToken

    # User Service에 두 번째 구매자 계정을 생성
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: '#(user2Email)', password: '#(testPassword)', nickname: 'stockfailureuser2' }
    When method post
    Then status 201

    # User Service에서 두 번째 구매자로 로그인해 JWT를 발급
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: '#(user2Email)', password: '#(testPassword)' }
    When method post
    Then status 200
    * def user2Token = response.data.accessToken

  Scenario: 재고 차감 실패 → 결제 승인 취소 보상 처리
    # Product Service에 실제 재고가 1개인 테스트 상품을 생성
    Given path '/api/v1/admin/products'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '재고 실패 테스트 상품', description: 'SAGA 진단용', price: 10000, brand: 'TestBrand', category: 'Sneakers', imageUrl: 'http://img.test/stock-failure.jpg', initialQuantity: 1 }
    When method post
    Then status 201
    * def productId = response.data.productId

    * def startAt = java.time.LocalDateTime.now().plusSeconds(5).format(fmt)
    * def endAt = java.time.LocalDateTime.now().plusDays(1).format(fmt)
    # Drop Service에 판매 수량을 2개로 지정한 테스트 드롭을 생성
    Given path '/api/v1/admin/drops'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { productId: '#(productId)', startAt: '#(startAt)', endAt: '#(endAt)', totalQty: 2, holdTtlSec: 60 }
    When method post
    Then status 201
    * def dropId = response.data.dropId

    * configure retry = { count: 20, interval: 1000 }
    * retry until response.data.status == 'OPEN'
    # Drop Service에서 드롭이 구매 가능한 OPEN 상태가 될 때까지 조회
    Given path '/api/v1/drops/' + dropId
    And header X-Gateway-Secret = gatewaySecret
    When method get
    Then status 200

    # Drop Service에서 첫 번째 구매자가 상품을 선점
    Given path '/api/v1/drops/' + dropId + '/purchase'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user1Token
    When method post
    Then status 202
    * def firstOrderId = response.data.orderId

    # Drop Service에서 두 번째 구매자가 상품을 선점해 재고 차감 실패를 유도
    Given path '/api/v1/drops/' + dropId + '/purchase'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user2Token
    When method post
    Then status 202
    * def secondOrderId = response.data.orderId

    * eval java.lang.Thread.sleep(30000)
    # Payment Service에서 첫 번째 구매자의 결제 상태를 조회
    Given path '/api/v1/payments/me'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user1Token
    When method get
    Then status 200
    * def firstPayments = karate.filter(response.data.content, function(p){ return p.orderId == firstOrderId })

    # Payment Service에서 두 번째 구매자의 결제가 취소됐는지 조회
    Given path '/api/v1/payments/me'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user2Token
    When method get
    Then status 200
    * def secondPayments = karate.filter(response.data.content, function(p){ return p.orderId == secondOrderId })
    * def allPaymentStatuses = karate.map(firstPayments.concat(secondPayments), function(p){ return p.paymentStatus })
    * def cancellationIds = karate.map(karate.filter(firstPayments.concat(secondPayments), function(p){ return p.paymentStatus == 'CANCELED' }), function(p){ return p.providerCancellationId })

    * print '재고 실패 진단', { firstOrderId: firstOrderId, secondOrderId: secondOrderId, paymentStatuses: allPaymentStatuses, cancellationIds: cancellationIds }
    And match allPaymentStatuses contains 'PAID'
    And match allPaymentStatuses contains 'CANCELED'
    And match cancellationIds[0] == '#string'
