Feature: 알림 인증·인가 보안

  # ================================================================
  # 알림 인증·인가·경로 보안 시나리오
  # 실행: bash e2e/run.sh notification/notification_security
  #
  # 시나리오 목록:
  #   1. [예외] 토큰 없이 알림 목록 조회 → 401
  #   2. [예외] 토큰 없이 읽음 처리 → 401
  #   3. [예외] 변조된 JWT로 알림 목록 조회 → 401
  #   4. [정상] USER 토큰으로 알림 목록 조회 → 200
  #
  # 주의: Background에서 매 시나리오마다 USER·ADMIN 계정을 각각 생성하고
  #       로그인하여 두 토큰을 모두 확보한 뒤 시나리오가 시작된다.
  #       알림 데이터 없이 보안 레이어만 검증한다.
  # ================================================================

  Background:
    * url baseUrl
    * def userEmail  = 'e2e-nsec-user-'  + java.util.UUID.randomUUID() + '@example.com'
    * def adminEmail = 'e2e-nsec-admin-' + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'

    # 사전 준비 1: USER 계정 생성
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword), nickname: 'e2ensecuser' }
    When method post
    Then status 201

    # 사전 준비 2: ADMIN 계정 생성
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: #(adminEmail), password: #(testPassword), nickname: 'e2ensecadmin' }
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
  # 시나리오 1: 토큰 없이 알림 목록 조회 시 401을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 토큰 없이 알림 목록 조회 → 401
    Given path '/api/v1/notifications'
    And header X-Gateway-Secret = gatewaySecret
    When method get
    Then status 401

  # ----------------------------------------------------------------
  # 시나리오 2: 토큰 없이 읽음 처리 시 401을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 토큰 없이 읽음 처리 → 401
    * def randomId = java.util.UUID.randomUUID().toString()
    Given path '/api/v1/notifications/' + randomId + '/read'
    And header X-Gateway-Secret = gatewaySecret
    When method patch
    Then status 401

  # ----------------------------------------------------------------
  # 시나리오 3: 변조된 JWT로 알림 목록 조회 시 서명 검증 실패로 401을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 변조된 JWT로 알림 목록 조회 → 401
    Given path '/api/v1/notifications'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer invalid.jwt.token'
    When method get
    Then status 401

  # ----------------------------------------------------------------
  # 시나리오 4: USER 토큰으로 알림 목록 조회 시 200을 반환한다
  # ----------------------------------------------------------------
  Scenario: [정상] USER 토큰으로 알림 목록 조회 → 200
    Given path '/api/v1/notifications'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userAccessToken
    When method get
    Then status 200
    And match response.data.content == '#array'
