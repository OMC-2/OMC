Feature: 쿠폰 발급 Rate Limiting

  # ================================================================
  # Gateway의 couponRateLimiter(replenishRate=5, burstCapacity=10)를
  # 실제 서비스 호출로 E2E 검증.
  #
  # 실행: bash e2e/run.sh coupon/coupon_rate_limit
  #
  # 시나리오 목록:
  #   1. [Rate Limit] burstCapacity(10) 초과 시 429 반환
  #   2. [Rate Limit] 다른 유저는 버킷이 독립적이다
  #   3. [Rate Limit] 429 응답 포맷 검증
  #
  # 동시성 전략:
  #   ConcurrentIssueHelper(CountDownLatch)로 15개 요청을 동시에 발사.
  #   burst=10이므로 반드시 5개 이상 429가 발생함 (타이밍 의존성 없음).
  #   순차 요청은 replenishRate(5/sec)와 소비 속도가 맞물려 429가 비결정적이므로
  #   사용하지 않음.
  #
  # 버킷 초기화:
  #   Background에서 매 Scenario마다 새 UUID 계정을 생성 → Redis 버킷 자동 초기화.
  #   버킷 키 = coupon-service:{keycloakUUID}
  # ================================================================

  Background:
    * url baseUrl
    * def adminEmail = 'e2e-rl-admin-' + java.util.UUID.randomUUID() + '@example.com'
    * def userAEmail = 'e2e-rl-usera-' + java.util.UUID.randomUUID() + '@example.com'
    * def userBEmail = 'e2e-rl-userb-' + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'

    # ADMIN 계정 생성 + 로그인
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: #(adminEmail), password: #(testPassword), nickname: 'e2erladmin' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(adminEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def adminToken = response.data.accessToken

    # 쿠폰 생성 (수량 100개 — Rate Limit 소진이 목적이므로 재고는 충분히)
    Given path '/api/v1/coupons'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { name: 'Rate Limit 테스트 쿠폰', discountType: 'AMOUNT', discountValue: 1000, totalQuantity: 100, startedAt: '2020-01-01T00:00:00', expiredAt: '2099-12-31T23:59:59' }
    When method post
    Then status 201
    * def couponId = response.data.couponId

    # USER-A 계정 생성 + 로그인 (버킷 소진 주체)
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userAEmail), password: #(testPassword), nickname: 'e2erlusera' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userAEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def userAToken = response.data.accessToken

    # USER-B 계정 생성 + 로그인 (독립 버킷 검증용)
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userBEmail), password: #(testPassword), nickname: 'e2erluserb' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userBEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def userBToken = response.data.accessToken

  # ----------------------------------------------------------------
  # 시나리오 1: burstCapacity(10) 초과 시 429 반환
  #   USER-A 토큰으로 15개를 동시에 발사 → 5개 이상 429
  # ----------------------------------------------------------------
  Scenario: [Rate Limit] burstCapacity 초과 시 429 반환
    # Rate Limit 버킷 충전 대기 (이전 테스트에서 소진된 버킷이 있을 경우 대비)
    * java.lang.Thread.sleep(3000)
    * def Helper = Java.type('e2e.ConcurrentIssueHelper')
    * def tokenList = []
    * eval for (var i = 0; i < 15; i++) tokenList.push(userAToken)
    * def results = Helper.issueCoupons(baseUrl, gatewaySecret, couponId + '', tokenList)
    * def statuses = karate.map(results, function(r){ return r.issueStatus })
    * assert statuses.indexOf(429) >= 0

  # ----------------------------------------------------------------
  # 시나리오 2: 다른 유저는 버킷이 독립적이다
  #   USER-A 버킷 소진 후 USER-B는 429가 아닌 응답(201/409)을 받아야 함
  # ----------------------------------------------------------------
  Scenario: [Rate Limit] 다른 유저는 버킷이 독립적이다
    # Rate Limit 버킷 충전 대기
    * java.lang.Thread.sleep(3000)
    * def Helper = Java.type('e2e.ConcurrentIssueHelper')

    # USER-A 버킷 소진 (15개 동시 요청 → 5개 이상 429)
    * def tokenListA = []
    * eval for (var i = 0; i < 15; i++) tokenListA.push(userAToken)
    * def resultsA = Helper.issueCoupons(baseUrl, gatewaySecret, couponId + '', tokenListA)
    * def statusesA = karate.map(resultsA, function(r){ return r.issueStatus })
    * assert statusesA.indexOf(429) >= 0

    # USER-B는 별도 버킷이므로 영향 없음 (202 최초 발급 또는 409 중복)
    Given path '/api/v1/coupons/' + couponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userBToken
    When method post
    Then assert responseStatus != 429

  # ----------------------------------------------------------------
  # 시나리오 3: 429 응답 포맷 검증
  #   RateLimitErrorResponseFilter가 반환하는 JSON 바디 구조를 E2E로 검증
  # ----------------------------------------------------------------
  Scenario: [Rate Limit] 429 응답 포맷 검증
    # Rate Limit 버킷 충전 대기
    * java.lang.Thread.sleep(3000)
    * def Helper = Java.type('e2e.ConcurrentIssueHelper')
    * def tokenList = []
    * eval for (var i = 0; i < 15; i++) tokenList.push(userAToken)
    * def results = Helper.issueCoupons(baseUrl, gatewaySecret, couponId + '', tokenList)
    * def found429 = karate.filter(results, function(r){ return r.issueStatus == 429 })
    * assert found429.length > 0
    * json rateLimitResponse = found429[0].responseBody
    * match rateLimitResponse.success == false
    * match rateLimitResponse.status == 429
    * match rateLimitResponse.errorCode == 'RATE_LIMIT_EXCEEDED'
    * match rateLimitResponse.message == '요청이 너무 많습니다. 잠시 후 다시 시도해주세요.'
