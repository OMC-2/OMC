Feature: 드롭 관리 (Admin CRUD)

  # ================================================================
  # 드롭 Admin CRUD 시나리오
  # 실행: bash e2e/run.sh drop/drop_admin_crud
  #
  # 시나리오 목록:
  #   1. [정상] ADMIN이 드롭 생성 → 201, dropId 반환
  #   2. [예외] USER로 드롭 생성 → 403
  #   3. [예외] 토큰 없이 드롭 생성 → 401
  #   4. [정상] SCHEDULED 드롭 수정 → 200, 변경 내용 반영
  #   5. [예외] 존재하지 않는 dropId 수정 → 404 + DROP-001
  #   6. [정상] SCHEDULED 드롭 삭제 → 204
  #
  # 주의: Background에서 ADMIN 계정과 USER 계정을 각각 생성하고 로그인한다.
  #       드롭의 productId는 드롭 서비스가 생성 시 유효성을 검사하지 않으므로
  #       임의의 UUID를 사용한다.
  # ================================================================

  Background:
    * url baseUrl
    * def adminEmail = 'e2e-drop-admin-' + java.util.UUID.randomUUID() + '@example.com'
    * def userEmail  = 'e2e-drop-user-'  + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'

    # 사전 준비 1: ADMIN 계정 생성
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: #(adminEmail), password: #(testPassword), nickname: 'e2edropadmin' }
    When method post
    Then status 201

    # 사전 준비 2: ADMIN 로그인
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(adminEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def adminToken = response.data.accessToken

    # 사전 준비 3: USER 계정 생성
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword), nickname: 'e2edropuser' }
    When method post
    Then status 201

    # 사전 준비 4: USER 로그인
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(userEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def userToken = response.data.accessToken

    # 드롭 요청 바디 (startAt: 내일, endAt: 모레 — SCHEDULED 상태 유지)
    * def fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    * def startAt = java.time.LocalDateTime.now().plusDays(1).format(fmt)
    * def endAt   = java.time.LocalDateTime.now().plusDays(2).format(fmt)
    * def productId = java.util.UUID.randomUUID().toString()
    * def dropBody = { productId: #(productId), startAt: #(startAt), endAt: #(endAt), totalQty: 100, holdTtlSec: 300 }

  # ----------------------------------------------------------------
  # 시나리오 1: ADMIN 계정으로 드롭을 생성하면 201과 dropId를 반환한다
  # ----------------------------------------------------------------
  Scenario: [정상] ADMIN 드롭 생성 → 201, dropId 반환
    Given path '/api/v1/admin/drops'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request dropBody
    When method post
    Then status 201
    And match response.data.dropId == '#uuid'
    And match response.data.status == 'SCHEDULED'
    And match response.data.totalQty == 100
    And match response.data.holdTtlSec == 300

  # ----------------------------------------------------------------
  # 시나리오 2: USER 토큰으로 드롭 생성 시 403을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] USER 토큰으로 드롭 생성 → 403
    Given path '/api/v1/admin/drops'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + userToken
    And request dropBody
    When method post
    Then status 403

  # ----------------------------------------------------------------
  # 시나리오 3: 토큰 없이 드롭 생성 시 401을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 토큰 없이 드롭 생성 → 401
    Given path '/api/v1/admin/drops'
    And header X-Gateway-Secret = gatewaySecret
    And request dropBody
    When method post
    Then status 401

  # ----------------------------------------------------------------
  # 시나리오 4: SCHEDULED 상태의 드롭을 수정하면 200과 변경 내용을 반환한다
  # ----------------------------------------------------------------
  Scenario: [정상] SCHEDULED 드롭 수정 → 200, 변경 내용 반영
    # 드롭 생성
    Given path '/api/v1/admin/drops'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request dropBody
    When method post
    Then status 201
    * def dropId = response.data.dropId

    # 수정 요청
    * def updateBody = { startAt: #(startAt), endAt: #(endAt), totalQty: 200, holdTtlSec: 600 }
    Given path '/api/v1/admin/drops/' + dropId
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request updateBody
    When method put
    Then status 200
    And match response.data.totalQty == 200
    And match response.data.holdTtlSec == 600

  # ----------------------------------------------------------------
  # 시나리오 5: 존재하지 않는 dropId 수정 시 404와 DROP-001을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 존재하지 않는 dropId 수정 → 404 + DROP-001
    * def updateBody = { startAt: #(startAt), endAt: #(endAt), totalQty: 100, holdTtlSec: 300 }
    Given path '/api/v1/admin/drops/' + java.util.UUID.randomUUID()
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request updateBody
    When method put
    Then status 404
    And match response.errorCode == 'DROP-001'

  # ----------------------------------------------------------------
  # 시나리오 6: SCHEDULED 상태의 드롭을 삭제하면 204를 반환한다
  # ----------------------------------------------------------------
  Scenario: [정상] SCHEDULED 드롭 삭제 → 204
    # 드롭 생성
    Given path '/api/v1/admin/drops'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request dropBody
    When method post
    Then status 201
    * def dropId = response.data.dropId

    # 삭제
    Given path '/api/v1/admin/drops/' + dropId
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    When method delete
    Then status 204

    # 삭제 후 조회 시 404
    Given path '/api/v1/drops/' + dropId
    And header X-Gateway-Secret = gatewaySecret
    When method get
    Then status 404
    And match response.errorCode == 'DROP-001'
