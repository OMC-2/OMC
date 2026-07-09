#!/bin/bash
# order-service 서킷브레이커/장애 상황 AI 진단 검증용 카오스 스크립트
#
# product-service 를 의도적으로 중지시켜 장애를 유발하고,
# LLM 메트릭 분석 API(/api/v1/admin/analyze-metrics)가 실제 장애 메트릭
# (p99 응답 지연 급증 등)을 감지해 Hard Rule 을 발동하는지 검증한다.
#
# 실행:
#   bash scripts/demo/cb-chaos.sh
#
# 순서:
#   1. admin/user 계정 생성 + 로그인
#   2. product 생성 → drop 생성(재고10) → OPEN 대기   (product 살아있을 때)
#   3. product-service 중지 (장애 유발)
#   4. 6명이 각자 드롭 선점 → order→product 호출 6회 실패 → p99 지연 급증
#   5. AI 진단 호출 → Hard Rule(p99>5s) 발동 + LLM 추론 확인
#   6. product-service 복구
#
# 환경변수(선택, 미설정 시 로컬 기본값):
#   BASE_URL, ORDER_URL, PROM_URL, GATEWAY_SECRET, ADMIN_SECRET, TEST_PASSWORD
set -e

BASE="${BASE_URL:-http://localhost:8080}"          # gateway
ORDER="${ORDER_URL:-http://localhost:8083}"        # order 직접 (AI 진단)
PROM="${PROM_URL:-http://localhost:19090}"         # prometheus
GW_SECRET="${GATEWAY_SECRET:-local-secret}"
ADMIN_SECRET="${ADMIN_SECRET:-local-admin-secret}"
PASS="${TEST_PASSWORD:-Test1234!}"
TS=$(date +%s)
ADMIN_EMAIL="cbchaos-admin-${TS}@omc.com"

echo "================================================================"
echo " [cb-chaos] CB/장애 AI 진단 검증 시작 (ts=${TS})"
echo "================================================================"

# ---- 1. admin 생성 + 로그인 ----
echo "[cb-chaos] admin 생성 + 로그인..."
curl -s -X POST "${BASE}/api/v1/users/admin/signup" \
  -H "X-Gateway-Secret: ${GW_SECRET}" -H "X-Admin-Secret: ${ADMIN_SECRET}" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"${ADMIN_EMAIL}\",\"password\":\"${PASS}\",\"nickname\":\"cbadmin\"}" > /dev/null

ADMIN_TOKEN=$(curl -s -X POST "${BASE}/api/v1/users/login" \
  -H "X-Gateway-Secret: ${GW_SECRET}" -H 'Content-Type: application/json' \
  -d "{\"email\":\"${ADMIN_EMAIL}\",\"password\":\"${PASS}\"}" \
  | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

# ---- 2. user 6명 생성 + 로그인 (1유저=1선점 이므로 CB/부하 위해 6명) ----
echo "[cb-chaos] user 6명 생성 + 로그인..."
declare -a USER_TOKENS
for u in $(seq 1 6); do
  UEMAIL="cbchaos-user${u}-${TS}@omc.com"
  curl -s -X POST "${BASE}/api/v1/users/signup" \
    -H "X-Gateway-Secret: ${GW_SECRET}" -H 'Content-Type: application/json' \
    -d "{\"email\":\"${UEMAIL}\",\"password\":\"${PASS}\",\"nickname\":\"cbuser${u}\"}" > /dev/null
  UT=$(curl -s -X POST "${BASE}/api/v1/users/login" \
    -H "X-Gateway-Secret: ${GW_SECRET}" -H 'Content-Type: application/json' \
    -d "{\"email\":\"${UEMAIL}\",\"password\":\"${PASS}\"}" \
    | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
  USER_TOKENS[$u]=$UT
done

# ---- 3. product 생성 ----
echo "[cb-chaos] product 생성..."
PRODUCT_ID=$(curl -s -X POST "${BASE}/api/v1/admin/products" \
  -H "X-Gateway-Secret: ${GW_SECRET}" -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"name":"CBChaosProduct","description":"CB test","price":10000,"brand":"TestBrand","category":"Sneakers","imageUrl":"http://img.test/cb.jpg","initialQuantity":100}' \
  | grep -o '"productId":"[^"]*"' | cut -d'"' -f4)

# ---- 4. drop 생성 (재고 10, 5초 후 OPEN) ----
echo "[cb-chaos] drop 생성 (totalQty=10)..."
START_AT=$(date -d "+5 seconds" +"%Y-%m-%dT%H:%M:%S" 2>/dev/null || date -v+5S +"%Y-%m-%dT%H:%M:%S")
END_AT=$(date -d "+1 day" +"%Y-%m-%dT%H:%M:%S" 2>/dev/null || date -v+1d +"%Y-%m-%dT%H:%M:%S")

DROP_ID=$(curl -s -X POST "${BASE}/api/v1/admin/drops" \
  -H "X-Gateway-Secret: ${GW_SECRET}" -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d "{\"productId\":\"${PRODUCT_ID}\",\"startAt\":\"${START_AT}\",\"endAt\":\"${END_AT}\",\"totalQty\":10,\"holdTtlSec\":300}" \
  | grep -o '"dropId":"[^"]*"' | cut -d'"' -f4)

echo "[cb-chaos] drop OPEN 대기 (최대 15초)..."
for i in $(seq 1 15); do
  STATUS=$(curl -s "${BASE}/api/v1/drops/${DROP_ID}" -H "X-Gateway-Secret: ${GW_SECRET}" \
    | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
  if [ "$STATUS" = "OPEN" ]; then echo "[cb-chaos] → OPEN 확인 (${i}초)"; break; fi
  sleep 1
done

# 데이터 준비 검증 - 실패 시 product 죽이지 않고 중단
if [ -z "$PRODUCT_ID" ] || [ -z "$DROP_ID" ]; then
  echo "[cb-chaos] !! 데이터 준비 실패 (productId/dropId 없음). 중단."
  exit 1
fi

# ---- 5. product 죽이기 ----
echo "[cb-chaos] ★ product-service 중지 (장애 유발)..."
docker stop omc-product-service > /dev/null
sleep 3

# ---- 6. 6명이 각자 선점 (order→product 호출 6회 실패 유발) ----
echo "[cb-chaos] 6명이 각자 드롭 선점 (order→product 호출 실패 유발)..."
for u in $(seq 1 6); do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${BASE}/api/v1/drops/${DROP_ID}/purchase" \
    -H "X-Gateway-Secret: ${GW_SECRET}" -H "Authorization: Bearer ${USER_TOKENS[$u]}")
  echo "[cb-chaos]   user${u} 선점: HTTP ${CODE}"
  sleep 2
done
echo "[cb-chaos] 메트릭 반영 대기 (20초, Prometheus scrape + 히스토그램 반영)..."
sleep 20

# ---- 7. AI 진단 호출 ----
echo "================================================================"
echo " [cb-chaos] AI 진단 (실제 장애 메트릭 기반)"
echo "================================================================"
curl -s -X POST "${ORDER}/api/v1/admin/analyze-metrics" \
  -H "X-Gateway-Secret: ${GW_SECRET}" \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001" \
  -H "X-User-Role: ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"question":"orders are failing after a deploy, what is wrong?"}'
echo ""
echo ""

# ---- 8. product 복구 ----
echo "[cb-chaos] product-service 복구..."
docker start omc-product-service > /dev/null
echo "[cb-chaos] 완료. (CB는 wait-duration 후 half-open → 정상 복구)"
echo "================================================================"
