Feature: 토큰 갱신

  # ================================================================
  # 토큰 갱신 시나리오
  # 실행: bash e2e/run.sh user/token_refresh
  #
  # 시나리오 목록:
  #   1. [정상] 유효한 refreshToken으로 갱신 → 200, 새 accessToken 발급
  #   2. [예외] 잘못된 refreshToken으로 갱신 → 401
  #
  # 주의: Background에서 매 시나리오마다 새 계정을 생성하고 로그인하여
  #       refreshToken을 확보한 뒤 시나리오가 시작된다.
  # ================================================================

  Background:
    * url baseUrl
    # 토큰 갱신 테스트 전용 유니크 이메일 생성
    * def testEmail = 'e2e-refresh-' + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'
    # 사전 준비 1: 테스트용 계정 생성
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(testEmail), password: #(testPassword), nickname: 'e2erefreshuser' }
    When method post
    Then status 201
    # 사전 준비 2: 로그인하여 refreshToken 확보
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(testEmail), password: #(testPassword) }
    When method post
    Then status 200
    # 이후 시나리오에서 사용할 토큰 저장
    * def refreshToken = response.data.refreshToken

  # ----------------------------------------------------------------
  # 시나리오 1: 유효한 refreshToken으로 갱신하면 새 accessToken을 반환한다
  # ----------------------------------------------------------------
  Scenario: [정상] 유효한 refreshToken으로 갱신 → 200, 새 accessToken 발급
    Given path '/api/v1/users/token/refresh'
    And header X-Gateway-Secret = gatewaySecret
    And request { refreshToken: #(refreshToken) }
    When method post
    Then status 200
    # '#notnull'은 Karate 내장 매처 — 값이 존재하는지 검증
    And match response.data.accessToken == '#notnull'
    And match response.data.refreshToken == '#notnull'
    And match response.data.tokenType == 'Bearer'

  # ----------------------------------------------------------------
  # 시나리오 2: 잘못된 refreshToken으로 갱신 시도 시 401을 반환한다
  #   Keycloak 4xx → user-service가 INVALID_REFRESH_TOKEN(USER-007, 401)으로 변환
  # ----------------------------------------------------------------
  Scenario: [예외] 잘못된 refreshToken으로 갱신 → 401
    Given path '/api/v1/users/token/refresh'
    And header X-Gateway-Secret = gatewaySecret
    And request { refreshToken: 'invalid-refresh-token' }
    When method post
    Then status 401
    And match response.errorCode == 'USER-007'
