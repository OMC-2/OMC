Feature: 쿠폰 발급 Cold Start 검증

  # 실행: bash e2e/run.sh coupon/coupon_cold_start

  Background:
    * url baseUrl
    * def adminEmail = 'e2e-cold-admin-' + java.util.UUID.randomUUID() + '@example.com'
    * def user1Email = 'e2e-cold-user1-' + java.util.UUID.randomUUID() + '@example.com'
    * def user2Email = 'e2e-cold-user2-' + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'

    # ADMIN 계정 생성 + 로그인
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: #(adminEmail), password: #(testPassword), nickname: 'e2ecoldadmin' }
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
    And request { name: 'Cold Start 검증 쿠폰', discountType: 'AMOUNT', discountValue: 1000, totalQuantity: 100, startedAt: '2020-01-01T00:00:00', expiredAt: '2099-12-31T23:59:59' }
    When method post
    Then status 201
    * def couponId = response.data.couponId

    # USER1 계정 생성 + 로그인
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(user1Email), password: #(testPassword), nickname: 'e2ecolduser1' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(user1Email), password: #(testPassword) }
    When method post
    Then status 200
    * def user1AccessToken = response.data.accessToken

    # USER2 계정 생성 + 로그인
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(user2Email), password: #(testPassword), nickname: 'e2ecolduser2' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(user2Email), password: #(testPassword) }
    When method post
    Then status 200
    * def user2AccessToken = response.data.accessToken

  Scenario: [정상] Cold start 이후 다른 유저도 쿠폰 발급 성공 → 연속 2회 모두 202
    # 1st 발급: USER1 (cold start 첫 호출)
    Given path '/api/v1/coupons/' + couponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user1AccessToken
    When method post
    Then status 202
    And match response.message == '쿠폰이 발급되었습니다.'

    # 2nd 발급: USER2 (warm 두 번째 호출)
    Given path '/api/v1/coupons/' + couponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user2AccessToken
    When method post
    Then status 202
    And match response.message == '쿠폰이 발급되었습니다.'
