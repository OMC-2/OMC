@ignore
Feature: 관리자 인증 공통 모듈
  Scenario: 관리자 로그인 및 토큰 발급
    * url baseUrl
    * def adminEmail = 'admin@omc.com'
    * def adminPassword = 'password123'

    # 관리자 가입 시도
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: #(adminEmail), password: #(adminPassword), nickname: '운영관리자' }
    When method post

    # 관리자 로그인
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(adminEmail), password: #(adminPassword) }
    When method post
    Then status 200
    * def accessToken = response.data.accessToken