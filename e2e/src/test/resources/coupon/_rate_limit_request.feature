@ignore
Feature: Helper - Rate Limit 테스트용 쿠폰 발급 단건 요청

  # ================================================================
  # karate.repeat() 에서 호출되는 헬퍼 피처
  # 입력 변수: token (유저 accessToken), couponId (발급 대상 쿠폰 UUID)
  # 반환 변수: issueStatus (HTTP 상태코드), issueResponse (응답 바디)
  # ================================================================

  Scenario: 쿠폰 발급 시도 후 상태 코드 및 응답 바디 반환
    Given url baseUrl
    And path '/api/v1/coupons/' + couponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + token
    When method post
    * def issueStatus = responseStatus
    * def issueResponse = response
