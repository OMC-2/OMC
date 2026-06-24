Feature: 결제 인증 인가 보안

  # ================================================================
  # 결제 보안 시나리오
  # 실행: bash e2e/run.sh payment/payment_security
  #
  # 시나리오 목록:
  #   1. [예외] 토큰 없이 내 결제 조회
  #   2. [예외] 변조된 토큰으로 내 결제 조회
  #   3. [정상] USER 토큰으로 내 결제 조회
  # ================================================================

  Background:
    * url baseUrl
    * def userEmail = 'e2e-payment-security-' + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'

    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword), nickname: 'e2epaymentsecurity' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def userAccessToken = response.data.accessToken

  Scenario: [예외] 토큰 없이 내 결제 조회
    Given path '/api/v1/payments/me'
    When method get
    Then status 401

  Scenario: [예외] 변조된 토큰으로 내 결제 조회
    Given path '/api/v1/payments/me'
    And header Authorization = 'Bearer invalid.jwt.token'
    When method get
    Then status 401

  Scenario: [정상] USER 토큰으로 내 결제 조회
    Given path '/api/v1/payments/me'
    And header Authorization = 'Bearer ' + userAccessToken
    When method get
    Then status 200
    And match response.data.content == '#array'
