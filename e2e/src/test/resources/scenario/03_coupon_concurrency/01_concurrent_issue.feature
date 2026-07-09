Feature: [시나리오] 쿠폰 선착순 동시 발급

  # ================================================================
  # 쿠폰 선착순 발급 시나리오
  # 실행: bash e2e/run.sh scenario/03_coupon_concurrency/01_concurrent_issue
  #
  # 검증 흐름:
  #   callSingle로 ADMIN + 유저 50명 사전 생성 (Keycloak 호출은 여기서 끝)
  #   각 시나리오는 쿠폰 생성 + 동시 발급만 수행 (Keycloak 호출 없음)
  #   → 수량만큼만 성공(201), 나머지 실패(409, COUPON-004)
  #   → 성공한 유저 발급 내역 AVAILABLE 확인
  #   → 성공한 유저 COUPON_ISSUED 알림 수신 확인 (Kafka 비동기)
  # ================================================================

  Background:
    * url baseUrl
    * def setup = karate.callSingle('classpath:scenario/03_coupon_concurrency/_setup_concurrent_users.feature')
    * def adminToken = setup.adminToken

  # ----------------------------------------------------------------
  # 시나리오 1: 유저 10명 / 수량 5 → 성공 5건 + 실패 5건 + 내역/알림 검증
  # ----------------------------------------------------------------
  Scenario: [정상] 소규모 동시 발급 → 수량만 성공 + 내역/알림 검증
    * def tokens = setup.tokens.slice(0, 10)

    # STEP 1: 수량 5짜리 쿠폰 생성
    Given path '/api/v1/coupons'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '소규모 동시 발급 테스트 쿠폰', discountType: 'AMOUNT', discountValue: 1000, totalQuantity: 5, startedAt: '2020-01-01T00:00:00', expiredAt: '2099-12-31T23:59:59' }
    When method post
    Then status 201
    * def couponId = response.data.couponId

    # STEP 2: 10명 동시 발급 (Java CountDownLatch)
    * def ConcurrentHelper = Java.type('e2e.ConcurrentIssueHelper')
    * def results = ConcurrentHelper.issueCoupons(baseUrl, gatewaySecret, couponId + '', tokens)

    # STEP 3: 성공/실패 카운트 검증
    * def successResults = karate.filter(results, function(r){ return r.issueStatus == 202 })
    * def failResults    = karate.filter(results, function(r){ return r.issueStatus == 409 })
    * assert successResults.length == 5
    * assert failResults.length == 5
    * match each failResults[*].issueErrorCode == 'COUPON-004'

    # STEP 4: 성공한 유저 중 샘플 1명의 내 쿠폰 목록 조회 → AVAILABLE 확인 (Consumer 비동기 처리 대기)
    * def sampleToken = successResults[0].token
    * configure retry = { count: 10, interval: 2000 }
    Given path '/api/v1/coupons/me'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + sampleToken
    And retry until karate.filter(response.data.content, function(c){ return c.couponId == couponId }).length > 0
    When method get
    Then status 200
    * def issuedCoupons = karate.filter(response.data.content, function(c){ return c.couponId == couponId })
    And assert issuedCoupons.length == 1
    And match issuedCoupons[0].status == 'AVAILABLE'

    # STEP 5: 성공한 유저 COUPON_ISSUED 알림 수신 확인 (Kafka 비동기 최대 30초 대기)
    * configure retry = { count: 15, interval: 2000 }
    Given path '/api/v1/notifications'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + sampleToken
    And retry until karate.filter(response.data.content, function(n){ return n.notificationType == 'COUPON_ISSUED' }).length > 0
    When method get
    Then status 200
    * def couponNotifications = karate.filter(response.data.content, function(n){ return n.notificationType == 'COUPON_ISSUED' })
    And assert couponNotifications.length > 0

  # ----------------------------------------------------------------
  # 시나리오 2: 유저 50명 / 수량 25 → 성공 25건 + 실패 25건
  # ----------------------------------------------------------------
  @large
  Scenario: [정상] 대규모 동시 발급 → 수량만 성공
    * def tokens = setup.tokens

    # STEP 1: 수량 25짜리 쿠폰 생성
    Given path '/api/v1/coupons'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '대규모 동시 발급 테스트 쿠폰', discountType: 'AMOUNT', discountValue: 1000, totalQuantity: 25, startedAt: '2020-01-01T00:00:00', expiredAt: '2099-12-31T23:59:59' }
    When method post
    Then status 201
    * def couponId = response.data.couponId

    # STEP 2: 50명 동시 발급 (Java CountDownLatch)
    * def ConcurrentHelper = Java.type('e2e.ConcurrentIssueHelper')
    * def results = ConcurrentHelper.issueCoupons(baseUrl, gatewaySecret, couponId + '', tokens)

    # STEP 3: 성공/실패 카운트 검증
    * def successResults = karate.filter(results, function(r){ return r.issueStatus == 202 })
    * def failResults    = karate.filter(results, function(r){ return r.issueStatus == 409 })
    * assert successResults.length == 25
    * assert failResults.length == 25
    * match each failResults[*].issueErrorCode == 'COUPON-004'
