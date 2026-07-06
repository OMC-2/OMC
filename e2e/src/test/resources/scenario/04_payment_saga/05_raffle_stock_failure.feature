Feature: [시나리오] 래플 당첨자 재고 차감 실패 → 결제 승인 취소 보상

  # ================================================================
  # 래플 흐름으로 재현하는 "재고 차감 실패 → 결제 승인 취소 보상" 시나리오
  # 실행: bash e2e/run.sh scenario/04_payment_saga/05_raffle_stock_failure
  #
  # 배경:
  #   02_stock_failure.feature(드롭 기반)는 DropStatusScheduler.getAvailableQty()가
  #   OPEN 전이 시 요청한 totalQty가 아니라 product-service의 실시간 재고로 Redis를
  #   워밍하기 때문에, 애초에 실제 재고보다 많은 선점이 Drop 레벨에서 불가능해서
  #   재현할 수 없다 (@ignore 처리됨).
  #
  #   반면 래플 추첨(RaffleDrawService.drawRaffle())은 winnerCount와 응모자 수만으로
  #   당첨자를 정하고, 실제 상품 재고를 사전에 확인하지 않는다. 따라서
  #   winnerCount를 실제 재고보다 많게 설정하면, 당첨자 중 일부는 실제 재고 차감
  #   단계(product-service)에서 반드시 실패하게 되어 이 시나리오를 재현할 수 있다.
  #
  # 검증 흐름:
  #   Product: 재고 1개 상품 생성
  #   Raffle: winnerCount 2로 래플 생성 → OPEN
  #   User1, User2: 응모 (쿠폰 + 빌링키)
  #   관리자: 추첨 실행 → 응모자 2명 전원 당첨 (winnerCount=2, 응모자=2)
  #   Order: raffle.winner.selected 소비 → 주문 생성(RAFFLE) → order.created 발행 (2건)
  #   Payment: order.created 소비 → confirmBillingPayment() 자동 실행 (2건)
  #   Product: payment.completed 소비 → 재고 차감 시도
  #     → 1건은 성공 (STOCK_DEDUCTED), 1건은 재고 부족으로 실패 (STOCK_FAILED)
  #   Payment: stock.failed 소비 → 해당 결제 승인 취소 (CancellationCode.STOCK_DEDUCT_FAILED)
  #
  # 검증 포인트:
  #   1. 당첨자 2명의 결제 상태를 폴링해서, 정확히 1건은 PAID, 1건은 CANCELED가
  #      되는지 확인 (둘 다 PAID이거나 둘 다 CANCELED면 실패 — 재고 정합성이 깨진 것)
  #   2. CANCELED된 유저가 주문취소/결제실패 알림을 수신하는지 확인
  #      (PaymentResponse에는 취소 사유 필드가 없어 알림으로 간접 확인)
  #
  # 주의:
  #   - order-service의 OrderCreatedEvent(RAFFLE 타입)에도 productId가 필요하다.
  #     이 필드가 없으면(2026-07-02 기준 미병합 버그) 당첨자 결제가 전부 실패해서
  #     이 시나리오도 실패한다 — 03_auto_payment_chain.feature와 동일한 원인.
  #   - Kafka 체인 완료까지 최대 60초(30회 × 2초) 폴링한다.
  # ================================================================

  Background:
    * url baseUrl
    * def fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    * def adminEmail = 'e2e-raffle-stockfail-admin-' + java.util.UUID.randomUUID() + '@example.com'
    * def user1Email = 'e2e-raffle-stockfail-user1-' + java.util.UUID.randomUUID() + '@example.com'
    * def user2Email = 'e2e-raffle-stockfail-user2-' + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'

    # ── 1. ADMIN 계정 생성 + 로그인 ──────────────────────────────────
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: #(adminEmail), password: #(testPassword), nickname: 'rafflestockfailadmin' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(adminEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def adminToken = response.data.accessToken

    # ── 2. USER1, USER2 계정 생성 + 로그인 ───────────────────────────
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(user1Email), password: #(testPassword), nickname: 'rafflestockfailuser1' }
    When method post
    Then status 201
    * def user1Id = response.data.userId

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(user1Email), password: #(testPassword) }
    When method post
    Then status 200
    * def user1Token = response.data.accessToken

    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(user2Email), password: #(testPassword), nickname: 'rafflestockfailuser2' }
    When method post
    Then status 201
    * def user2Id = response.data.userId

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(user2Email), password: #(testPassword) }
    When method post
    Then status 200
    * def user2Token = response.data.accessToken

    # ── 3. 상품 등록 (실제 재고 1개 — 당첨자 2명보다 적게) ─────────────
    Given path '/api/v1/admin/products'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '래플 재고부족 테스트 상품', description: 'SAGA 검증용', price: 10000, brand: 'TestBrand', category: 'Sneakers', imageUrl: 'http://img.test/raffle-stock-failure.jpg', initialQuantity: 1 }
    When method post
    Then status 201
    * def productId = response.data.productId

    # ── 4. 래플 생성 (winnerCount=2, 응모자 2명 전원 당첨되게) ─────────
    * def dropId = java.util.UUID.randomUUID().toString()
    * def startedAt = java.time.LocalDateTime.now().plusSeconds(3).format(fmt)
    * def endedAt = java.time.LocalDateTime.now().plusDays(1).format(fmt)

    Given path '/api/v1/admin/raffles'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { dropId: #(dropId), productId: #(productId), name: '재고부족 SAGA 검증 래플', winnerCount: 2, startedAt: #(startedAt), endedAt: #(endedAt) }
    When method post
    Then status 201
    * def raffleId = response.data.raffleId

    # ── 5. 래플 상태를 OPEN으로 강제 변경 ────────────────────────────
    Given path '/api/v1/admin/raffles/' + raffleId + '/status'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { status: 'OPEN' }
    When method post
    Then status 200

  Scenario: [래플] 당첨자 2명 중 1명만 재고 차감 성공, 나머지 1명은 재고부족으로 결제 취소
    # STEP 1: USER1, USER2 응모 (빌링키, 선결제 개념)
    #   쿠폰은 이 시나리오의 검증 대상이 아니므로 발급하지 않고 couponId: null로 진행한다
    #   (실제 발급되지 않은 랜덤 UUID를 couponId로 넣으면 payment-service가
    #    coupon-service에 존재하지 않는 쿠폰을 조회하려다 실패해, 결제가 PENDING에
    #    멈춘 채 PAID/CANCELED로 전이되지 못하고 폴링이 타임아웃된다 — 2026-07-05 확인)
    * def billingKey1 = java.util.UUID.randomUUID().toString()
    * def billingKey2 = java.util.UUID.randomUUID().toString()

    Given path '/api/v1/raffles/' + raffleId + '/entries'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user1Token
    And request { userId: #(user1Id), billingKeyId: #(billingKey1), couponId: null, originalAmount: 10000, discountAmount: 0, finalAmount: 10000 }
    When method post
    Then status 201

    Given path '/api/v1/raffles/' + raffleId + '/entries'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user2Token
    And request { userId: #(user2Id), billingKeyId: #(billingKey2), couponId: null, originalAmount: 10000, discountAmount: 0, finalAmount: 10000 }
    When method post
    Then status 201

    # STEP 2: 관리자 수동 추첨 (응모자 2명, winnerCount 2 → 전원 당첨)
    Given path '/api/v1/admin/raffles/' + raffleId + '/draw'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 200

    # STEP 3: Kafka 체인(order.created → payment.completed → 재고차감 → (stock.failed → 취소))이
    #   끝날 시간을 고정 대기로 확보한 뒤 최종 상태를 한 번만 조회한다.
    #   주의: 결제 승인 직후에는 재고 확정 전에도 PAID로 먼저 세팅되고, 재고 부족 시에만
    #   나중에 CANCELED로 전이된다. 즉 PAID는 최종 상태가 아니므로 "PAID 또는 CANCELED가
    #   보이면 종료"하는 조건부 재시도로는 재고 차감이 끝나기 전에 폴링이 멈춰버려
    #   오탐(두 명 다 PAID로 보임)이 발생한다 (2026-07-05 확인). 01_payment_failure.feature와
    #   동일하게 고정 대기 방식을 사용한다.
    * eval java.lang.Thread.sleep(20000)

    Given path '/api/v1/payments/me'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user1Token
    When method get
    Then status 200
    * def user1Payments = response.data.content
    * def user1Status = user1Payments[0].paymentStatus

    Given path '/api/v1/payments/me'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user2Token
    When method get
    Then status 200
    * def user2Payments = response.data.content
    * def user2Status = user2Payments[0].paymentStatus

    * def paidCount = (user1Status == 'PAID' ? 1 : 0) + (user2Status == 'PAID' ? 1 : 0)
    * def canceledCount = (user1Status == 'CANCELED' ? 1 : 0) + (user2Status == 'CANCELED' ? 1 : 0)

    * print '래플 재고부족 진단', { user1Status: user1Status, user2Status: user2Status }

    # 정확히 1명 PAID, 1명 CANCELED여야 함 (재고 1개 vs 당첨자 2명)
    And match paidCount == 1
    And match canceledCount == 1

    # CANCELED된 유저는 결제/주문 취소 관련 알림을 받아야 함
    * def canceledUserToken = user1Status == 'CANCELED' ? user1Token : user2Token
    * configure retry = { count: 15, interval: 2000 }
    Given path '/api/v1/notifications'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + canceledUserToken
    And retry until karate.filter(response.data.content, function(n){ return n.notificationType == 'ORDER_CANCELLED' || n.notificationType == 'PAYMENT_FAILED' }).length > 0
    When method get
    Then status 200
    * def cancelNotifications = karate.filter(response.data.content, function(n){ return n.notificationType == 'ORDER_CANCELLED' || n.notificationType == 'PAYMENT_FAILED' })
    And assert cancelNotifications.length > 0
