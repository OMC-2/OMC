Feature: 오픈 전 드롭 수정 및 삭제

  Background:
    * url baseUrl
    # 공통 인증 호출하여 토큰 확보
    * def auth = call read('admin-auth.feature')
    * def adminToken = auth.accessToken

  # ================================================================
  # 시나리오 1: 드롭 생성 → 수정 → 삭제
  # 활성 드롭 존재 시 수정 제한
  # ================================================================
  Scenario: 드롭 생성, 수정, 활성 드롭 존재 시 상품 수정 제한, 드롭 삭제 검증
    # [1단계] 상품 등록 및 재고 확인 (기존 시나리오 1 + 2 결합)
    Given path '/api/v1/admin/products'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request
      """
      {
        "name": "드롭 테스트 상품",
        "price": 689000,
        "brand": "Nintendo",
        "category": "게이밍 기기",
        "initialQuantity": 50
      }
      """
    When method post
    Then status 201
    * def productId = response.data.productId

    # [2단계] 드롭 생성 (SCHEDULED)
    Given path '/api/v1/admin/drops'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request
      """
      {
        "productId": "#(productId)",
        "startAt": "2099-12-01T10:00:00",
        "endAt": "2099-12-01T12:00:00",
        "totalQty": 50,
        "holdTtlSec": 600
      }
      """
    When method post
    Then status 201
    And match response.data.status == 'SCHEDULED'
    * def dropId = response.data.dropId

    # [3단계] 드롭 수정 (오픈 전 수정 가능)
    Given path '/api/v1/admin/drops/' + dropId
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request
      """
      {
        "startAt": "2099-12-02T10:00:00",
        "endAt": "2099-12-02T12:00:00",
        "totalQty": 50,
        "holdTtlSec": 600
      }
      """
    When method put
    Then status 200

    # [4단계] 드롭 SCHEDULED 상태에서 상품 수정 시도 → 409 (ACTIVE_DROP_EXISTS)
    # SCHEDULED, OPEN 모두 활성 드롭으로 판단
    Given path '/api/v1/admin/products/' + productId
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { "price": 600000 }
    When method patch
    Then status 409

    # [5단계] 드롭 삭제 (오픈 전 삭제 가능)
    Given path '/api/v1/admin/drops/' + dropId
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    When method delete
    Then status 204

    # [6단계] 드롭 삭제 후 상품 수정 가능 확인 (활성 드롭 없음)
    Given path '/api/v1/admin/products/' + productId
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { "price": 600000 }
    When method patch
    Then status 200
    And match response.data.price == 600000