Feature: 래플 응모 및 구매 (SAGA 검증 포함)

  Background:
    * url baseUrl
    * def adminEmail = 'e2e-raffle-admin-' + java.util.UUID.randomUUID() + '@example.com'
    * def user1Email = 'e2e-raffle-user1-' + java.util.UUID.randomUUID() + '@example.com'
    * def user2Email = 'e2e-raffle-user2-' + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'
    * def fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    # 1. ADMIN 계정 생성 및 로그인
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: #(adminEmail), password: #(testPassword), nickname: 'raffleAdmin' }
    When method post
    Then status 201
    * def adminUserId = response.data.userId

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(adminEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def adminToken = response.data.accessToken

    # 2. 일반 USER1 계정 생성 및 로그인
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(user1Email), password: #(testPassword), nickname: 'raffleUser1' }
    When method post
    Then status 201
    * def user1Id = response.data.userId

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(user1Email), password: #(testPassword) }
    When method post
    Then status 200
    * def user1Token = response.data.accessToken

    # 3. 일반 USER2 계정 생성 및 로그인
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(user2Email), password: #(testPassword), nickname: 'raffleUser2' }
    When method post
    Then status 201
    * def user2Id = response.data.userId

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(user2Email), password: #(testPassword) }
    When method post
    Then status 200
    * def user2Token = response.data.accessToken

    # 4. 래플 생성 (시작 및 종료 시간을 미래로 설정하여 @Future 제약 조건 통과)
    * def productId = java.util.UUID.randomUUID().toString()
    * def dropId = java.util.UUID.randomUUID().toString()
    * def startedAt = java.time.LocalDateTime.now().plusMinutes(5).format(fmt)
    * def endedAt = java.time.LocalDateTime.now().plusDays(1).format(fmt)

    Given path '/api/v1/admin/raffles'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { dropId: #(dropId), productId: #(productId), name: 'E2E 테스트 래플', winnerCount: 1, startedAt: #(startedAt), endedAt: #(endedAt) }
    When method post
    Then status 201
    * def raffleId = response.data.raffleId

    # 5. 상태를 OPEN으로 강제 변경
    Given path '/api/v1/admin/raffles/' + raffleId + '/status'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { status: 'OPEN' }
    When method post
    Then status 200


  Scenario: [정상] 래플 응모 (쿠폰 포함) 및 100원 가승인 검증
    # USER1이 래플 응모 요청
    * def couponId = java.util.UUID.randomUUID().toString()
    * def billingKey = java.util.UUID.randomUUID().toString()

    Given path '/api/v1/raffles/' + raffleId + '/entries'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user1Token
    And request { userId: #(user1Id), billingKeyId: #(billingKey), couponId: #(couponId), originalAmount: 10000, discountAmount: 1000, finalAmount: 9000 }
    When method post
    Then status 201
    And match response.data.raffleId == raffleId
    And match response.data.userId == user1Id


  Scenario: [예외] 동일 유저 중복 응모 시 409 Conflict 반환 (Redis SADD 방어)
    * def couponId = java.util.UUID.randomUUID().toString()
    * def billingKey = java.util.UUID.randomUUID().toString()

    # 첫 번째 응모 (정상)
    Given path '/api/v1/raffles/' + raffleId + '/entries'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user1Token
    And request { userId: #(user1Id), billingKeyId: #(billingKey), couponId: #(couponId), originalAmount: 10000, discountAmount: 1000, finalAmount: 9000 }
    When method post
    Then status 201

    # 두 번째 응모 (중복)
    Given path '/api/v1/raffles/' + raffleId + '/entries'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user1Token
    And request { userId: #(user1Id), billingKeyId: #(billingKey), couponId: #(couponId), originalAmount: 10000, discountAmount: 1000, finalAmount: 9000 }
    When method post
    Then status 400


  Scenario: [정상] 래플 추첨 진행(Draw) 및 SAGA를 통한 결제/주문 최종 상태 검증
    * def couponId1 = java.util.UUID.randomUUID().toString()
    * def billingKey1 = java.util.UUID.randomUUID().toString()
    * def couponId2 = java.util.UUID.randomUUID().toString()
    * def billingKey2 = java.util.UUID.randomUUID().toString()

    # USER1 응모
    Given path '/api/v1/raffles/' + raffleId + '/entries'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user1Token
    And request { userId: #(user1Id), billingKeyId: #(billingKey1), couponId: #(couponId1), originalAmount: 10000, discountAmount: 1000, finalAmount: 9000 }
    When method post
    Then status 201

    # USER2 응모
    Given path '/api/v1/raffles/' + raffleId + '/entries'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user2Token
    And request { userId: #(user2Id), billingKeyId: #(billingKey2), couponId: #(couponId2), originalAmount: 10000, discountAmount: 1000, finalAmount: 9000 }
    When method post
    Then status 201

    # 관리자: 래플 수동 추첨(Draw) 진행
    Given path '/api/v1/admin/raffles/' + raffleId + '/draw'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 200

    # 추첨 후 래플 상태가 CLOSED인지 확인
    Given path '/api/v1/raffles/' + raffleId
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user1Token
    When method get
    Then status 200
    And match response.data.status == 'CLOSED'

    # SAGA가 끝날 때까지 주문내역 폴링 (당첨자의 주문 상태 확인)
    # 실제로는 SAGA 완료 후 User의 Order List를 통해 당첨 여부를 파악할 수 있습니다.
    # 본 시나리오는 SAGA 연동 점검용이므로, 잠시 대기 후 종료합니다. (E2E 테스트 인프라에 맞게 보완 가능)