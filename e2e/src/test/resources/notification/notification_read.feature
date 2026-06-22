Feature: 알림 읽음 처리

  # ================================================================
  # 알림 읽음 처리 시나리오
  # 실행: bash e2e/run.sh notification/notification_read
  #
  # 시나리오 목록:
  #   1. [정상] 본인 알림 읽음 처리 성공 → 200
  #   2. [예외] 다른 유저의 알림 읽음 처리 → 403, NOTIFICATION-002
  #   3. [예외] 존재하지 않는 notificationId → 404, NOTIFICATION-001
  #   4. [예외] X-Gateway-Secret 없이 읽음 처리 → 403
  #   5. [예외] 토큰 없이 읽음 처리 → 401
  #
  # 주의: Background에서 매 시나리오마다 USER·ADMIN·OtherUser 계정을 생성하고,
  #       쿠폰 발급 크로스서비스 흐름으로 USER의 알림 1건을 생성한 뒤
  #       notificationId를 추출하고 시나리오가 시작된다.
  # ================================================================

  Background:
    * url baseUrl
    * def userEmail  = 'e2e-read-user-'  + java.util.UUID.randomUUID() + '@example.com'
    * def adminEmail = 'e2e-read-admin-' + java.util.UUID.randomUUID() + '@example.com'
    * def otherEmail = 'e2e-read-other-' + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'

    # 사전 준비 1: USER 계정 생성 + 로그인
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword), nickname: 'e2ereaduser' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def userAccessToken = response.data.accessToken

    # 사전 준비 2: ADMIN 계정 생성 + 로그인
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: #(adminEmail), password: #(testPassword), nickname: 'e2ereadadmin' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(adminEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def adminAccessToken = response.data.accessToken

    # 사전 준비 3: 다른 USER 계정 생성 + 로그인 (타인 접근 시나리오용)
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(otherEmail), password: #(testPassword), nickname: 'e2ereadother' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(otherEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def otherAccessToken = response.data.accessToken

    # 사전 준비 4: ADMIN으로 쿠폰 생성
    Given path '/api/v1/coupons'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminAccessToken
    And request { name: '읽음 처리 테스트 쿠폰', discountType: 'AMOUNT', discountValue: 1000, totalQuantity: 100, startedAt: '2020-01-01T00:00:00', expiredAt: '2099-12-31T23:59:59' }
    When method post
    Then status 201
    * def couponId = response.data.couponId

    # 사전 준비 5: USER로 쿠폰 발급 → coupon.issued Kafka 이벤트 발행
    Given path '/api/v1/coupons/' + couponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userAccessToken
    When method post
    Then status 201

    # 사전 준비 6: Kafka Consumer 처리 대기 (3초)
    * eval karate.pause(3000)

    # 사전 준비 7: 알림 목록 조회 → notificationId 추출
    Given path '/api/v1/notifications'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userAccessToken
    When method get
    Then status 200
    * def notificationId = response.data.content[0].notificationId

  # ----------------------------------------------------------------
  # 시나리오 1: 본인 알림을 읽음 처리하면 200을 반환한다
  # ----------------------------------------------------------------
  Scenario: [정상] 본인 알림 읽음 처리 성공 → 200
    Given path '/api/v1/notifications/' + notificationId + '/read'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userAccessToken
    When method patch
    Then status 200
    And match response.data == null

  # ----------------------------------------------------------------
  # 시나리오 2: 다른 유저의 알림을 읽음 처리하면 403과 NOTIFICATION-002를 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 다른 유저의 알림 읽음 처리 → 403, NOTIFICATION-002
    Given path '/api/v1/notifications/' + notificationId + '/read'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + otherAccessToken
    When method patch
    Then status 403
    And match response.errorCode == 'NOTIFICATION-002'

  # ----------------------------------------------------------------
  # 시나리오 3: 존재하지 않는 notificationId로 읽음 처리하면 404와 NOTIFICATION-001을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 존재하지 않는 notificationId → 404, NOTIFICATION-001
    * def randomId = java.util.UUID.randomUUID().toString()
    Given path '/api/v1/notifications/' + randomId + '/read'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userAccessToken
    When method patch
    Then status 404
    And match response.errorCode == 'NOTIFICATION-001'

  # ----------------------------------------------------------------
  # 시나리오 4: X-Gateway-Secret 없이 읽음 처리 시 403을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] X-Gateway-Secret 없이 읽음 처리 → 403
    Given path '/api/v1/notifications/' + notificationId + '/read'
    And header Authorization = 'Bearer ' + userAccessToken
    When method patch
    Then status 403

  # ----------------------------------------------------------------
  # 시나리오 5: 토큰 없이 읽음 처리 시 401을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 토큰 없이 읽음 처리 → 401
    Given path '/api/v1/notifications/' + notificationId + '/read'
    And header X-Gateway-Secret = gatewaySecret
    When method patch
    Then status 401
