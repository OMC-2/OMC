# Auth 기능 테스트 가이드

## 1. 환경 실행 순서

### Step 1 — 인프라 기동 (postgres, redis, kafka, keycloak)

```bash
docker compose up -d
```

Keycloak이 완전히 뜰 때까지 대기 (약 90초)

확인:
```bash
docker inspect omc-keycloak --format='{{.State.Health.Status}}'
# healthy 출력될 때까지 대기
```

### Step 2 — JAR 빌드

```bash
./gradlew build -x test
```

### Step 3 — 서비스 기동 (eureka, config, gateway, user-service 등)

Docker 이미지 빌드 + 서비스 실행을 한 번에:
```bash
docker compose -f docker-compose.yml -f docker-compose.services.yml up --build -d
```

gateway, user-service healthy 확인:
```bash
docker inspect omc-gateway --format='{{.State.Health.Status}}'
docker inspect omc-user-service --format='{{.State.Health.Status}}'
```

---

## 2. API 테스트

### 회원가입

```bash
curl -X POST http://localhost:8080/api/v1/users/signup \
  -H "Content-Type: application/json" \
  -d '{
    "email": "my@test.com",
    "password": "Test1234!",
    "nickname": "내이름",
    "slackId": "U123"
  }'
```

**성공 응답:**
```json
{
  "success": true,
  "status": 201,
  "data": {
    "userId": "...",
    "email": "my@test.com",
    "nickname": "내이름",
    "role": "USER"
  }
}
```

---

### Admin 회원가입

```bash
curl -X POST http://localhost:8080/api/v1/users/admin/signup \
  -H "Content-Type: application/json" \
  -H "X-Admin-Secret: local-admin-secret" \
  -d '{
    "email": "admin@test.com",
    "password": "Admin1234!",
    "nickname": "관리자",
    "slackId": "U999"
  }'
```

**성공 응답:**
```json
{
  "success": true,
  "status": 201,
  "data": {
    "userId": "...",
    "email": "admin@test.com",
    "nickname": "관리자",
    "role": "ADMIN"
  }
}
```

`X-Admin-Secret` 헤더가 없거나 틀리면 `403 Forbidden` 응답.

---

### 로그인 (JWT 발급)

```bash
curl -X POST http://localhost:8080/api/v1/users/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "my@test.com",
    "password": "Test1234!"
  }'
```

**성공 응답:**
```json
{
  "success": true,
  "status": 200,
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "eyJhbGci...",
    "tokenType": "Bearer",
    "expiresIn": 3600
  }
}
```

`data.accessToken` 값을 복사한다.

---

### 내 프로필 조회 (JWT 검증 확인)

```bash
curl -X GET http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer {accessToken}"
```

**성공 응답:**
```json
{
  "success": true,
  "status": 200,
  "data": {
    "userId": "...",
    "email": "my@test.com",
    "nickname": "내이름",
    "slackId": "U123",
    "role": "USER",
    "createdAt": "..."
  }
}
```

---

## 3. 종료

```bash
docker compose -f docker-compose.yml -f docker-compose.services.yml down
```

데이터까지 초기화하려면:
```bash
docker compose -f docker-compose.yml -f docker-compose.services.yml down -v
```

---

## 4. 트러블슈팅

| 증상 | 원인 | 해결 |
|------|------|------|
| `503 Service Unavailable` | user-service가 Eureka에 미등록 | 30초 후 재시도 |
| `401 Unauthorized` | JWT 토큰 없거나 만료 | 로그인 재시도 |
| keycloak `unhealthy` | 기동 시간 부족 | `docker inspect omc-keycloak` 상태 확인 후 대기 |
| Flyway checksum mismatch | 마이그레이션 파일 변경 후 재기동 | `docker compose down -v` 로 볼륨 초기화 후 재시작 |
