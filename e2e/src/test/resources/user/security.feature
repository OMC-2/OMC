Feature: 인증·인가·경로 보안

  # ================================================================
  # 인증·인가·경로 보안 시나리오
  # 실행: bash e2e/run.sh user/security
  #
  # 시나리오 목록:
  #   1. [예외] JWT 없이 보호 엔드포인트 접근 → 401
  #   2. [예외] 변조된 JWT로 접근 → 401
  #   3. [예외] USER 토큰으로 ADMIN 전용 엔드포인트 접근 → 403
  #   4. [예외] ADMIN 토큰으로 USER 전용 엔드포인트 접근 → 403
  #   5. [예외] 유효한 JWT로 /internal/** 경로 직접 호출 → 403 (denyAll)
  #   6. [정상] ADMIN 토큰으로 ADMIN 전용 엔드포인트 접근 → 200
  #   7. [정상] USER 토큰으로 USER 전용 엔드포인트 접근 → 200
  #
  # 주의: Background에서 매 시나리오마다 USER 계정과 ADMIN 계정을 각각 생성하고
  #       로그인하여 두 토큰을 모두 확보한 뒤 시나리오가 시작된다.
  # ================================================================

  Background:
    * url baseUrl
    # USER 계정 전용 유니크 이메일 생성
    * def userEmail = 'e2e-sec-user-' + java.util.UUID.randomUUID() + '@example.com'
    # ADMIN 계정 전용 유니크 이메일 생성
    * def adminEmail = 'e2e-sec-admin-' + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'

    # 사전 준비 1: USER 계정 생성
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword), nickname: 'e2esecuser' }
    When method post
    Then status 201

    # 사전 준비 2: ADMIN 계정 생성 (X-Admin-Secret 헤더 필요)
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: #(adminEmail), password: #(testPassword), nickname: 'e2esecadmin' }
    When method post
    Then status 201

    # 사전 준비 3: USER 로그인 → userAccessToken 확보
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def userAccessToken = response.data.accessToken

    # 사전 준비 4: ADMIN 로그인 → adminAccessToken 확보
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(adminEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def adminAccessToken = response.data.accessToken

  # ----------------------------------------------------------------
  # 시나리오 1: JWT 없이 보호된 엔드포인트에 접근하면 401을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] JWT 없이 보호 엔드포인트 접근 → 401
    Given path '/api/v1/users/me'
    And header X-Gateway-Secret = gatewaySecret
    When method get
    Then status 401

  # ----------------------------------------------------------------
  # 시나리오 2: 변조된 JWT로 접근하면 서명 검증 실패로 401을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 변조된 JWT로 접근 → 401
    Given path '/api/v1/users/me'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer invalid.jwt.token'
    When method get
    Then status 401

  # ----------------------------------------------------------------
  # 시나리오 3: USER 토큰으로 ADMIN 전용 엔드포인트에 접근하면 403을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] USER 토큰으로 ADMIN 전용 엔드포인트 접근 → 403
    Given path '/api/v1/users/test/admin-only'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userAccessToken
    When method get
    Then status 403

  # ----------------------------------------------------------------
  # 시나리오 4: ADMIN 토큰으로 USER 전용 엔드포인트에 접근하면 403을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] ADMIN 토큰으로 USER 전용 엔드포인트 접근 → 403
    Given path '/api/v1/users/test/user-only'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminAccessToken
    When method get
    Then status 403

  # ----------------------------------------------------------------
  # 시나리오 5: 유효한 JWT가 있어도 /internal/** 경로는 Gateway의 denyAll()로 차단된다
  #   denyAll()은 인증 여부와 무관하게 모든 접근을 거부한다 → 403
  #   (JWT 없이 호출하면 denyAll이 아닌 OAuth2 인증 실패로 401이 오므로 검증 불가)
  # ----------------------------------------------------------------
  Scenario: [예외] 유효한 JWT로 /internal/** 경로 직접 호출 → 403 (denyAll)
    Given path '/internal/v1/users/keycloak/some-id'
    And header Authorization = 'Bearer ' + userAccessToken
    When method get
    Then status 403

  # ----------------------------------------------------------------
  # 시나리오 6: ADMIN 토큰으로 ADMIN 전용 엔드포인트에 접근하면 200을 반환한다
  # ----------------------------------------------------------------
  Scenario: [정상] ADMIN 토큰으로 ADMIN 전용 엔드포인트 접근 → 200
    Given path '/api/v1/users/test/admin-only'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminAccessToken
    When method get
    Then status 200
    # Gateway가 주입한 role이 ADMIN인지 검증
    And match response.data.role == 'ADMIN'
    And match response.data.userId == '#uuid'

  # ----------------------------------------------------------------
  # 시나리오 7: USER 토큰으로 USER 전용 엔드포인트에 접근하면 200을 반환한다
  # ----------------------------------------------------------------
  Scenario: [정상] USER 토큰으로 USER 전용 엔드포인트 접근 → 200
    Given path '/api/v1/users/test/user-only'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userAccessToken
    When method get
    Then status 200
    # Gateway가 주입한 role이 USER인지 검증
    And match response.data.role == 'USER'
    And match response.data.userId == '#uuid'
