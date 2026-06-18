Feature: 회원가입

  # ================================================================
  # 회원가입 시나리오
  # 실행: bash e2e/run.sh user/signup
  #
  # 시나리오 목록:
  #   1. [정상] 일반 회원가입 성공 → 201
  #   2. [예외] 중복 이메일 재가입 → 409
  #   3. [정상] 어드민 가입 성공 → 201
  #
  # 주의: 시나리오마다 UUID 기반 유니크 이메일을 생성하므로
  #       매 실행마다 DB에 새 계정이 생성된다.
  # ================================================================

  Background:
    * url baseUrl
    # 매 시나리오 실행 전 유니크한 이메일 생성 (중복 충돌 방지)
    * def uniqueEmail = 'e2e-' + java.util.UUID.randomUUID() + '@example.com'

  # ----------------------------------------------------------------
  # 시나리오 1: 올바른 값으로 가입하면 201과 함께 userId, role을 반환한다
  # ----------------------------------------------------------------
  Scenario: [정상] 회원가입 성공 → 201
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(uniqueEmail), password: 'password123', nickname: 'e2euser' }
    When method post
    Then status 201
    And match response.data.role == 'USER'
    And match response.data.email == uniqueEmail
    # '#uuid'는 Karate 내장 매처 — UUID 형식인지 검증
    And match response.data.userId == '#uuid'
    # 이후 시나리오에서 사용할 수 있도록 userId 저장
    * def signupUserId = response.data.userId

  # ----------------------------------------------------------------
  # 시나리오 2: 이미 가입된 이메일로 재가입 시 409와 errorCode를 반환한다
  #   1단계에서 계정을 생성한 뒤,
  #   2단계에서 동일 이메일로 다시 가입 시도한다
  # ----------------------------------------------------------------
  Scenario: [예외] 중복 이메일 재가입 → 409
    # 1단계: 정상 가입으로 계정 생성
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(uniqueEmail), password: 'password123', nickname: 'e2euser' }
    When method post
    Then status 201
    # 2단계: 동일 이메일로 재가입 시도 → 409 응답이 와야 테스트 통과
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(uniqueEmail), password: 'password123', nickname: 'e2euser' }
    When method post
    Then status 409
    And match response.errorCode == 'USER-002'

  # ----------------------------------------------------------------
  # 시나리오 3: X-Admin-Secret 헤더를 포함하면 ADMIN 권한으로 가입된다
  # ----------------------------------------------------------------
  Scenario: [정상] 어드민 가입 성공 → 201
    # 어드민 전용 유니크 이메일 (일반 uniqueEmail과 별도 생성)
    * def adminEmail = 'e2e-admin-' + java.util.UUID.randomUUID() + '@example.com'
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: #(adminEmail), password: 'password123', nickname: 'e2eadmin' }
    When method post
    Then status 201
    And match response.data.role == 'ADMIN'
