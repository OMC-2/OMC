Feature: [시나리오] 구매 hold 만료 선점 자동 해제

  # ================================================================
  # 구매 hold 만료 이후 다음 사용자 구매 기회 이전 시나리오
  # 실행: bash e2e/run.sh scenario/04_payment_saga/03_hold_expire
  #
  # 검증 흐름:
  #   Drop: 짧은 TTL의 구매 hold 생성
  #   Order: 존재하지 않는 상품으로 주문 생성 중단
  #   Drop: hold 만료 → 선점과 재고 자동 복구
  #   User: 다음 사용자가 동일 드롭 구매
  # ================================================================

  Background:
    * url baseUrl
    * def fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    * def uniqueId = java.util.UUID.randomUUID().toString()
    * def testPassword = 'password123'
    * def adminEmail = 'e2e-hold-expire-admin-' + uniqueId + '@example.com'
    * def user1Email = 'e2e-hold-expire-user1-' + uniqueId + '@example.com'
    * def user2Email = 'e2e-hold-expire-user2-' + uniqueId + '@example.com'

    # User Service에 테스트용 관리자 계정을 생성
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: '#(adminEmail)', password: '#(testPassword)', nickname: 'holdexpireadmin' }
    When method post
    Then status 201

    # User Service에서 관리자 계정으로 로그인해 JWT를 발급
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: '#(adminEmail)', password: '#(testPassword)' }
    When method post
    Then status 200
    * def adminToken = response.data.accessToken

    # User Service에 첫 번째 구매자 계정을 생성
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: '#(user1Email)', password: '#(testPassword)', nickname: 'holdexpireuser1' }
    When method post
    Then status 201

    # User Service에서 첫 번째 구매자로 로그인해 JWT를 발급
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: '#(user1Email)', password: '#(testPassword)' }
    When method post
    Then status 200
    * def user1Token = response.data.accessToken

    # User Service에 두 번째 구매자 계정을 생성
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: '#(user2Email)', password: '#(testPassword)', nickname: 'holdexpireuser2' }
    When method post
    Then status 201

    # User Service에서 두 번째 구매자로 로그인해 JWT를 발급
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: '#(user2Email)', password: '#(testPassword)' }
    When method post
    Then status 200
    * def user2Token = response.data.accessToken

  Scenario: 구매 hold 만료 → 선점 해제 → 다음 사용자 기회 이전
    # 존재하지 않는 상품으로 주문 생성을 중단시켜 결제가 완료되지 않는 hold를 생성
    * def missingProductId = java.util.UUID.randomUUID().toString()
    * def startAt = java.time.LocalDateTime.now().plusSeconds(5).format(fmt)
    * def endAt = java.time.LocalDateTime.now().plusDays(1).format(fmt)

    # Drop Service에 주문 생성이 완료되지 않을 짧은 TTL의 드롭을 생성
    Given path '/api/v1/admin/drops'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { productId: '#(missingProductId)', startAt: '#(startAt)', endAt: '#(endAt)', totalQty: 1, holdTtlSec: 2 }
    When method post
    Then status 201
    * def dropId = response.data.dropId

    * configure retry = { count: 20, interval: 1000 }
    * retry until response.data.status == 'OPEN'
    # Drop Service에서 드롭이 구매 가능한 OPEN 상태가 될 때까지 조회
    Given path '/api/v1/drops/' + dropId
    And header X-Gateway-Secret = gatewaySecret
    When method get
    Then status 200

    # Drop Service에서 첫 번째 구매자가 상품을 선점해 만료 대상 hold를 생성
    Given path '/api/v1/drops/' + dropId + '/purchase'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user1Token
    When method post
    Then status 202
    * def expiredOrderId = response.data.orderId

    * eval java.lang.Thread.sleep(20000)
    # Drop Service에서 두 번째 구매자가 만료 후 복구된 재고를 선점할 수 있는지 확인
    Given path '/api/v1/drops/' + dropId + '/purchase'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user2Token
    When method post
    * def nextUserPurchaseStatus = responseStatus

    * print 'hold 만료 진단', { expiredOrderId: expiredOrderId, nextUserPurchaseStatus: nextUserPurchaseStatus }
    And match nextUserPurchaseStatus == 202
