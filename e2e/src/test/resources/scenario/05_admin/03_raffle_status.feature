Feature: 관리자 래플 상태 강제 변경 시나리오

  # ================================================================
  # 관리자가 래플의 상태를 SCHEDULED -> OPEN -> CLOSED 등으로 강제 전환하는 검증
  # 실행: bash e2e/run.sh scenario/05_admin/03_raffle_status
  #
  # 검증 흐름:
  #   상품 등록 → 드롭 생성 → 래플 생성(SCHEDULED)
  #   → 상태 강제 변경 OPEN → 상태 강제 변경 CLOSED
  #
  # 검증 포인트:
  #   1. 관리자 API로 래플 상태를 SCHEDULED → OPEN으로 강제 전환 가능
  #   2. 관리자 API로 래플 상태를 OPEN → CLOSED로 강제 전환 가능
  # ================================================================

  Background:
    * url baseUrl
    # 공통 인증 호출하여 관리자 토큰 확보
    * def auth = call read('admin-auth.feature')
    * def adminToken = auth.accessToken

    # 사전 준비: 상품 -> 드롭 -> 래플 생성
    # [단계 1] 상품 등록
    Given path '/api/v1/admin/products'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { "name": "상태 변경 테스트 상품", "price": 100000, "brand": "Test", "category": "디바이스", "initialQuantity": 10 }
    When method post
    Then status 201
    * def productId = response.data.productId

    # [단계 2] 드롭 생성
    Given path '/api/v1/admin/drops'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { "productId": "#(productId)", "startAt": "2099-12-01T10:00:00", "endAt": "2099-12-01T18:00:00", "totalQty": 10, "holdTtlSec": 600 }
    When method post
    Then status 201
    * def dropId = response.data.dropId

    # [3단계] 래플 생성 (기본 상태: SCHEDULED)
    Given path '/api/v1/admin/raffles'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { "dropId": "#(dropId)", "productId": "#(productId)", "name": "상태 관리 래플", "winnerCount": 2, "startedAt": "2099-12-01T10:00:00", "endedAt": "2099-12-01T18:00:00" }
    When method post
    Then status 201
    And match response.data.status == 'SCHEDULED'
    * def raffleId = response.data.raffleId

  Scenario: [관리자] 래플 상태를 SCHEDULED에서 OPEN으로, 다시 CLOSED로 강제 변경한다
    # [1단계] OPEN 상태로 강제 변경 (예시 API: PATCH /api/v1/admin/raffles/{id}/status)
    Given path '/api/v1/admin/raffles/' + raffleId + '/status'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { "status": "OPEN" }
    When method post
    Then status 200
    And match response.success == true

    # [2단계] CLOSED 상태로 강제 변경
    Given path '/api/v1/admin/raffles/' + raffleId + '/status'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { "status": "CLOSED" }
    When method post
    Then status 200
    And match response.success == true