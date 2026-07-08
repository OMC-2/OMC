Feature: 쿠폰 인증·인가 보안

  # ================================================================
  # 쿠폰 인증·인가 보안 시나리오
  # 실행: bash e2e/run.sh coupon/coupon_security
  #
  # 시나리오 목록:
  #   1. [예외] 토큰 없이 쿠폰 목록 조회 → 401
  #   2. [예외] USER 토큰으로 ADMIN 전용 쿠폰 생성 → 403
  #   3. [예외] ADMIN 토큰으로 USER 전용 쿠폰 발급 → 403
  #   4. [예외] 변조된 JWT로 발급 시도 → 401
  #   5. [정상] ADMIN 토큰으로 쿠폰 목록 조회 → 200
  #   6. [정상] USER 토큰으로 쿠폰 발급 → 201
  #
  # 주의: Background에서 매 시나리오마다 USER·ADMIN 계정을 각각 생성하고
  #       로그인하여 두 토큰을 모두 확보한 뒤, ADMIN으로 쿠폰 1개를 생성한다.
  # ================================================================

  Background:
    * url baseUrl
    * def adminEmail = 'e2e-sec-coupon-admin-' + java.util.UUID.randomUUID() + '@example.com'
    * def userEmail  = 'e2e-sec-coupon-user-'  + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'

    # 사전 준비 1: ADMIN 계정 생성
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: #(adminEmail), password: #(testPassword), nickname: 'e2eseccouponadmin' }
    When method post
    Then status 201

    # 사전 준비 2: ADMIN 로그인 → adminAccessToken 확보
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(adminEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def adminAccessToken = response.data.accessToken

    # 사전 준비 3: USER 계정 생성
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword), nickname: 'e2eseccouponuser' }
    When method post
    Then status 201

    # 사전 준비 4: USER 로그인 → userAccessToken 확보
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def userAccessToken = response.data.accessToken

    # 사전 준비 5: ADMIN으로 테스트용 쿠폰 생성 → couponId 저장
    Given path '/api/v1/coupons'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminAccessToken
    And request { name: '보안 테스트 쿠폰', discountType: 'AMOUNT', discountValue: 500, totalQuantity: 100, startedAt: '2020-01-01T00:00:00', expiredAt: '2099-12-31T23:59:59' }
    When method post
    Then status 201
    * def couponId = response.data.couponId

  # ----------------------------------------------------------------
  # 시나리오 1: 토큰 없이 ADMIN 전용 쿠폰 목록 조회 시 401을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 토큰 없이 쿠폰 목록 조회 → 401
    Given path '/api/v1/coupons'
    And header X-Gateway-Secret = gatewaySecret
    When method get
    Then status 401

  # ----------------------------------------------------------------
  # 시나리오 2: USER 토큰으로 ADMIN 전용 쿠폰 생성 시도 시 403을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] USER 토큰으로 ADMIN 전용 쿠폰 생성 → 403
    Given path '/api/v1/coupons'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userAccessToken
    And request { name: '침범 쿠폰', discountType: 'AMOUNT', discountValue: 1000, totalQuantity: 10, startedAt: '2020-01-01T00:00:00', expiredAt: '2099-12-31T23:59:59' }
    When method post
    Then status 403

  # ----------------------------------------------------------------
  # 시나리오 3: ADMIN 토큰으로 USER 전용 쿠폰 발급 시도 시 403을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] ADMIN 토큰으로 USER 전용 쿠폰 발급 → 403
    Given path '/api/v1/coupons/' + couponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminAccessToken
    When method post
    Then status 403

  # ----------------------------------------------------------------
  # 시나리오 4: 변조된 JWT로 쿠폰 발급 시도 시 401을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 변조된 JWT로 발급 시도 → 401
    Given path '/api/v1/coupons/' + couponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer invalid.jwt.token'
    When method post
    Then status 401

  # ----------------------------------------------------------------
  # 시나리오 5: ADMIN 토큰으로 ADMIN 전용 쿠폰 목록 조회 시 200을 반환한다
  # ----------------------------------------------------------------
  Scenario: [정상] ADMIN 토큰으로 쿠폰 목록 조회 → 200
    Given path '/api/v1/coupons'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminAccessToken
    When method get
    Then status 200
    And match response.data.content == '#array'

  # ----------------------------------------------------------------
  # 시나리오 6: USER 토큰으로 쿠폰 발급 시 201을 반환한다
  # ----------------------------------------------------------------
  Scenario: [정상] USER 토큰으로 쿠폰 발급 → 202
    Given path '/api/v1/coupons/' + couponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userAccessToken
    When method post
    Then status 202
    And match response.message == '쿠폰이 발급되었습니다.'
