Feature: [시나리오] 비로그인 접근 차단

  # ================================================================
  # 인증 토큰 없이 보호된 API 호출 시 401 반환 검증 (대표 케이스)
  # 실행: bash e2e/run.sh scenario/06_auth_errors/01_unauthenticated
  #
  # 시나리오 목록:
  #   1. [예외] 비로그인 쿠폰 발급 시도 → 401
  #
  # 주의: 인증 미통과 시 리소스 존재 여부와 무관하게 401 반환.
  #       리소스 사전 생성 불필요 — 임의 UUID 사용.
  #       서비스별 전수 검증은 coupon_security.feature 등에서 담당.
  # ================================================================

  Background:
    * url baseUrl

  # ----------------------------------------------------------------
  # 시나리오 1: Authorization 헤더 없이 쿠폰 발급 시도 시 401을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 비로그인 쿠폰 발급 시도 → 401
    * def fakeCouponId = java.util.UUID.randomUUID()
    Given path '/api/v1/coupons/' + fakeCouponId + '/issue'
    And header X-Gateway-Secret = gatewaySecret
    When method post
    Then status 401
