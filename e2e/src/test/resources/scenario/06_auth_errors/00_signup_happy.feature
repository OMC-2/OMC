Feature: [시나리오] 회원가입 해피패쓰

  # ================================================================
  # 권한 오류 시나리오 사전 흐름 — 정상 가입이 되는 상황을 먼저 시연
  # 실행: bash e2e/run.sh scenario/06_auth_errors/00_signup_happy
  #
  # 시나리오 목록:
  #   1. [정상] 일반 유저 회원가입 성공 → 201, role=USER
  #   2. [정상] 가입 후 로그인 성공 + 프로필 조회 → 200
  #   3. [정상] 관리자 회원가입 성공 → 201, role=ADMIN
  #
  # 주의: 각 시나리오는 독립 실행 — Background에서 새 이메일을 생성하며
  #       시나리오 간 변수 공유 없음.
  # ================================================================

  Background:
    * url baseUrl
    * def uniqueEmail = 'e2e-signup-happy-' + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'

  # ----------------------------------------------------------------
  # 시나리오 1: 올바른 정보로 가입하면 201과 role=USER를 반환한다
  # ----------------------------------------------------------------
  Scenario: [정상] 일반 유저 회원가입 성공 → 201, role=USER
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(uniqueEmail), password: #(testPassword), nickname: 'e2esignuphappy' }
    When method post
    Then status 201
    And match response.data.role == 'USER'
    And match response.data.email == uniqueEmail
    And match response.data.userId == '#uuid'

  # ----------------------------------------------------------------
  # 시나리오 2: 가입 후 로그인 → accessToken 발급 → 프로필 조회 성공
  # ----------------------------------------------------------------
  Scenario: [정상] 가입 후 로그인 성공 + 프로필 조회
    # STEP 1: 회원가입
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(uniqueEmail), password: #(testPassword), nickname: 'e2esignuphappy' }
    When method post
    Then status 201

    # STEP 2: 로그인 → accessToken / refreshToken 확보
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(uniqueEmail), password: #(testPassword) }
    When method post
    Then status 200
    And match response.data.accessToken == '#notnull'
    And match response.data.refreshToken == '#notnull'
    * def accessToken = response.data.accessToken

    # STEP 3: 발급된 accessToken으로 프로필 조회 → 가입한 이메일 일치 확인
    Given path '/api/v1/users/me'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + accessToken
    When method get
    Then status 200
    And match response.data.email == uniqueEmail
    And match response.data.role == 'USER'

  # ----------------------------------------------------------------
  # 시나리오 3: X-Admin-Secret 헤더 포함 시 ADMIN 권한으로 가입된다
  # ----------------------------------------------------------------
  Scenario: [정상] 관리자 회원가입 성공 → 201, role=ADMIN
    * def adminEmail = 'e2e-signup-happy-admin-' + java.util.UUID.randomUUID() + '@example.com'
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: #(adminEmail), password: #(testPassword), nickname: 'e2esignupadmin' }
    When method post
    Then status 201
    And match response.data.role == 'ADMIN'
    And match response.data.email == adminEmail
    And match response.data.userId == '#uuid'
