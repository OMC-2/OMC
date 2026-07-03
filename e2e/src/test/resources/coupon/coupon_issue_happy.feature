Feature: 쿠폰 발급 성공 (Zipkin 확인용)

  # 실행: bash e2e/run.sh coupon/coupon_issue_happy

  Background:
    * url baseUrl
    * def adminEmail = 'e2e-happy-admin-' + java.util.UUID.randomUUID() + '@example.com'
    * def userEmail  = 'e2e-happy-user-'  + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'

    # ADMIN 계정 생성 + 로그인
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: #(adminEmail), password: #(testPassword), nickname: 'e2ehappyadmin' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(adminEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def adminAccessToken = response.data.accessToken

    # 쿠폰 생성
    Given path '/api/v1/coupons'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminAccessToken
    And request { name: 'Zipkin 확인용 쿠폰', discountType: 'AMOUNT', discountValue: 1000, totalQuantity: 100, startedAt: '2020-01-01T00:00:00', expiredAt: '2099-12-31T23:59:59' }
    When method post
    Then status 201
    * def couponId = response.data.couponId

    # USER 계정 생성 + 로그인
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword), nickname: 'e2ehappyuser' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def userAccessToken = response.data.accessToken

  Scenario: [정상] 쿠폰 발급 성공 → 201
    Given path '/api/v1/coupons/' + couponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userAccessToken
    When method post
    Then status 201
    And match response.data.userCouponId == '#uuid'
    And match response.data.status == 'AVAILABLE'
    And match response.data.couponId == couponId
