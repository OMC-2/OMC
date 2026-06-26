Feature: [시나리오] 토큰 갱신

  # ================================================================
  # 토큰 갱신 흐름 검증
  # 실행: bash e2e/run.sh scenario/06_auth_errors/03_token_refresh
  #
  # 시나리오 목록:
  #   1. [정상] 유효한 refreshToken으로 갱신 → 새 accessToken으로 API 호출 성공
  #   2. [예외] 잘못된 refreshToken으로 갱신 → 401, USER-007
  #
  # 검증 흐름 (시나리오 1):
  #   회원가입 → 로그인 → 토큰 갱신 → 새 accessToken으로 프로필 조회 성공
  # ================================================================

  Background:
    * url baseUrl
    * def testEmail = 'e2e-token-refresh-' + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'

    # 사전 준비 1: 계정 생성
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(testEmail), password: #(testPassword), nickname: 'e2etokenrefresh' }
    When method post
    Then status 201

    # 사전 준비 2: 로그인 → refreshToken 확보
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(testEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def refreshToken = response.data.refreshToken

  # ----------------------------------------------------------------
  # 시나리오 1: 유효한 refreshToken으로 갱신 후 새 토큰으로 API 호출 성공
  # ----------------------------------------------------------------
  Scenario: [정상] 유효한 refreshToken으로 갱신 후 검증
    # STEP 1: 토큰 갱신 → 새 accessToken 발급
    Given path '/api/v1/users/token/refresh'
    And header X-Gateway-Secret = gatewaySecret
    And request { refreshToken: #(refreshToken) }
    When method post
    Then status 200
    And match response.data.accessToken == '#notnull'
    And match response.data.refreshToken == '#notnull'
    And match response.data.tokenType == 'Bearer'
    * def newAccessToken = response.data.accessToken

    # STEP 2: 새 accessToken으로 프로필 조회 → 실제로 유효한 토큰임을 검증
    Given path '/api/v1/users/me'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + newAccessToken
    When method get
    Then status 200
    And match response.data.email == testEmail

  # ----------------------------------------------------------------
  # 시나리오 2: 잘못된 refreshToken으로 갱신 시도 시 401을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 잘못된 refreshToken으로 갱신 → 401, USER-007
    Given path '/api/v1/users/token/refresh'
    And header X-Gateway-Secret = gatewaySecret
    And request { refreshToken: 'invalid-refresh-token' }
    When method post
    Then status 401
    And match response.errorCode == 'USER-007'
