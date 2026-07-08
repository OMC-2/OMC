Feature: 내 쿠폰 조회

  # ================================================================
  # 내 쿠폰 목록 · 단건 조회 시나리오
  # 실행: bash e2e/run.sh coupon/coupon_my
  #
  # 시나리오 목록:
  #   1. [정상] 내 쿠폰 목록 조회 → 200, 페이징 응답
  #   2. [정상] 내 쿠폰 단건 조회 → 200, userCouponId 일치
  #   3. [예외] 없는 userCouponId 조회 → 404, COUPON-002
  #   4. [예외] 토큰 없이 조회 → 401
  #
  # 주의: Background에서 매 시나리오마다 ADMIN·USER 계정을 생성하고,
  #       쿠폰을 발급하여 userCouponId를 확보한 뒤 시나리오가 시작된다.
  # ================================================================

  Background:
    * url baseUrl
    * def adminEmail = 'e2e-my-admin-' + java.util.UUID.randomUUID() + '@example.com'
    * def userEmail  = 'e2e-my-user-'  + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'

    # 사전 준비 1: ADMIN 계정 생성 + 로그인
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: #(adminEmail), password: #(testPassword), nickname: 'e2emyadmin' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(adminEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def adminAccessToken = response.data.accessToken

    # 사전 준비 2: 쿠폰 생성
    Given path '/api/v1/coupons'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminAccessToken
    And request { name: '내 쿠폰 조회 테스트', discountType: 'AMOUNT', discountValue: 2000, totalQuantity: 100, startedAt: '2020-01-01T00:00:00', expiredAt: '2099-12-31T23:59:59' }
    When method post
    Then status 201
    * def couponId = response.data.couponId

    # 사전 준비 3: USER 계정 생성 + 로그인
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword), nickname: 'e2emyuser' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def userAccessToken = response.data.accessToken

    # 사전 준비 4: 쿠폰 발급 (비동기 202) → Consumer 처리 대기 → userCouponId 확보
    Given path '/api/v1/coupons/' + couponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userAccessToken
    When method post
    Then status 202
    * java.lang.Thread.sleep(3000)
    Given path '/api/v1/coupons/me'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userAccessToken
    When method get
    Then status 200
    * def userCouponId = response.data.content[0].userCouponId

  # ----------------------------------------------------------------
  # 시나리오 1: 내 쿠폰 목록 조회 시 페이징 형식으로 반환한다
  # ----------------------------------------------------------------
  Scenario: [정상] 내 쿠폰 목록 조회 → 200, 페이징 응답
    Given path '/api/v1/coupons/me'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userAccessToken
    When method get
    Then status 200
    And match response.data.content == '#[_ > 0]'
    And match response.data.content[0].userCouponId == '#uuid'
    And match response.data.content[0].status == 'AVAILABLE'

  # ----------------------------------------------------------------
  # 시나리오 2: 내 쿠폰 단건 조회 시 발급한 userCouponId와 일치하는 정보를 반환한다
  # ----------------------------------------------------------------
  Scenario: [정상] 내 쿠폰 단건 조회 → 200, userCouponId 일치
    Given path '/api/v1/coupons/me/' + userCouponId
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userAccessToken
    When method get
    Then status 200
    And match response.data.userCouponId == userCouponId
    And match response.data.status == 'AVAILABLE'
    And match response.data.couponId == couponId

  # ----------------------------------------------------------------
  # 시나리오 3: 존재하지 않는 userCouponId로 조회하면 404와 COUPON-002를 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 없는 userCouponId 조회 → 404, COUPON-002
    * def randomId = java.util.UUID.randomUUID().toString()
    Given path '/api/v1/coupons/me/' + randomId
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userAccessToken
    When method get
    Then status 404
    And match response.errorCode == 'COUPON-002'

  # ----------------------------------------------------------------
  # 시나리오 4: 토큰 없이 내 쿠폰 조회 시 401을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 토큰 없이 조회 → 401
    Given path '/api/v1/coupons/me'
    And header X-Gateway-Secret = gatewaySecret
    When method get
    Then status 401
