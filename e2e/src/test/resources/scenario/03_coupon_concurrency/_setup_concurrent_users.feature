Feature: Setup - 동시성 테스트용 사전 설정

  # ================================================================
  # karate.callSingle() 로 호출 → JVM 세션 내 한 번만 실행, 결과 캐시
  # ADMIN + 유저 50명을 미리 생성해 시나리오 실행 중 Keycloak 호출을 없앤다.
  # 반환값: { adminToken, tokens: [50개] }
  # ================================================================

  Scenario: ADMIN + 유저 50명 사전 생성
    * url baseUrl

    # ADMIN 생성 + 로그인
    * def adminEmail = 'e2e-concurrent-admin-' + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: #(adminEmail), password: #(testPassword), nickname: 'e2econcurrentAdmin' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(adminEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def adminToken = response.data.accessToken

    # 유저 50명 생성 (100ms 딜레이 포함)
    * def userData = karate.repeat(50, function(i){ return {} })
    * def users = karate.call('classpath:scenario/03_coupon_concurrency/_create_user.feature', userData)
    * def tokens = karate.map(users, function(u){ return u.accessToken })
