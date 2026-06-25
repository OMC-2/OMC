Feature: 관리자 응모자 목록 조회 및 수동 추첨

  Background:
    * url baseUrl
    # 공통 인증 호출하여 관리자 토큰 확보
    * def auth = call read('admin-auth.feature')
    * def adminToken = auth.accessToken

    # 사전 준비: 상품 → 드롭 → 래플 생성
    # [1단계] 상품 등록
    Given path '/api/v1/admin/products'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { "name": "추첨 테스트 상품", "price": 100000, "brand": "Test", "category": "디바이스", "initialQuantity": 5 }
    When method post
    Then status 201
    * def productId = response.data.productId

    # [2단계] 드롭 생성
    Given path '/api/v1/admin/drops'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { "productId": "#(productId)", "startAt": "2099-12-01T10:00:00", "endAt": "2099-12-01T18:00:00", "totalQty": 5, "holdTtlSec": 600 }
    When method post
    Then status 201
    * def dropId = response.data.dropId

    # [3단계] 래플 생성
    Given path '/api/v1/admin/raffles'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { "dropId": "#(dropId)", "productId": "#(productId)", "name": "추첨 테스트 래플", "winnerCount": 2, "startedAt": "2099-12-01T10:00:00", "endedAt": "2099-12-01T18:00:00" }
    When method post
    Then status 201
    And match response.data.status == 'SCHEDULED'
    * def raffleId = response.data.raffleId

  # ================================================================
  # 시나리오 1: 응모자 목록 조회
  # ================================================================
  Scenario: [관리자] 응모자 목록을 조회한다
    Given path '/api/v1/admin/raffles/' + raffleId + '/entries'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    When method get
    Then status 200
    And match response.success == true
    And match response.data.content == '#array'
    And match response.data.totalElements == '#number'

  # ================================================================
  # 시나리오 2: 수동 추첨 실행
  # OPEN 상태에서만 추첨 가능
  # ================================================================
  Scenario: [관리자] 래플 상태를 OPEN으로 변경 후 수동 추첨 실행
    # [1단계] OPEN 상태로 강제 변경
    Given path '/api/v1/admin/raffles/' + raffleId + '/status'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { "status": "OPEN" }
    When method post
    Then status 200
    And match response.success == true

    # [2단계] 수동 추첨 실행
    Given path '/api/v1/admin/raffles/' + raffleId + '/draw'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    When method post
    Then status 200
    And match response.success == true
