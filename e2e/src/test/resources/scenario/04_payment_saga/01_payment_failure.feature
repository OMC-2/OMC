Feature: [시나리오] 결제 수단 오류 보상 처리

  # ================================================================
  # 결제 수단 오류 이후 SAGA 보상 흐름 시나리오
  # 실행: bash e2e/run.sh scenario/04_payment_saga/01_payment_failure
  #
  # 목적:
  #   현재 협업 코드를 그대로 실행해 서비스 간 흐름이 끊기는 지점을 확인
  #   테스트를 통과시키기 위한 서비스 코드 수정이나 별도 테스트 API는 사용하지 않음
  #
  # 검증 흐름:
  #   Gateway: 회원가입 및 로그인 → JWT 발급
  #   Drop: 상품과 드롭 생성 → 구매 선점 → orderId 발급
  #   Order: purchase.confirmed 소비 → 주문 생성 → order.created 발행
  #   Payment: 결제 승인 또는 실패 → payment.completed/payment.failed 발행
  #   Product: payment.completed 소비 → 재고 차감 또는 stock.failed 발행
  #   Coupon: payment.completed/payment.failed/refund.done 소비 → 쿠폰 확정 또는 복원
  #   Notification: order.confirmed/order.cancelled 소비 → 사용자 알림 생성
  #
  # 검증 포인트:
  #   결제 실패 후 Payment가 FAILED이고 쿠폰과 드롭 선점이 복원되는지 확인
  #
  # 현재 진단 예상 지점:
  #   - Payment가 쿠폰 선점 API를 호출하고 RESERVED 응답으로 할인 금액을 검증
  #   - TestPaymentAdapter가 E2E 식별자로 카드 거절과 PG 연결 오류를 재현
  #   - 각 실패는 테스트 결함으로 숨기지 않고 실제 협업 코드의 보완 지점으로 확인
  #
  # 주의:
  #   - Gateway와 관련 서비스 및 PostgreSQL Redis Kafka가 모두 실행 중이어야 함
  #   - 비동기 이벤트 처리 완료를 위해 시나리오별로 최대 30초 대기
  #   - payment 내부 API는 paymentServiceUrl(8085)로 직접 호출
  # ================================================================

  Background:
    * url baseUrl
    * def fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    * def uniqueId = java.util.UUID.randomUUID().toString()
    * def testPassword = 'password123'
    * def adminEmail = 'e2e-saga-failure-admin-' + uniqueId + '@example.com'
    * def user1Email = 'e2e-saga-failure-user1-' + uniqueId + '@example.com'
    * def user2Email = 'e2e-saga-failure-user2-' + uniqueId + '@example.com'

    # User Service에 테스트용 관리자 계정을 생성
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: '#(adminEmail)', password: '#(testPassword)', nickname: 'sagafailureadmin' }
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
    And request { email: '#(user1Email)', password: '#(testPassword)', nickname: 'sagafailureuser1' }
    When method post
    Then status 201
    * def user1Id = response.data.userId

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
    And request { email: '#(user2Email)', password: '#(testPassword)', nickname: 'sagafailureuser2' }
    When method post
    Then status 201
    * def user2Id = response.data.userId

    # User Service에서 두 번째 구매자로 로그인해 JWT를 발급
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: '#(user2Email)', password: '#(testPassword)' }
    When method post
    Then status 200
    * def user2Token = response.data.accessToken

  Scenario: 카드 한도 초과 → 결제 실패 → hold 및 쿠폰 복원 → 실패 알림
    # Coupon Service에 결제 실패 복원 여부를 확인할 정액 쿠폰을 생성
    Given path '/api/v1/coupons'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '결제 실패 복원 쿠폰', discountType: 'AMOUNT', discountValue: 1000, totalQuantity: 10, startedAt: '2020-01-01T00:00:00', expiredAt: '2099-12-31T23:59:59' }
    When method post
    Then status 201
    * def couponId = response.data.couponId

    # Coupon Service에서 첫 번째 구매자에게 테스트 쿠폰을 발급 (비동기 202)
    Given path '/api/v1/coupons/' + couponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user1Token
    When method post
    Then status 202
    * java.lang.Thread.sleep(3000)
    Given path '/api/v1/coupons/me'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user1Token
    When method get
    Then status 200
    * def userCouponId = response.data.content[0].userCouponId

    # Product Service에 카드 실패 시나리오용 상품과 재고를 생성
    Given path '/api/v1/admin/products'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '카드 실패 테스트 상품', description: 'SAGA 진단용', price: 10000, brand: 'TestBrand', category: 'Sneakers', imageUrl: 'http://img.test/card-failure.jpg', initialQuantity: 5 }
    When method post
    Then status 201
    * def productId = response.data.productId

    * def startAt = java.time.LocalDateTime.now().plusSeconds(5).format(fmt)
    * def endAt = java.time.LocalDateTime.now().plusDays(1).format(fmt)
    # Drop Service에 카드 실패 시나리오용 선착순 드롭을 생성
    Given path '/api/v1/admin/drops'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { productId: '#(productId)', startAt: '#(startAt)', endAt: '#(endAt)', totalQty: 1, holdTtlSec: 60 }
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

    # Drop Service에서 첫 번째 구매자가 상품을 선점하고 주문 ID를 발급
    Given path '/api/v1/drops/' + dropId + '/purchase'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user1Token
    When method post
    Then status 202
    * def orderId = response.data.orderId

    # PG Stub이 이 식별자를 카드 한도 초과로 처리한다는 계약을 기준으로 진단
    * url paymentServiceUrl
    # Payment Service 내부 API를 호출해 카드 한도 초과 실패를 강제로 발생
    Given path '/internal/v1/payments/confirm'
    And header X-User-Id = user1Id
    And request { orderID: '#(orderId)', dropId: '#(dropId)', productId: '#(productId)', providerPaymentId: 'E2E_CARD_LIMIT_EXCEEDED', couponID: '#(userCouponId)', originalAmount: 10000, discountAmount: 1000, finalAmount: 9000 }
    When method post
    * def paymentFailureStatus = responseStatus

    # Payment Service에서 첫 번째 구매자의 결제가 FAILED로 저장됐는지 조회 (최대 20초 폴링)
    * url baseUrl
    * configure retry = { count: 10, interval: 2000 }
    * retry until karate.filter(response.data.content, function(p){ return p.orderId == orderId && p.paymentStatus == 'FAILED' }).length > 0
    Given path '/api/v1/payments/me'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user1Token
    When method get
    Then status 200
    * def orderPayments = karate.filter(response.data.content, function(p){ return p.orderId == orderId })
    * def paymentStatuses = karate.map(orderPayments, function(p){ return p.paymentStatus })

    # Coupon Service에서 실패한 결제의 쿠폰이 AVAILABLE로 복구됐는지 조회 (최대 20초 폴링)
    * configure retry = { count: 10, interval: 2000 }
    * retry until response.data.status == 'AVAILABLE'
    Given path '/api/v1/coupons/me/' + userCouponId
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user1Token
    When method get
    Then status 200
    * def restoredCouponStatus = response.data.status

    # Notification Service에서 결제 실패와 주문 취소 알림을 조회 (ORDER_CANCELLED는 체인이 길어 최대 40초 폴링)
    * configure retry = { count: 20, interval: 2000 }
    * retry until karate.filter(response.data.content, function(n){ return n.referenceId == orderId && n.notificationType == 'ORDER_CANCELLED' }).length > 0
    Given path '/api/v1/notifications'
    And param size = 30
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user1Token
    When method get
    Then status 200
    * def notificationTypes = karate.map(karate.filter(response.data.content, function(n){ return n.referenceId == orderId }), function(n){ return n.notificationType })

    # Drop Service에서 두 번째 구매자가 복구된 재고를 다시 선점할 수 있는지 확인
    Given path '/api/v1/drops/' + dropId + '/purchase'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + user2Token
    When method post
    * def nextUserPurchaseStatus = responseStatus

    * print '카드 실패 진단', { paymentApiStatus: '#(paymentFailureStatus)', paymentStatuses: '#(paymentStatuses)', couponStatus: '#(restoredCouponStatus)', notifications: '#(notificationTypes)', nextUserPurchaseStatus: '#(nextUserPurchaseStatus)' }
    And match paymentFailureStatus == 400
    And match paymentStatuses contains 'FAILED'
    And match restoredCouponStatus == 'AVAILABLE'
    And match notificationTypes contains 'PAYMENT_FAILED'
    And match notificationTypes contains 'ORDER_CANCELLED'
    And match nextUserPurchaseStatus == 202

