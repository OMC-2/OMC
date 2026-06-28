Feature: [시나리오] 권한 오류

  # ================================================================
  # 권한 없는 역할로 제한된 API 호출 시 403 반환 검증 (대표 케이스)
  # 실행: bash e2e/run.sh scenario/06_auth_errors/02_unauthorized
  #
  # 시나리오 목록:
  #   1. [예외] 일반유저가 관리자 전용 쿠폰 생성 API 호출 → 403
  #   2. [예외] ADMIN이 USER 전용 쿠폰 발급 API 호출 → 403
  #
  # 주의: 서비스별 전수 검증은 coupon_security.feature 등에서 담당.
  #       여기선 "역할이 다르면 막힌다"는 흐름 시연용.
  #       X-Gateway-Secret 우회 검증은 마이크로서비스 포트가 외부 미노출이라
  #       E2E 환경에서 테스트 불가 → 서비스 통합 테스트에서 담당.
  # ================================================================

  Background:
    * url baseUrl
    * def adminEmail = 'e2e-unauth-admin-' + java.util.UUID.randomUUID() + '@example.com'
    * def userEmail  = 'e2e-unauth-user-'  + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'

    # ADMIN 계정 생성 + 로그인
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: #(adminEmail), password: #(testPassword), nickname: 'e2eunauthAdmin' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(adminEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def adminToken = response.data.accessToken

    # USER 계정 생성 + 로그인
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword), nickname: 'e2eunauthUser' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def userToken = response.data.accessToken

    # 쿠폰 생성 (시나리오 2, 3의 호출 대상 리소스)
    Given path '/api/v1/coupons'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { name: '권한 오류 테스트 쿠폰', discountType: 'AMOUNT', discountValue: 1000, totalQuantity: 100, startedAt: '2020-01-01T00:00:00', expiredAt: '2099-12-31T23:59:59' }
    When method post
    Then status 201
    * def couponId = response.data.couponId

  # ----------------------------------------------------------------
  # 시나리오 1: USER 토큰으로 ADMIN 전용 쿠폰 생성 시 403을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 일반유저가 관리자 API 호출 → 403
    Given path '/api/v1/coupons'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    And request { name: '침범 쿠폰', discountType: 'AMOUNT', discountValue: 500, totalQuantity: 10, startedAt: '2020-01-01T00:00:00', expiredAt: '2099-12-31T23:59:59' }
    When method post
    Then status 403

  # ----------------------------------------------------------------
  # 시나리오 2: ADMIN 토큰으로 USER 전용 쿠폰 발급 시 403을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] ADMIN이 USER 전용 API 호출 → 403
    Given path '/api/v1/coupons/' + couponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 403
