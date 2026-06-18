Feature: 프로필 조회

  # ================================================================
  # 프로필 조회 시나리오
  # 실행: bash e2e/run.sh user/profile
  #
  # 시나리오 목록:
  #   1. [정상] 내 프로필 조회 → 200, 이메일 일치
  #   2. [예외] 토큰 없이 조회 → 401
  #
  # 주의: Background에서 매 시나리오마다 계정 생성 → 로그인을 순서대로
  #       실행하여 accessToken을 확보한 뒤 시나리오가 시작된다.
  # ================================================================

  Background:
    * url baseUrl
    # 프로필 테스트 전용 유니크 이메일 생성
    * def testEmail = 'e2e-profile-' + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'
    # 사전 준비 1: 테스트용 계정 생성
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(testEmail), password: #(testPassword), nickname: 'e2eprofileuser' }
    When method post
    Then status 201
    # 사전 준비 2: 로그인하여 accessToken 확보
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(testEmail), password: #(testPassword) }
    When method post
    Then status 200
    # 이후 시나리오에서 Authorization 헤더에 사용할 토큰 저장
    * def accessToken = response.data.accessToken

  # ----------------------------------------------------------------
  # 시나리오 1: 유효한 토큰으로 /me 호출 시 내 프로필을 반환한다
  # ----------------------------------------------------------------
  Scenario: [정상] 내 프로필 조회 → 200, 이메일 일치
    Given path '/api/v1/users/me'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + accessToken
    When method get
    Then status 200
    # 가입 시 사용한 이메일과 일치하는지 검증
    And match response.data.email == testEmail
    And match response.data.nickname == '#notnull'

  # ----------------------------------------------------------------
  # 시나리오 2: 토큰 없이 /me 호출 시 인증 실패로 401을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 토큰 없이 조회 → 401
    Given path '/api/v1/users/me'
    And header X-Gateway-Secret = gatewaySecret
    When method get
    Then status 401
