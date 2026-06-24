Feature: 드롭 구매 선점

  # ================================================================
  # 드롭 구매 선점 시나리오
  # 실행: bash e2e/run.sh drop/drop_purchase
  #
  # 시나리오 목록:
  #   1. [정상] OPEN 드롭 구매 선점 → 202, orderId + queueNumber
  #   2. [예외] 같은 사용자 중복 구매 → 409 + DROP-005
  #   3. [예외] 재고 소진 후 구매 → 409 + DROP-004
  #   4. [예외] 미오픈 드롭 구매 → 409 + DROP-002
  #   5. [예외] 토큰 없이 구매 → 401
  #
  # 주의: Background에서 startAt을 +5초로 설정한 드롭을 생성하고,
  #       DropStatusScheduler(5초 주기)가 OPEN으로 전이할 때까지
  #       retry until로 폴링한다. 최대 15초 대기.
  #       (@FutureOrPresent는 나노초 단위 → now()는 서버 도달 시 이미 과거가 됨)
  #
  #       시나리오 3(재고 소진)은 totalQty=1인 드롭을 시나리오 내에서
  #       별도로 생성하고 OPEN 상태까지 폴링한 뒤 검증한다.
  # ================================================================

  Background:
    * url baseUrl
    * def adminEmail = 'e2e-dropp-admin-' + java.util.UUID.randomUUID() + '@example.com'
    * def user1Email = 'e2e-dropp-user1-' + java.util.UUID.randomUUID() + '@example.com'
    * def user2Email = 'e2e-dropp-user2-' + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'

    # 사전 준비 1: ADMIN 계정 생성 + 로그인
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: #(adminEmail), password: #(testPassword), nickname: 'e2edroppAdmin' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(adminEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def adminToken = response.data.accessToken

    # 사전 준비 2: USER1 계정 생성 + 로그인
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(user1Email), password: #(testPassword), nickname: 'e2edroppUser1' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(user1Email), password: #(testPassword) }
    When method post
    Then status 200
    * def user1Token = response.data.accessToken

    # 사전 준비 3: USER2 계정 생성 + 로그인 (재고 소진 시나리오용)
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(user2Email), password: #(testPassword), nickname: 'e2edroppUser2' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(user2Email), password: #(testPassword) }
    When method post
    Then status 200
    * def user2Token = response.data.accessToken

    # 사전 준비 4: startAt을 +5초로 설정 → @FutureOrPresent 통과 + 스케줄러가 곧 OPEN으로 전이
    * def fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    * def pastStartAt  = java.time.LocalDateTime.now().plusSeconds(5).format(fmt)
    * def futureEndAt  = java.time.LocalDateTime.now().plusDays(1).format(fmt)
    * def productId = java.util.UUID.randomUUID().toString()

    Given path '/api/v1/admin/drops'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { productId: #(productId), startAt: #(pastStartAt), endAt: #(futureEndAt), totalQty: 100, holdTtlSec: 300 }
    When method post
    Then status 201
    * def dropId = response.data.dropId

    # 사전 준비 5: DropStatusScheduler가 OPEN으로 전이할 때까지 폴링 (최대 15초)
    * configure retry = { count: 15, interval: 1000 }
    * retry until response.data.status == 'OPEN'
    Given path '/api/v1/drops/' + dropId
    And header X-Gateway-Secret = gatewaySecret
    When method get

  # ----------------------------------------------------------------
  # 시나리오 1: OPEN 드롭에 구매 선점 시 202와 orderId, queueNumber를 반환한다
  # ----------------------------------------------------------------
  Scenario: [정상] OPEN 드롭 구매 선점 → 202, orderId + queueNumber
    Given path '/api/v1/drops/' + dropId + '/purchase'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user1Token
    When method post
    Then status 202
    And match response.data.orderId == '#uuid'
    And match response.data.queueNumber == '#number'
    And assert response.data.queueNumber >= 1

  # ----------------------------------------------------------------
  # 시나리오 2: 같은 사용자가 중복 구매 시 409와 DROP-005를 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 중복 구매 → 409 + DROP-005
    # 첫 번째 구매 성공
    Given path '/api/v1/drops/' + dropId + '/purchase'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user1Token
    When method post
    Then status 202

    # 동일 사용자 재구매 → 중복
    Given path '/api/v1/drops/' + dropId + '/purchase'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user1Token
    When method post
    Then status 409
    And match response.errorCode == 'DROP-005'

  # ----------------------------------------------------------------
  # 시나리오 3: 재고 소진 후 구매 시 409와 DROP-004를 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 재고 소진 → 409 + DROP-004
    # totalQty=1인 드롭 별도 생성 (Background 폴링 후 pastStartAt이 과거가 되므로 시각 재계산)
    * def soldOutFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    * def soldOutStartAt = java.time.LocalDateTime.now().plusSeconds(5).format(soldOutFmt)
    Given path '/api/v1/admin/drops'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { productId: #(productId), startAt: #(soldOutStartAt), endAt: #(futureEndAt), totalQty: 1, holdTtlSec: 300 }
    When method post
    Then status 201
    * def soldOutDropId = response.data.dropId

    # totalQty=1 드롭이 OPEN될 때까지 폴링
    * configure retry = { count: 15, interval: 1000 }
    * retry until response.data.status == 'OPEN'
    Given path '/api/v1/drops/' + soldOutDropId
    And header X-Gateway-Secret = gatewaySecret
    When method get

    # user1이 재고 1개 선점
    Given path '/api/v1/drops/' + soldOutDropId + '/purchase'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user1Token
    When method post
    Then status 202

    # user2가 재고 소진 후 구매 시도
    Given path '/api/v1/drops/' + soldOutDropId + '/purchase'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user2Token
    When method post
    Then status 409
    And match response.errorCode == 'DROP-004'

  # ----------------------------------------------------------------
  # 시나리오 4: OPEN 상태가 아닌 드롭 구매 시 409와 DROP-002를 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 미오픈(SCHEDULED) 드롭 구매 → 409 + DROP-002
    # startAt을 미래로 설정 → SCHEDULED 상태 유지
    * def futureFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    * def futureStartAt = java.time.LocalDateTime.now().plusDays(1).format(futureFmt)
    * def farFutureEndAt = java.time.LocalDateTime.now().plusDays(2).format(futureFmt)

    Given path '/api/v1/admin/drops'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { productId: #(productId), startAt: #(futureStartAt), endAt: #(farFutureEndAt), totalQty: 100, holdTtlSec: 300 }
    When method post
    Then status 201
    * def scheduledDropId = response.data.dropId

    # SCHEDULED 상태 드롭 구매 시도
    Given path '/api/v1/drops/' + scheduledDropId + '/purchase'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user1Token
    When method post
    Then status 409
    And match response.errorCode == 'DROP-002'

  # ----------------------------------------------------------------
  # 시나리오 5: 토큰 없이 구매 시 401을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 토큰 없이 구매 → 401
    Given path '/api/v1/drops/' + dropId + '/purchase'
    And header X-Gateway-Secret = gatewaySecret
    When method post
    Then status 401
