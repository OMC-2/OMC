# 테스트 전략

이 프로젝트에서 사용하는 3가지 테스트 계층에 대한 설명.

---

## 1. 단위 테스트 (Unit Test)

### 목적

하나의 함수(메서드)가 올바르게 동작하는지 검증한다.
외부 의존성(DB, Keycloak 등)은 모두 Mock으로 대체한다.

### 특징

- 빠르다 (ms 단위)
- 외부 인프라 불필요
- CI에서 항상 실행

### 대상

- `UserService.signup()` — 비즈니스 로직만 검증
- 예: 중복 이메일이면 예외를 던지는가?
- 예: Keycloak 호출 후 DB 저장 실패 시 롤백(deleteUser)이 호출되는가?

### 도구

- JUnit 5 + Mockito

### 예시

```java
// UserRepository, KeycloakAdminClient를 Mock으로 주입
// 실제 DB, 실제 Keycloak 없이 UserService 로직만 실행
@Test
void 이미_존재하는_이메일로_가입하면_예외가_발생한다() {
    given(userRepository.existsByEmail("test@test.com")).willReturn(true);
    assertThatThrownBy(() -> userService.signup(request))
        .isInstanceOf(UserAlreadyExistsException.class);
}
```

---

## 2. 통합 테스트 (Integration Test)

### 목적

하나의 API 요청이 컨트롤러 → 서비스 → DB까지 연결되는 전체 흐름을 검증한다.

### 특징

- `@SpringBootTest`로 애플리케이션 전체를 테스트 프로세스 안에서 기동
- MockMvc로 HTTP 요청을 시뮬레이션
- TestContainers로 실제 PostgreSQL 컨테이너를 테스트 중에만 띄움
- Keycloak은 WireMock으로 대체 (실제 Keycloak은 너무 무거움)
- 단위 테스트보다 느리지만 CI에서 실행 가능

### 대상

- `POST /api/v1/users/signup` 한 번 호출로 아래를 모두 검증
  - 응답 HTTP 상태 201
  - 응답 body 구조 및 데이터
  - 실제 PostgreSQL DB에 데이터가 저장되었는지
  - 중복 이메일 재가입 시 409 반환

### 사전 설정: Docker Desktop 소켓 허용

TestContainers는 Docker 데몬 소켓을 통해 컨테이너를 제어한다.  
Docker Desktop 4.x 이상에서는 기본 소켓이 비활성화되어 있어 아래 설정이 필요하다.

**Docker Desktop → Settings → Advanced → "Allow the default Docker socket to be used (requires password)"** 를 켠다.

> 이 옵션을 켜면 `/var/run/docker.sock`이 생성되어 TestContainers가 Docker 데몬에 접근할 수 있다.  
> 비밀번호를 한 번 입력하면 이후 재기동 시에도 유지된다.

### 도구

- JUnit 5 + `@SpringBootTest` + MockMvc
- TestContainers (PostgreSQL)
- WireMock (Keycloak Admin API mocking)

### 흐름

```
MockMvc → POST /signup
    → Controller
    → UserService
    → WireMock (Keycloak 가짜 응답)
    → 실제 PostgreSQL 컨테이너에 저장
    → DB 직접 조회로 저장 확인
```

---

## 3. Newman E2E 테스트

### 목적

인프라(Keycloak, Gateway, DB)가 모두 실제로 기동된 상태에서 진짜 HTTP 요청으로 API 흐름을 검증한다.

### 특징

- 서버가 실제로 떠 있어야 실행 가능 (docker compose 기동 후)
- 코드가 아닌 Postman Collection 파일(.json)로 시나리오 정의
- 나중에 여러 서비스 연계 시나리오(회원가입 → 주문 → 결제)도 추가 가능
- 배포 후 스모크 테스트로도 활용 가능

> **스모크 테스트**: 배포 직후 "서버가 살아있나?"를 확인하는 최소한의 검증.
> 핵심 API 몇 개만 찔러보고 기본 동작 여부를 빠르게 확인하는 용도.

### 대상 시나리오 (예정)

1. 회원가입 → 201
2. 중복 이메일 가입 → 409
3. 로그인 → JWT 토큰 발급
4. 토큰으로 `GET /me` → 프로필 반환
5. 어드민 가입 → 201, role = ADMIN

### 도구

- Postman Collection + Newman CLI

### 실행 방법 (예정)

```bash
# 인프라 기동 후
newman run docs/test/postman/user-service.postman_collection.json \
  --env-var baseUrl=http://localhost:8080
```

---

## 요약

| 구분 | 범위 | 인프라 필요 | 속도 | 주요 목적 |
|---|---|---|---|---|
| 단위 테스트 | 함수 1개 | 불필요 (전부 Mock) | 빠름 | 비즈니스 로직 검증 |
| 통합 테스트 | API 1개 (컨트롤러~DB) | PostgreSQL (TestContainers) | 보통 | 전체 흐름 + DB 저장 검증 |
| Newman E2E | 여러 API 시나리오 | 전체 인프라 기동 필요 | 느림 | 실제 환경 흐름 검증 |
