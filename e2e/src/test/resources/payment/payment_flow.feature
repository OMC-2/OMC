Feature: 결제 승인 조회 취소

  # ================================================================
  # 결제 핵심 흐름 시나리오
  # 실행: bash e2e/run.sh payment/payment_flow
  #
  # 시나리오 목록:
  #   1. [정상] 결제 승인 후 본인 결제 조회
  #   2. [정상] 같은 주문 결제 승인 재요청 시 중복 생성 방지
  #   3. [정상] 본인 결제 취소
  #   4. [예외] 다른 사용자의 결제 취소
  #   5. [예외] 결제 금액 불일치
  #
  # 결제 승인 API는 서비스 간 내부 API라 payment-service로 직접 호출한다.
  # 조회와 취소는 Gateway를 경유하여 JWT 검증 및 사용자 헤더 전달까지 검증한다.
  # ================================================================

  Background:
    * def userEmail = 'e2e-payment-user-' + java.util.UUID.randomUUID() + '@example.com'
    * def otherUserEmail = 'e2e-payment-other-' + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'

    # 결제 사용자 생성 및 로그인
    * url baseUrl
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword), nickname: 'e2epaymentuser' }
    When method post
    Then status 201
    * def userId = response.data.userId

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def userAccessToken = response.data.accessToken

    # 다른 사용자 생성 및 로그인
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(otherUserEmail), password: #(testPassword), nickname: 'e2epaymentother' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(otherUserEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def otherUserAccessToken = response.data.accessToken

    * def orderId = java.util.UUID.randomUUID().toString()
    * def dropId = java.util.UUID.randomUUID().toString()
    * def productId = java.util.UUID.randomUUID().toString()
    * def confirmBody =
    """
    {
      orderID: '#(orderId)',
      dropId: '#(dropId)',
      productId: '#(productId)',
      providerPaymentId: 'E2E 결제 승인 아이디',
      couponID: null,
      originalAmount: 10000,
      discountAmount: 0,
      finalAmount: 10000
    }
    """

  Scenario: [정상] 결제 승인 후 본인 결제 조회
    * url paymentServiceUrl
    Given path '/internal/v1/payments/confirm'
    And header X-User-Id = userId
    And request confirmBody
    When method post
    Then status 201
    And match response.paymentId == '#uuid'
    And match response.orderId == orderId
    And match response.userId == userId
    And match response.paymentStatus == 'PAID'
    * def paymentId = response.paymentId

    * url baseUrl
    Given path '/api/v1/payments/me'
    And header Authorization = 'Bearer ' + userAccessToken
    When method get
    Then status 200
    And match response.data.content[*].paymentId contains paymentId
    And match response.data.content[*].orderId contains orderId

  Scenario: [정상] 같은 주문 결제 승인 재요청 시 중복 생성 방지
    * url paymentServiceUrl
    Given path '/internal/v1/payments/confirm'
    And header X-User-Id = userId
    And request confirmBody
    When method post
    Then status 201
    * def firstPaymentId = response.paymentId

    Given path '/internal/v1/payments/confirm'
    And header X-User-Id = userId
    And request confirmBody
    When method post
    Then status 201
    And match response.paymentId == firstPaymentId
    And match response.paymentStatus == 'PAID'

    * url baseUrl
    Given path '/api/v1/payments/me'
    And header Authorization = 'Bearer ' + userAccessToken
    When method get
    Then status 200
    * def sameOrderPayments = karate.filter(response.data.content, function(payment){ return payment.orderId == orderId })
    And match sameOrderPayments == '#[1]'

  Scenario: [정상] 본인 결제 취소
    * url paymentServiceUrl
    Given path '/internal/v1/payments/confirm'
    And header X-User-Id = userId
    And request confirmBody
    When method post
    Then status 201
    * def paymentId = response.paymentId

    * url baseUrl
    Given path '/api/v1/payments/' + paymentId + '/cancel'
    And header Authorization = 'Bearer ' + userAccessToken
    And request { cancellationCode: 'USER_CANCEL', cancelReason: 'E2E 사용자 결제 취소' }
    When method post
    Then status 200
    And match response.data.paymentId == paymentId
    And match response.data.paymentStatus == 'CANCELED'

    Given path '/api/v1/payments/me'
    And header Authorization = 'Bearer ' + userAccessToken
    When method get
    Then status 200
    * def canceledPayments = karate.filter(response.data.content, function(payment){ return payment.paymentId == paymentId })
    And match canceledPayments == '#[1]'
    And match canceledPayments[0].paymentStatus == 'CANCELED'
    And match canceledPayments[0].cancellationCode == 'USER_CANCEL'

  Scenario: [예외] 다른 사용자의 결제 취소
    * url paymentServiceUrl
    Given path '/internal/v1/payments/confirm'
    And header X-User-Id = userId
    And request confirmBody
    When method post
    Then status 201
    * def paymentId = response.paymentId

    * url baseUrl
    Given path '/api/v1/payments/' + paymentId + '/cancel'
    And header Authorization = 'Bearer ' + otherUserAccessToken
    And request { cancellationCode: 'USER_CANCEL', cancelReason: '다른 사용자 결제 취소 시도' }
    When method post
    Then status 403
    And match response.errorCode == 'COMMON-002'

  Scenario: [예외] 결제 금액 불일치
    * url paymentServiceUrl
    Given path '/internal/v1/payments/confirm'
    And header X-User-Id = userId
    And request
    """
    {
      orderID: '#(orderId)',
      dropId: '#(dropId)',
      productId: '#(productId)',
      providerPaymentId: 'E2E 잘못된 결제 승인 아이디',
      couponID: null,
      originalAmount: 10000,
      discountAmount: 1000,
      finalAmount: 10000
    }
    """
    When method post
    Then status 400
    And match response.errorCode == 'PAYMENT-006'
