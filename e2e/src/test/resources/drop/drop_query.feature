Feature: 드롭 조회

  # ================================================================
  # 드롭 조회 시나리오
  # 실행: bash e2e/run.sh drop/drop_query
  #
  # 시나리오 목록:
  #   1. [정상] 드롭 목록 조회 → 200, 페이지 응답
  #   2. [정상] status 필터로 드롭 목록 조회 → 200, 해당 상태만 반환
  #   3. [정상] dropId로 드롭 단건 조회 → 200, 필드 확인
  #   4. [예외] 존재하지 않는 dropId 단건 조회 → 404 + DROP-001
  #
  # 주의: 드롭 목록 조회는 인증이 필요 없다.
  #       Background에서 ADMIN 계정을 만들고 드롭 1개를 생성해 두고 시작한다.
  # ================================================================

  Background:
    * url baseUrl
    * def adminEmail = 'e2e-dropq-admin-' + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'

    # 사전 준비 1: ADMIN 계정 생성 + 로그인
    Given path '/api/v1/users/admin/signup'
    And header X-Gateway-Secret = gatewaySecret
    And header X-Admin-Secret = adminSecret
    And request { email: #(adminEmail), password: #(testPassword), nickname: 'e2edropqadmin' }
    When method post
    Then status 201

    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(adminEmail), password: #(testPassword) }
    When method post
    Then status 200
    * def adminToken = response.data.accessToken

    # 사전 준비 2: SCHEDULED 드롭 1개 생성
    * def fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    * def startAt = java.time.LocalDateTime.now().plusDays(1).format(fmt)
    * def endAt   = java.time.LocalDateTime.now().plusDays(2).format(fmt)
    * def productId = java.util.UUID.randomUUID().toString()

    Given path '/api/v1/admin/drops'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + adminToken
    And request { productId: #(productId), startAt: #(startAt), endAt: #(endAt), totalQty: 100, holdTtlSec: 300 }
    When method post
    Then status 201
    * def dropId = response.data.dropId

  # ----------------------------------------------------------------
  # 시나리오 1: 드롭 목록을 조회하면 200과 페이지 응답을 반환한다
  # ----------------------------------------------------------------
  Scenario: [정상] 드롭 목록 조회 → 200, 페이지 응답
    Given path '/api/v1/drops'
    And header X-Gateway-Secret = gatewaySecret
    When method get
    Then status 200
    And match response.data.content == '#array'
    And match response.data.totalElements == '#number'

  # ----------------------------------------------------------------
  # 시나리오 2: status 파라미터로 드롭 목록을 필터링한다
  # ----------------------------------------------------------------
  Scenario: [정상] status=SCHEDULED 필터 조회 → SCHEDULED 드롭만 반환
    Given path '/api/v1/drops'
    And header X-Gateway-Secret = gatewaySecret
    And param status = 'SCHEDULED'
    When method get
    Then status 200
    And match each response.data.content[*].status == 'SCHEDULED'

  # ----------------------------------------------------------------
  # 시나리오 3: dropId로 드롭 단건을 조회한다
  # ----------------------------------------------------------------
  Scenario: [정상] dropId로 단건 조회 → 200, 필드 확인
    Given path '/api/v1/drops/' + dropId
    And header X-Gateway-Secret = gatewaySecret
    When method get
    Then status 200
    And match response.data.dropId == dropId
    And match response.data.status == 'SCHEDULED'
    And match response.data.totalQty == 100

  # ----------------------------------------------------------------
  # 시나리오 4: 존재하지 않는 dropId 조회 시 404와 DROP-001을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 존재하지 않는 dropId 조회 → 404 + DROP-001
    Given path '/api/v1/drops/' + java.util.UUID.randomUUID()
    And header X-Gateway-Secret = gatewaySecret
    When method get
    Then status 404
    And match response.errorCode == 'DROP-001'
