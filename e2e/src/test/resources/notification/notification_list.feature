Feature: 알림 목록 조회

  # ================================================================
  # 알림 목록 조회 시나리오
  # 실행: bash e2e/run.sh notification/notification_list
  #
  # 시나리오 목록:
  #   1. [정상] 신규 유저 알림 목록 조회 → 200, 빈 배열
  #   2. [정상] 쿠폰 발급 후 알림 목록 조회 → 200, COUPON_ISSUED 알림 1건
  #   3. [예외] 토큰 없이 조회 → 401
  #   4. [예외] 변조된 JWT로 조회 → 401
  #
  # 주의: Background에서 매 시나리오마다 USER 계정을 생성하고 로그인한다.
  #       시나리오 2는 쿠폰 발급 크로스서비스 흐름으로 알림 데이터를 생성한다.
  # ================================================================

  Background:
    * url baseUrl
    * def userEmail  = 'e2e-list-user-' + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'

    # 사전 준비 1: USER 계정 생성 + 로그인
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword), nickname: 'e2elistuser' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def userAccessToken = response.data.accessToken

  # ----------------------------------------------------------------
  # 시나리오 1: 신규 유저의 알림 목록 조회 시 빈 배열을 반환한다
  # ----------------------------------------------------------------
  Scenario: [정상] 신규 유저 알림 목록 조회 → 200, 빈 배열
    Given path '/api/v1/notifications'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userAccessToken
    When method get
    Then status 200
    And match response.data.content == []
    And match response.data.totalElements == 0

  # ----------------------------------------------------------------
  # 시나리오 2: 쿠폰 발급 후 알림 목록 조회 시 COUPON_ISSUED 알림 1건을 반환한다
  #   1단계: ADMIN 계정 생성 + 로그인
  #   2단계: ADMIN으로 쿠폰 생성
  #   3단계: USER로 쿠폰 발급 → coupon.issued Kafka 이벤트 발행
  #   4단계: 3초 대기 (Kafka Consumer 처리)
  #   5단계: 알림 목록 조회 → COUPON_ISSUED 알림 확인
  # ----------------------------------------------------------------
  Scenario: [정상] 쿠폰 발급 후 알림 목록 조회 → COUPON_ISSUED 알림 1건
    # 1단계: ADMIN 계정 생성 + 로그인
    * def adminEmail = 'e2e-list-admin-' + java.util.UUID.randomUUID() + '@example.com'
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: #(adminEmail), password: #(testPassword), nickname: 'e2elistadmin' }
    When method post
    Then status 201
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(adminEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def adminAccessToken = response.data.accessToken
    # 2단계: ADMIN으로 쿠폰 생성
    Given path '/api/v1/coupons'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminAccessToken
    And request { name: '알림 목록 테스트 쿠폰', discountType: 'AMOUNT', discountValue: 1000, totalQuantity: 100, startedAt: '2020-01-01T00:00:00', expiredAt: '2099-12-31T23:59:59' }
    When method post
    Then status 201
    * def couponId = response.data.couponId
    # 3단계: USER로 쿠폰 발급 → coupon.issued 이벤트 발행
    Given path '/api/v1/coupons/' + couponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userAccessToken
    When method post
    Then status 201
    # 4단계: OutboxPoller(5s) + Kafka Consumer 처리 대기
    * eval java.lang.Thread.sleep(10000)
    # 5단계: 알림 목록 조회 → COUPON_ISSUED 알림 확인
    Given path '/api/v1/notifications'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userAccessToken
    When method get
    Then status 200
    And match response.data.totalElements == 1
    And match response.data.content[0].notificationType == 'COUPON_ISSUED'
    And match response.data.content[0].notificationId == '#uuid'

  # ----------------------------------------------------------------
  # 시나리오 3: 토큰 없이 조회 시 401을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 토큰 없이 조회 → 401
    Given path '/api/v1/notifications'
    And header X-Gateway-Secret = gatewaySecret
    When method get
    Then status 401

  # ----------------------------------------------------------------
  # 시나리오 4: 변조된 JWT로 조회 시 서명 검증 실패로 401을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 변조된 JWT로 조회 → 401
    Given path '/api/v1/notifications'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer invalid.jwt.token'
    When method get
    Then status 401
