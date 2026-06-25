@ignore
Feature: Helper - 유저 생성

  # ================================================================
  # karate.parallel() 에서 호출되는 헬퍼 피처
  # 유저 1명을 생성하고 accessToken 을 반환한다.
  # ================================================================

  Scenario: 유저 생성 후 accessToken 반환
    * url baseUrl
    * def email = 'e2e-concurrent-' + java.util.UUID.randomUUID() + '@example.com'
    * def pwd = 'password123'

    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(email), password: #(pwd), nickname: 'e2econcurrent' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(email), password: #(pwd) }
    When method post
    Then status 200
    * def accessToken = response.data.accessToken

    # Keycloak 과부하 방지: 연속 생성 시 100ms 대기
    * eval java.lang.Thread.sleep(100)
