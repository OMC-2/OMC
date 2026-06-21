Feature: 쿠폰 발급

  # ================================================================
  # 쿠폰 발급 시나리오
  # 실행: bash e2e/run.sh coupon/coupon_issue
  #
  # 시나리오 목록:
  #   1. [정상] USER 쿠폰 발급 성공 → 201, userCouponId 반환
  #   2. [예외] 동일 쿠폰 중복 발급 → 409, COUPON-003
  #   3. [예외] 재고 소진 후 발급 시도 → 409, COUPON-004
  #   4. [예외] 토큰 없이 발급 → 401
  #   5. [예외] ADMIN 토큰으로 발급 → 403
  #
  # 주의: Background에서 매 시나리오마다 ADMIN·USER 계정을 생성하고,
  #       ADMIN으로 쿠폰 2개(일반 100개, 재고1개)를 생성한 뒤 시나리오가 시작된다.
  # ================================================================

  Background:
    * url baseUrl
    * def adminEmail = 'e2e-issue-admin-' + java.util.UUID.randomUUID() + '@example.com'
    * def userEmail  = 'e2e-issue-user-'  + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'

    # 사전 준비 1: ADMIN 계정 생성 + 로그인
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: #(adminEmail), password: #(testPassword), nickname: 'e2eissueadmin' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(adminEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def adminAccessToken = response.data.accessToken

    # 사전 준비 2: 일반 쿠폰 생성 (totalQuantity=100)
    Given path '/api/v1/coupons'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminAccessToken
    And request { name: '일반 발급 쿠폰', discountType: 'AMOUNT', discountValue: 1000, totalQuantity: 100, startedAt: '2020-01-01T00:00:00', expiredAt: '2099-12-31T23:59:59' }
    When method post
    Then status 201
    * def normalCouponId = response.data.couponId

    # 사전 준비 3: 재고 1개짜리 쿠폰 생성 (품절 테스트용)
    Given path '/api/v1/coupons'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminAccessToken
    And request { name: '한정 쿠폰', discountType: 'RATE', discountValue: 10, totalQuantity: 1, startedAt: '2020-01-01T00:00:00', expiredAt: '2099-12-31T23:59:59' }
    When method post
    Then status 201
    * def limitedCouponId = response.data.couponId

    # 사전 준비 4: USER 계정 생성 + 로그인
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword), nickname: 'e2eissueuser' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def userAccessToken = response.data.accessToken

  # ----------------------------------------------------------------
  # 시나리오 1: USER가 쿠폰을 발급하면 201과 함께 userCouponId를 반환한다
  # ----------------------------------------------------------------
  Scenario: [정상] 쿠폰 발급 성공 → 201, userCouponId 반환
    Given path '/api/v1/coupons/' + normalCouponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userAccessToken
    When method post
    Then status 201
    And match response.data.userCouponId == '#uuid'
    And match response.data.status == 'AVAILABLE'
    And match response.data.couponId == normalCouponId

  # ----------------------------------------------------------------
  # 시나리오 2: 동일 쿠폰을 2번 발급하면 409와 COUPON-003을 반환한다
  #   1단계: 정상 발급
  #   2단계: 동일 쿠폰 재발급 시도 → 409
  # ----------------------------------------------------------------
  Scenario: [예외] 동일 쿠폰 중복 발급 → 409, COUPON-003
    # 1단계: 1차 발급 성공
    Given path '/api/v1/coupons/' + normalCouponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userAccessToken
    When method post
    Then status 201
    # 2단계: 동일 쿠폰 재발급 → 409
    Given path '/api/v1/coupons/' + normalCouponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userAccessToken
    When method post
    Then status 409
    And match response.errorCode == 'COUPON-003'

  # ----------------------------------------------------------------
  # 시나리오 3: 재고가 소진된 쿠폰 발급 시도 시 409와 COUPON-004를 반환한다
  #   1단계: 유일한 재고 발급 (다른 유저로 발급하기 위해 새 유저 생성)
  #   2단계: USER로 동일 쿠폰 발급 시도 → 재고 없음 → 409
  # ----------------------------------------------------------------
  Scenario: [예외] 재고 소진 후 발급 시도 → 409, COUPON-004
    # 1단계: 새 유저를 만들어 재고 1개 쿠폰을 먼저 소진시킨다
    * def otherEmail = 'e2e-issue-other-' + java.util.UUID.randomUUID() + '@example.com'
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(otherEmail), password: #(testPassword), nickname: 'e2eissueother' }
    When method post
    Then status 201
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(otherEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def otherAccessToken = response.data.accessToken
    Given path '/api/v1/coupons/' + limitedCouponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + otherAccessToken
    When method post
    Then status 201
    # 2단계: USER로 동일 쿠폰 발급 시도 → 재고 없음 → 409
    Given path '/api/v1/coupons/' + limitedCouponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userAccessToken
    When method post
    Then status 409
    And match response.errorCode == 'COUPON-004'

  # ----------------------------------------------------------------
  # 시나리오 4: 토큰 없이 발급 시도 시 401을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 토큰 없이 발급 → 401
    Given path '/api/v1/coupons/' + normalCouponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    When method post
    Then status 401

  # ----------------------------------------------------------------
  # 시나리오 5: ADMIN 토큰으로 USER 전용 발급 시도 시 403을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] ADMIN 토큰으로 발급 → 403
    Given path '/api/v1/coupons/' + normalCouponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminAccessToken
    When method post
    Then status 403
