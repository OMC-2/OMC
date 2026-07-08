#!/bin/bash
# 기존 Keycloak 사용자에 db_user_id attribute를 일괄 설정하는 마이그레이션 스크립트.
# 실행 후 모든 사용자가 재로그인하면 JWT에 db_user_id claim이 포함된다.
#
# 사용법:
#   ./scripts/keycloak-migrate-db-user-id.sh
#
# 환경변수 (기본값은 docker-compose 기준):
#   DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASS
#   KEYCLOAK_URL, KEYCLOAK_REALM, KEYCLOAK_ADMIN_USER, KEYCLOAK_ADMIN_PASS

set -euo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-omc}"
DB_USER="${DB_USER:-omc}"
DB_PASS="${DB_PASS:-omc_password}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-omc}"
KEYCLOAK_ADMIN_USER="${KEYCLOAK_ADMIN_USER:-admin}"
KEYCLOAK_ADMIN_PASS="${KEYCLOAK_ADMIN_PASS:-admin}"

command -v psql >/dev/null 2>&1 || { echo "psql 이 설치되어 있지 않습니다."; exit 1; }
command -v curl >/dev/null 2>&1 || { echo "curl 이 설치되어 있지 않습니다."; exit 1; }
command -v jq   >/dev/null 2>&1 || { echo "jq 가 설치되어 있지 않습니다."; exit 1; }

echo "=== Keycloak db_user_id 마이그레이션 시작 ==="

# 1. Keycloak admin 토큰 발급
ADMIN_TOKEN=$(curl -sf -X POST \
  "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=admin-cli&username=${KEYCLOAK_ADMIN_USER}&password=${KEYCLOAK_ADMIN_PASS}" \
  | jq -r '.access_token')

if [ -z "$ADMIN_TOKEN" ] || [ "$ADMIN_TOKEN" = "null" ]; then
  echo "❌ Keycloak 관리자 토큰 발급 실패"
  exit 1
fi
echo "✓ Keycloak 관리자 토큰 발급 완료"

# 2. DB에서 (keycloak_id, user_id) 전체 조회
USERS=$(PGPASSWORD="${DB_PASS}" psql \
  -h "${DB_HOST}" -p "${DB_PORT}" \
  -U "${DB_USER}" -d "${DB_NAME}" \
  -t -A -F '|' \
  -c "SELECT keycloak_id, user_id FROM user_db.p_users ORDER BY created_at")

TOTAL=$(echo "$USERS" | grep -c '|' || true)
echo "✓ DB에서 ${TOTAL}명 조회 완료"

SUCCESS=0
FAIL=0

# 3. 각 사용자에 db_user_id attribute 설정
while IFS='|' read -r keycloak_id user_id; do
  keycloak_id=$(echo "$keycloak_id" | xargs)
  user_id=$(echo "$user_id" | xargs)

  [ -z "$keycloak_id" ] || [ -z "$user_id" ] && continue

  # 현재 사용자 정보 조회 (기존 attributes 보존)
  USER_DATA=$(curl -sf \
    "${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/users/${keycloak_id}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" || echo '{}')

  # attributes가 없으면 빈 오브젝트로 초기화 후 db_user_id 추가
  UPDATED=$(echo "$USER_DATA" | jq --arg db_user_id "$user_id" \
    'if .attributes == null then .attributes = {} else . end | .attributes.db_user_id = [$db_user_id]')

  HTTP_STATUS=$(curl -sf -o /dev/null -w "%{http_code}" -X PUT \
    "${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/users/${keycloak_id}" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "$UPDATED" || echo "000")

  if [ "$HTTP_STATUS" = "204" ]; then
    echo "  ✓ ${keycloak_id} → db_user_id=${user_id}"
    SUCCESS=$((SUCCESS + 1))
  else
    echo "  ✗ ${keycloak_id} 실패 (HTTP ${HTTP_STATUS})"
    FAIL=$((FAIL + 1))
  fi
done <<< "$USERS"

echo ""
echo "=== 마이그레이션 완료: 성공 ${SUCCESS}명 / 실패 ${FAIL}명 ==="

if [ "$FAIL" -gt 0 ]; then
  echo "일부 사용자 마이그레이션에 실패했습니다. 로그를 확인하고 재실행하세요."
  exit 1
fi
