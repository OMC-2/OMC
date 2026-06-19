Feature: 로그인

  # ================================================================
  # 로그인 시나리오
  # 실행: bash e2e/run.sh user/login
  #
  # 시나리오 목록:
  #   1. [정상] 로그인 성공 → 200, accessToken 발급
  #   2. [예외] 잘못된 비밀번호 → 401
  #
  # 주의: Background에서 매 시나리오마다 새 계정을 생성하고 시작한다.
  #       로그인 테스트를 위해 signup → login 순서로 실행된다.
  # ================================================================

  Background:
    * url baseUrl
    # 로그인 테스트 전용 유니크 이메일 생성
    * def testEmail = 'e2e-login-' + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'
    # 사전 준비: 로그인할 계정을 먼저 생성한다
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(testEmail), password: #(testPassword), nickname: 'e2eloginuser' }
    When method post
    Then status 201

  # ----------------------------------------------------------------
  # 시나리오 1: 올바른 이메일/비밀번호로 로그인하면 200과 토큰을 반환한다
  # ----------------------------------------------------------------
  Scenario: [정상] 로그인 성공 → 200, accessToken 발급
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(testEmail), password: #(testPassword) }
    When method post
    Then status 200
    # '#notnull'은 Karate 내장 매처 — 값이 존재하는지 검증
    And match response.data.accessToken == '#notnull'
    And match response.data.tokenType == '#notnull'
    # 이후 시나리오(프로필 조회 등)에서 사용할 수 있도록 토큰 저장
    * def accessToken = response.data.accessToken

  # ----------------------------------------------------------------
  # 시나리오 2: 잘못된 비밀번호로 로그인 시 401을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 잘못된 비밀번호 → 401
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(testEmail), password: 'wrongpassword' }
    When method post
    Then status 401
