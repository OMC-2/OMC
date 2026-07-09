#!/bin/bash
# ================================================================
# k6 부하테스트용 "조회 대상 주문" 1건 만들기
# E2E order_service.feature와 동일한 순서를 고정 계정으로 재현한다.
# 끝나면 k6에 넣을 USER_EMAIL / USER_PW / ORDER_ID를 출력한다.
#
# 실행: bash scripts/demo/seed-order.sh
# ================================================================
set -e

BASE="${BASE_URL:-http://localhost:8080}"
GW="${GATEWAY_SECRET:-local-secret}"
ADMIN_SECRET="${ADMIN_SECRET:-local-admin-secret}"
PW="password123"

# 고정 계정 (재실행 시 이미 있으면 409가 나므로 timestamp로 유니크하게)
TS=$(date +%s)
ADMIN_EMAIL="k6-admin-${TS}@example.com"
USER_EMAIL="k6-user-${TS}@example.com"

echo "[seed] admin 생성: $ADMIN_EMAIL"
curl -s -X POST "$BASE/api/v1/users/admin/signup" \
  -H "X-Gateway-Secret: $GW" -H "X-Admin-Secret: $ADMIN_SECRET" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$PW\",\"nickname\":\"k6admin\"}" > /dev/null

echo "[seed] admin 로그인"
ADMIN_TOKEN=$(curl -s -X POST "$BASE/api/v1/users/login" \
  -H "X-Gateway-Secret: $GW" -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$PW\"}" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

echo "[seed] user 생성: $USER_EMAIL"
curl -s -X POST "$BASE/api/v1/users/signup" \
  -H "X-Gateway-Secret: $GW" -H "Content-Type: application/json" \
  -d "{\"email\":\"$USER_EMAIL\",\"password\":\"$PW\",\"nickname\":\"k6user\"}" > /dev/null

echo "[seed] user 로그인"
USER_TOKEN=$(curl -s -X POST "$BASE/api/v1/users/login" \
  -H "X-Gateway-Secret: $GW" -H "Content-Type: application/json" \
  -d "{\"email\":\"$USER_EMAIL\",\"password\":\"$PW\"}" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

echo "[seed] 상품 생성"
PRODUCT_ID=$(curl -s -X POST "$BASE/api/v1/admin/products" \
  -H "X-Gateway-Secret: $GW" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"k6 load product","description":"for k6 load test","price":10000,"brand":"TestBrand","category":"Sneakers","imageUrl":"http://img.test/k6.jpg","initialQuantity":100}' \
  | grep -o '"productId":"[^"]*"' | cut -d'"' -f4)

# 드롭: 5초 후 시작, 1일 후 종료
START_AT=$(date -d "+5 seconds" +%Y-%m-%dT%H:%M:%S 2>/dev/null || date -v+5S +%Y-%m-%dT%H:%M:%S)
END_AT=$(date -d "+1 day" +%Y-%m-%dT%H:%M:%S 2>/dev/null || date -v+1d +%Y-%m-%dT%H:%M:%S)

echo "[seed] 드롭 생성 (start=$START_AT)"
DROP_ID=$(curl -s -X POST "$BASE/api/v1/admin/drops" \
  -H "X-Gateway-Secret: $GW" -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"productId\":\"$PRODUCT_ID\",\"startAt\":\"$START_AT\",\"endAt\":\"$END_AT\",\"totalQty\":50,\"holdTtlSec\":300}" \
  | grep -o '"dropId":"[^"]*"' | cut -d'"' -f4)

echo "[seed] 드롭 OPEN 대기 (최대 20초)..."
for i in $(seq 1 20); do
  STATUS=$(curl -s "$BASE/api/v1/drops/$DROP_ID" -H "X-Gateway-Secret: $GW" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
  if [ "$STATUS" = "OPEN" ]; then echo "  → OPEN ($i초)"; break; fi
  sleep 1
done

echo "[seed] 구매 선점 → orderId 생성"
ORDER_ID=$(curl -s -X POST "$BASE/api/v1/drops/$DROP_ID/purchase" \
  -H "X-Gateway-Secret: $GW" -H "Authorization: Bearer $USER_TOKEN" \
  | grep -o '"orderId":"[^"]*"' | cut -d'"' -f4)

echo "[seed] 주문 생성 대기 (PENDING_PAYMENT)..."
for i in $(seq 1 15); do
  OSTATUS=$(curl -s "$BASE/api/v1/orders/$ORDER_ID" \
    -H "X-Gateway-Secret: $GW" -H "Authorization: Bearer $USER_TOKEN" \
    | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
  if [ "$OSTATUS" = "PENDING_PAYMENT" ]; then echo "  → PENDING_PAYMENT ($i초)"; break; fi
  sleep 1
done

echo ""
echo "================================================================"
echo " 준비 완료! 아래 값을 k6에 사용하세요:"
echo "================================================================"
echo "  USER_EMAIL = $USER_EMAIL"
echo "  USER_PW    = $PW"
echo "  ORDER_ID   = $ORDER_ID"
echo ""
echo " 부하 실행 예:"
echo "  k6 run -e USER_EMAIL=$USER_EMAIL -e USER_PW=$PW -e ORDER_ID=$ORDER_ID k6/01-order-query.js"
echo "  k6 run -e USER_EMAIL=$USER_EMAIL -e USER_PW=$PW -e ORDER_ID=$ORDER_ID k6/04-order-query-stress.js"
echo "  k6 run -e USER_EMAIL=$USER_EMAIL -e USER_PW=$PW -e ORDER_ID=$ORDER_ID k6/05-order-query-spike.js"
echo "================================================================"
