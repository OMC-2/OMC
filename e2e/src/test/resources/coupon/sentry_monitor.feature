Feature: Sentry 에러 모니터링 검증

  # ================================================================
  # Sentry 캡처 대상 에러 시나리오
  # 실행: bash e2e/run.sh coupon/sentry_monitor
  #
  # 시나리오 목록:
  #   1. [보안] USER 토큰으로 ADMIN API 호출 → 403 (AccessDeniedException → Sentry)
  #   2. [보안] 게이트웨이 우회 직접 호출 → 400 (MissingRequestHeaderException → Sentry)
  #
  # 목적: 위 에러가 발생했을 때 Sentry 대시보드에 이슈가 등록되는지 확인
  # ================================================================

  Background:
    * url baseUrl
    * def userEmail = 'e2e-sentry-user-' + java.util.UUID.randomUUID() + '@example.com'
    * def adminEmail = 'e2e-sentry-admin-' + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'

    # 사전 준비 1: USER 계정 생성
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword), nickname: 'sentrymonuser' }
    When method post
    Then status 201

    # 사전 준비 2: USER 로그인 → userAccessToken 확보
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def userAccessToken = response.data.accessToken

    # 사전 준비 3: ADMIN 계정 생성
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: #(adminEmail), password: #(testPassword), nickname: 'sentrymonadmin' }
    When method post
    Then status 201

    # 사전 준비 4: ADMIN 로그인 → adminAccessToken 확보
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(adminEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def adminAccessToken = response.data.accessToken

  # ----------------------------------------------------------------
  # 시나리오 1: USER 토큰으로 ADMIN 전용 쿠폰 생성 → 403
  #   → AccessDeniedException 발생 → Sentry 캡처
  # ----------------------------------------------------------------
  Scenario: [보안] USER 토큰으로 ADMIN API 호출 → 403, Sentry 캡처
    Given path '/api/v1/coupons'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userAccessToken
    And request { name: '권한없는쿠폰', discountType: 'AMOUNT', discountValue: 1000, totalQuantity: 10, startedAt: '2020-01-01T00:00:00', expiredAt: '2099-12-31T23:59:59' }
    When method post
    Then status 403
    And match response.errorCode == 'COMMON-002'

  # ----------------------------------------------------------------
  # 시나리오 2: 게이트웨이 우회 → X-Gateway-Secret 없이 직접 호출 → 403
  #   → GatewayHeaderAuthFilter에서 감지 → Sentry WARNING 이벤트 캡처
  # ----------------------------------------------------------------
  Scenario: [보안] 게이트웨이 우회 직접 호출 → 403, Sentry 캡처
    * url couponServiceUrl
    Given path '/api/v1/coupons'
    And header Authorization = 'Bearer ' + adminAccessToken
    And request { name: '우회쿠폰', discountType: 'AMOUNT', discountValue: 1000, totalQuantity: 10, startedAt: '2020-01-01T00:00:00', expiredAt: '2099-12-31T23:59:59' }
    When method post
    Then status 403
    And match response.errorCode == 'COMMON-002'
