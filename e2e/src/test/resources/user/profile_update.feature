Feature: 프로필 수정 및 회원 탈퇴

  # ================================================================
  # 프로필 수정 및 회원 탈퇴 시나리오
  # 실행: bash e2e/run.sh user/profile_update
  #
  # 시나리오 목록:
  #   1. [정상] 닉네임 수정 → 200, 변경된 닉네임 반환
  #   2. [정상] 회원 탈퇴 → 200
  #   3. [예외] 토큰 없이 수정 → 401
  #
  # 주의: Background에서 매 시나리오마다 계정 생성 → 로그인을 순서대로
  #       실행하여 accessToken을 확보한 뒤 시나리오가 시작된다.
  # ================================================================

  Background:
    * url baseUrl
    # 프로필 수정 테스트 전용 유니크 이메일 생성
    * def testEmail = 'e2e-update-' + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'
    # 사전 준비 1: 테스트용 계정 생성
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(testEmail), password: #(testPassword), nickname: 'e2eupdateuser' }
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
  # 시나리오 1: 유효한 토큰으로 닉네임을 수정하면 변경된 값을 반환한다
  # ----------------------------------------------------------------
  Scenario: [정상] 닉네임 수정 → 200, 변경된 닉네임 반환
    Given path '/api/v1/users/me'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + accessToken
    And request { nickname: 'updated-nickname' }
    When method patch
    Then status 200
    # 요청한 닉네임으로 정상 변경됐는지 검증
    And match response.data.nickname == 'updated-nickname'
    And match response.data.userId == '#uuid'

  # ----------------------------------------------------------------
  # 시나리오 2: 유효한 토큰으로 탈퇴하면 200을 반환한다
  # ----------------------------------------------------------------
  Scenario: [정상] 회원 탈퇴 → 200
    Given path '/api/v1/users/me'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + accessToken
    When method delete
    Then status 200

  # ----------------------------------------------------------------
  # 시나리오 3: 토큰 없이 수정 요청 시 인증 실패로 401을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 토큰 없이 수정 → 401
    Given path '/api/v1/users/me'
    And header X-Gateway-Secret = gatewaySecret
    And request { nickname: 'updated-nickname' }
    When method patch
    Then status 401
