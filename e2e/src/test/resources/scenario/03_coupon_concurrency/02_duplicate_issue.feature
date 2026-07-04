Feature: [시나리오] 쿠폰 중복 발급 차단

  # ================================================================
  # 동일 쿠폰 중복 발급 시도 차단 시나리오
  # 실행: bash e2e/run.sh scenario/03_coupon_concurrency/02_duplicate_issue
  #
  # 시나리오 목록:
  #   1. [정상] 첫 발급 성공 → 201, status=AVAILABLE
  #   2. [예외] 동일 쿠폰 N회 연속 시도 → 1회 성공 후 추가 4회 모두 409, COUPON-003
  #
  # 주의: 각 시나리오는 독립 실행 — Background가 매 시나리오마다 재실행된다.
  # ================================================================

  Background:
    * url baseUrl
    * def adminEmail = 'e2e-dup-admin-' + java.util.UUID.randomUUID() + '@example.com'
    * def userEmail  = 'e2e-dup-user-'  + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'

    # ADMIN 계정 생성 + 로그인
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: #(adminEmail), password: #(testPassword), nickname: 'e2edupAdmin' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(adminEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def adminToken = response.data.accessToken

    # 쿠폰 생성
    Given path '/api/v1/coupons'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '중복 발급 테스트 쿠폰', discountType: 'AMOUNT', discountValue: 1000, totalQuantity: 100, startedAt: '2020-01-01T00:00:00', expiredAt: '2099-12-31T23:59:59' }
    When method post
    Then status 201
    * def couponId = response.data.couponId

    # USER 계정 생성 + 로그인
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword), nickname: 'e2edupUser' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def userToken = response.data.accessToken

  # ----------------------------------------------------------------
  # 시나리오 1: 첫 발급은 성공하고 202 Accepted를 반환한다
  # ----------------------------------------------------------------
  Scenario: [정상] 첫 발급 성공 → 202 Accepted
    Given path '/api/v1/coupons/' + couponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method post
    Then status 202
    And match response.message == '쿠폰이 발급되었습니다.'

  # ----------------------------------------------------------------
  # 시나리오 2: 1회 성공 후 동일 쿠폰 4회 추가 시도 → 모두 409, COUPON-003
  #   반복 시도해도 서버 상태가 변하지 않음을 검증
  # ----------------------------------------------------------------
  Scenario: [예외] 동일 쿠폰 N회 연속 시도 → 1회 성공 후 추가 4회 모두 실패
    # 1회차: 성공
    Given path '/api/v1/coupons/' + couponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method post
    Then status 202

    # 2회차: 중복 시도 → 409
    Given path '/api/v1/coupons/' + couponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method post
    Then status 409
    And match response.errorCode == 'COUPON-003'

    # 3회차: 중복 시도 → 409
    Given path '/api/v1/coupons/' + couponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method post
    Then status 409
    And match response.errorCode == 'COUPON-003'

    # 4회차: 중복 시도 → 409
    Given path '/api/v1/coupons/' + couponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method post
    Then status 409
    And match response.errorCode == 'COUPON-003'

    # 5회차: 중복 시도 → 409
    Given path '/api/v1/coupons/' + couponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    When method post
    Then status 409
    And match response.errorCode == 'COUPON-003'
