Feature: 쿠폰 생성

  # ================================================================
  # 쿠폰 생성 시나리오
  # 실행: bash e2e/run.sh coupon/coupon_create
  #
  # 시나리오 목록:
  #   1. [정상] ADMIN 쿠폰 생성 성공 → 201, couponId 반환
  #   2. [예외] 필수 필드 누락 (name 없음) → 400
  #   3. [예외] USER 토큰으로 쿠폰 생성 → 403
  #   4. [예외] 토큰 없이 쿠폰 생성 → 401
  #
  # 주의: Background에서 매 시나리오마다 ADMIN 계정과 USER 계정을 각각 생성하고
  #       로그인하여 두 토큰을 모두 확보한 뒤 시나리오가 시작된다.
  # ================================================================

  Background:
    * url baseUrl
    * def adminEmail = 'e2e-coupon-admin-' + java.util.UUID.randomUUID() + '@example.com'
    * def userEmail  = 'e2e-coupon-user-'  + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'

    # 사전 준비 1: ADMIN 계정 생성
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: #(adminEmail), password: #(testPassword), nickname: 'e2ecouponadmin' }
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
    And request { email: #(userEmail), password: #(testPassword), nickname: 'e2ecouponuser' }
    When method post
    Then status 201

    # 사전 준비 4: USER 로그인 → userAccessToken 확보
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def userAccessToken = response.data.accessToken

    # 쿠폰 생성 요청 바디
    * def couponBody = { name: 'E2E 테스트 쿠폰', discountType: 'AMOUNT', discountValue: 1000, totalQuantity: 100, startedAt: '2020-01-01T00:00:00', expiredAt: '2099-12-31T23:59:59' }

  # ----------------------------------------------------------------
  # 시나리오 1: ADMIN 계정으로 쿠폰을 생성하면 201과 함께 couponId를 반환한다
  # ----------------------------------------------------------------
  Scenario: [정상] ADMIN 쿠폰 생성 성공 → 201, couponId 반환
    Given path '/api/v1/coupons'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminAccessToken
    And request couponBody
    When method post
    Then status 201
    And match response.data.couponId == '#uuid'
    And match response.data.name == 'E2E 테스트 쿠폰'
    And match response.data.discountType == 'AMOUNT'
    And match response.data.totalQuantity == 100

  # ----------------------------------------------------------------
  # 시나리오 2: 필수 필드(name)가 없으면 400과 COMMON-001을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 필수 필드 누락 → 400
    Given path '/api/v1/coupons'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminAccessToken
    And request { discountType: 'AMOUNT', discountValue: 1000, totalQuantity: 100, startedAt: '2020-01-01T00:00:00', expiredAt: '2099-12-31T23:59:59' }
    When method post
    Then status 400
    And match response.errorCode == 'COMMON-001'

  # ----------------------------------------------------------------
  # 시나리오 3: USER 토큰으로 쿠폰 생성 시도 시 403을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] USER 토큰으로 쿠폰 생성 → 403
    Given path '/api/v1/coupons'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userAccessToken
    And request couponBody
    When method post
    Then status 403

  # ----------------------------------------------------------------
  # 시나리오 4: 토큰 없이 쿠폰 생성 시도 시 401을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 토큰 없이 쿠폰 생성 → 401
    Given path '/api/v1/coupons'
    And header X-Gateway-Secret = gatewaySecret
    And request couponBody
    When method post
    Then status 401
