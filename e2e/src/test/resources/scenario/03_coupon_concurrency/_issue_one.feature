@ignore
Feature: Helper - 쿠폰 발급 1건

  # ================================================================
  # karate.parallel() 에서 호출되는 헬퍼 피처
  # 입력 변수: token (유저 accessToken), couponId (발급 대상 쿠폰 UUID)
  # 반환 변수: issueStatus (HTTP 상태코드), issueErrorCode (실패 시 에러코드)
  # ================================================================

  Scenario: 쿠폰 발급 시도 후 결과 반환
    Given url baseUrl
    And path '/api/v1/coupons/' + couponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + token
    When method post
    * def issueStatus = responseStatus
    * def issueErrorCode = responseStatus != 201 ? response.errorCode : null
