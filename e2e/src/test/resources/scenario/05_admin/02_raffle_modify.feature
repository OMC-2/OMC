Feature: 오픈 전 래플 수정 및 삭제

  # ================================================================
  # 래플이 OPEN되기 전(SCHEDULED)에만 수정/삭제가 허용되는지 검증
  # 실행: bash e2e/run.sh scenario/05_admin/02_raffle_modify
  #
  # 검증 흐름:
  #   상품 등록 → 드롭 생성(래플 연결용) → 래플 생성(SCHEDULED)
  #   → 래플 수정(name, winnerCount) → 래플 삭제
  #
  # 검증 포인트:
  #   1. 래플은 드롭에 연결되어야 생성 가능
  #   2. SCHEDULED 상태의 래플은 name, winnerCount 수정 가능
  #   3. SCHEDULED 상태의 래플은 삭제 가능
  # ================================================================

  Background:
    * url baseUrl
    * def auth = call read('admin-auth.feature')
    * def adminToken = auth.accessToken

  Scenario: [관리자] 래플 생성 → 수정 → 삭제 (오픈 전)
    # [1단계] 상품 등록
    Given path '/api/v1/admin/products'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request
      """
      {
        "name": "래플 테스트 상품",
        "price": 689000,
        "brand": "Nintendo",
        "category": "게이밍 기기",
        "initialQuantity": 10
      }
      """
    When method post
    Then status 201
    * def productId = response.data.productId

    # [2단계] 드롭 생성 (래플 연결용)
    Given path '/api/v1/admin/drops'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request
      """
      {
        "productId": "#(productId)",
        "startAt": "2099-12-01T10:00:00",
        "endAt": "2099-12-01T18:00:00",
        "totalQty": 10,
        "holdTtlSec": 600
      }
      """
    When method post
    Then status 201
    * def dropId = response.data.dropId

    # [3단계] 래플 생성
    Given path '/api/v1/admin/raffles'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request
      """
      {
        "dropId": "#(dropId)",
        "productId": "#(productId)",
        "name": "Nintendo Switch 2 래플",
        "winnerCount": 10,
        "startedAt": "2099-12-01T10:00:00",
        "endedAt": "2099-12-01T18:00:00"
      }
      """
    When method post
    Then status 201
    And match response.data.status == 'SCHEDULED'
    * def raffleId = response.data.raffleId

    # [4단계] 래플 수정 (오픈 전 — name, winnerCount만 수정 가능)
    Given path '/api/v1/admin/raffles/' + raffleId
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request
      """
      {
        "name": "수정된 Nintendo Switch 2 래플",
        "winnerCount": 5
      }
      """
    When method put
    Then status 200
    And match response.success == true

    # [5단계] 래플 삭제 (오픈 전)
    Given path '/api/v1/admin/raffles/' + raffleId
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    When method delete
    Then status 200
    And match response.success == true