Feature: 주소 관리

  # ================================================================
  # 주소 CRUD + 기본 주소 설정 시나리오
  # 실행: bash e2e/run.sh user/address
  #
  # 시나리오 목록:
  #   1. [정상] 주소 등록 → 201, addressId 반환
  #   2. [정상] 주소 목록 조회 → 200, 페이징 응답
  #   3. [정상] 주소 단건 조회 → 200
  #   4. [정상] 주소 수정 → 200, 변경된 수령인 반환
  #   5. [정상] 기본 주소 설정 → 200, isDefault true
  #   6. [정상] 주소 삭제 → 200
  #   7. [예외] 토큰 없이 주소 등록 → 401
  #
  # 주의: 각 시나리오는 Background에서 계정 생성 → 로그인 → accessToken 확보 후
  #       시작된다. 주소 조회/수정/삭제 시나리오는 내부에서 주소를 먼저 등록한다.
  # ================================================================

  Background:
    * url baseUrl
    # 주소 테스트 전용 유니크 이메일 생성
    * def testEmail = 'e2e-address-' + java.util.UUID.randomUUID() + '@example.com'
    * def testPassword = 'password123'
    # 사전 준비 1: 테스트용 계정 생성
    Given path '/api/v1/users/signup'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(testEmail), password: #(testPassword), nickname: 'e2eaddressuser' }
    When method post
    Then status 201
    # 사전 준비 2: 로그인하여 accessToken 확보
    Given path '/api/v1/users/login'
    And header X-Gateway-Secret = gatewaySecret
    And request { email: #(testEmail), password: #(testPassword) }
    When method post
    Then status 200
    # 이후 시나리오에서 Authorization 헤더에 사용할 토큰 저장
    * def accessToken = response.data.accessToken
    # 주소 등록 시 사용할 기본 요청 바디
    * def addressBody = { recipientName: '홍길동', phone: '01012345678', zipCode: '12345', address: '서울시 강남구 테헤란로 1', addressDetail: '101호', isDefault: false }

  # ----------------------------------------------------------------
  # 시나리오 1: 주소를 등록하면 201과 함께 addressId를 반환한다
  # ----------------------------------------------------------------
  Scenario: [정상] 주소 등록 → 201, addressId 반환
    Given path '/api/v1/users/addresses'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + accessToken
    And request addressBody
    When method post
    Then status 201
    # '#uuid'는 Karate 내장 매처 — UUID 형식인지 검증
    And match response.data.addressId == '#uuid'
    And match response.data.recipientName == '홍길동'
    And match response.data.isDefault == false

  # ----------------------------------------------------------------
  # 시나리오 2: 주소 목록 조회 시 페이징 형식으로 반환한다
  #   내부에서 주소 1건 등록 후 목록을 조회한다
  # ----------------------------------------------------------------
  Scenario: [정상] 주소 목록 조회 → 200, 페이징 응답
    # 조회할 주소 사전 등록
    Given path '/api/v1/users/addresses'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + accessToken
    And request addressBody
    When method post
    Then status 201
    # 목록 조회
    Given path '/api/v1/users/addresses'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + accessToken
    When method get
    Then status 200
    # content 배열에 1건 이상 존재하는지 검증
    And match response.data.content == '#[_ > 0]'
    And match response.data.content[0].addressId == '#uuid'

  # ----------------------------------------------------------------
  # 시나리오 3: 주소 단건 조회 시 등록한 주소와 일치하는 정보를 반환한다
  #   내부에서 주소 1건 등록 후 해당 addressId로 단건 조회한다
  # ----------------------------------------------------------------
  Scenario: [정상] 주소 단건 조회 → 200
    # 조회할 주소 사전 등록
    Given path '/api/v1/users/addresses'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + accessToken
    And request addressBody
    When method post
    Then status 201
    * def createdAddressId = response.data.addressId
    # 단건 조회
    Given path '/api/v1/users/addresses/' + createdAddressId
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + accessToken
    When method get
    Then status 200
    # 등록한 addressId와 일치하는지 검증
    And match response.data.addressId == createdAddressId
    And match response.data.recipientName == '홍길동'

  # ----------------------------------------------------------------
  # 시나리오 4: 주소를 수정하면 변경된 수령인 이름을 반환한다
  #   내부에서 주소 1건 등록 후 수령인 이름을 변경한다
  # ----------------------------------------------------------------
  Scenario: [정상] 주소 수정 → 200, 변경된 수령인 반환
    # 수정할 주소 사전 등록
    Given path '/api/v1/users/addresses'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + accessToken
    And request addressBody
    When method post
    Then status 201
    * def createdAddressId = response.data.addressId
    # 수령인 이름 수정
    Given path '/api/v1/users/addresses/' + createdAddressId
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + accessToken
    And request { recipientName: '김철수' }
    When method patch
    Then status 200
    # 변경된 수령인 이름으로 반영됐는지 검증
    And match response.data.recipientName == '김철수'
    And match response.data.addressId == createdAddressId

  # ----------------------------------------------------------------
  # 시나리오 5: 기본 주소로 설정하면 isDefault가 true로 반환된다
  #   내부에서 주소 1건 등록 후 기본 주소로 지정한다
  # ----------------------------------------------------------------
  Scenario: [정상] 기본 주소 설정 → 200, isDefault true
    # 기본 주소로 설정할 주소 사전 등록
    Given path '/api/v1/users/addresses'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + accessToken
    And request addressBody
    When method post
    Then status 201
    * def createdAddressId = response.data.addressId
    # 기본 주소 지정
    Given path '/api/v1/users/addresses/' + createdAddressId + '/default'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + accessToken
    When method patch
    Then status 200
    And match response.data.addressId == createdAddressId
    And match response.data.isDefault == true

  # ----------------------------------------------------------------
  # 시나리오 6: 주소를 삭제하면 200을 반환한다
  #   내부에서 주소 1건 등록 후 삭제한다
  # ----------------------------------------------------------------
  Scenario: [정상] 주소 삭제 → 200
    # 삭제할 주소 사전 등록
    Given path '/api/v1/users/addresses'
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + accessToken
    And request addressBody
    When method post
    Then status 201
    * def createdAddressId = response.data.addressId
    # 주소 삭제
    Given path '/api/v1/users/addresses/' + createdAddressId
    And header X-Gateway-Secret = gatewaySecret
    And header Authorization = 'Bearer ' + accessToken
    When method delete
    Then status 200

  # ----------------------------------------------------------------
  # 시나리오 7: 토큰 없이 주소 등록 시 인증 실패로 401을 반환한다
  # ----------------------------------------------------------------
  Scenario: [예외] 토큰 없이 주소 등록 → 401
    Given path '/api/v1/users/addresses'
    And header X-Gateway-Secret = gatewaySecret
    And request addressBody
    When method post
    Then status 401
