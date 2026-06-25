Feature: [시나리오] 외부 결제 오류 멱등성 검증

  # ================================================================
  # 외부 PG 연결 오류 이후 동일 주문 재요청 멱등성 시나리오
  # 실행: bash e2e/run.sh scenario/04_payment_saga/04_idempotency
  #
  # 검증 흐름:
  #   Payment: PG 연결 오류 → UNKNOWN 결제 저장
  #   Payment: 동일 orderId 재요청 → 기존 결제 반환
  #
  # 검증 포인트:
  #   첫 요청은 503, 재요청은 201
  #   동일 orderId의 Payment는 한 건이며 UNKNOWN 상태 유지
  # ================================================================

  Background:
    * url baseUrl
    * def uniqueId = java.util.UUID.randomUUID().toString()
    * def testPassword = 'password123'
    * def userEmail = 'e2e-payment-idempotency-user-' + uniqueId + '@example.com'

    # User Service에 결제 멱등성 테스트용 구매자 계정을 생성
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: '#(userEmail)', password: '#(testPassword)', nickname: 'paymentidempotencyuser' }
    When method post
    Then status 201
    * def userId = response.data.userId

    # User Service에서 구매자로 로그인해 JWT를 발급
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: '#(userEmail)', password: '#(testPassword)' }
    When method post
    Then status 200
    * def userToken = response.data.accessToken

  Scenario: 외부 PG 연결 오류 → 재요청 시 결제 단건 및 UNKNOWN 유지
    * def orderId = java.util.UUID.randomUUID().toString()
    * def dropId = java.util.UUID.randomUUID().toString()
    * def productId = java.util.UUID.randomUUID().toString()
    * url paymentServiceUrl

    # Payment Service 내부 API를 호출해 PG 연결 오류와 UNKNOWN 상태를 발생
    Given path '/internal/v1/payments/confirm'
    And header X-User-Id = userId
    And request { orderID: '#(orderId)', dropId: '#(dropId)', productId: '#(productId)', providerPaymentId: 'E2E_GATEWAY_CONNECTION_ERROR', originalAmount: 10000, discountAmount: 0, finalAmount: 10000 }
    When method post
    * def firstAttemptStatus = responseStatus

    # Payment Service에 동일 주문을 재요청해 중복 결제 생성 여부를 확인
    Given path '/internal/v1/payments/confirm'
    And header X-User-Id = userId
    And request { orderID: '#(orderId)', dropId: '#(dropId)', productId: '#(productId)', providerPaymentId: 'E2E_GATEWAY_CONNECTION_ERROR', originalAmount: 10000, discountAmount: 0, finalAmount: 10000 }
    When method post
    * def secondAttemptStatus = responseStatus

    * url baseUrl
    # Payment Service에서 동일 주문의 결제가 한 건의 UNKNOWN 상태로 유지되는지 조회
    Given path '/api/v1/payments/me'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method get
    Then status 200
    * def sameOrderPayments = karate.filter(response.data.content, function(p){ return p.orderId == orderId })
    * def sameOrderStatuses = karate.map(sameOrderPayments, function(p){ return p.paymentStatus })

    * print 'PG 연결 오류 멱등성 진단', { firstAttemptStatus: firstAttemptStatus, secondAttemptStatus: secondAttemptStatus, paymentCount: sameOrderPayments.length, paymentStatuses: sameOrderStatuses }
    And match firstAttemptStatus == 503
    And match secondAttemptStatus == 201
    And match sameOrderPayments == '#[1]'
    And match sameOrderStatuses == ['UNKNOWN']
