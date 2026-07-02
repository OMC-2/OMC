#!/bin/bash
# k6/run.sh — 쿠폰 부하 테스트 자동화
# 사용법: ./k6/run.sh coupon [태그]
# 예시:   ./k6/run.sh coupon pool-size-10   (개선 전)
#         ./k6/run.sh coupon pool-size-50   (개선 후)

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
PROJECT_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
SCENARIO_TYPE=${1:-"coupon"}
TAG=${2:-""}

COMPOSE="docker compose -f $PROJECT_ROOT/docker-compose.yml -f $PROJECT_ROOT/docker-compose.services.yml"

# ============================================================
# 설정값 — 필요 시 수정
# ============================================================
BASE_URL="http://localhost:8080"
COUPON_SERVICE_URL="http://localhost:8087"
GATEWAY_SECRET="local-secret"
ADMIN_USER_ID="00000000-0000-0000-0000-000000000001"

COUPON_LOAD_QTY=100       # Smoke / Load / Spike Test 쿠폰 수량
COUPON_STRESS_QTY=10000   # Stress Test 쿠폰 수량 (재고 소진이 아닌 서버 한계 탐색)
USER_COUNT=1000           # 필요 유저 수 (Stress 최대 1000명 기준)

POSTGRES_CONTAINER="omc-postgres"
POSTGRES_USER="omc"
POSTGRES_DB="omc"

# ============================================================
# 결과 폴더 설정
# ============================================================
DATE=$(date +%Y-%m-%d)
if [ -n "$TAG" ]; then
  RESULT_DIR="$SCRIPT_DIR/results/${DATE}_${TAG}"
else
  RESULT_DIR="$SCRIPT_DIR/results/${DATE}"
fi

# ============================================================
# 공통 함수
# ============================================================

log() {
  echo "[$(date +%H:%M:%S)] $*"
}

sentry_disable() {
  log "▶ Sentry 비활성화 (부하 테스트 중 에러 알림 차단)"
  SENTRY_DSN="" $COMPOSE up -d --force-recreate --no-deps coupon-service > /dev/null 2>&1
  # Spring Boot 초기화 대기 (최대 90초)
  local i=0
  while [ $i -lt 90 ]; do
    if curl -s "http://localhost:8087/actuator/health" 2>/dev/null | grep -q '"UP"'; then
      log "  ✅ coupon-service 준비 완료 (Sentry 비활성화됨)"
      return 0
    fi
    sleep 3
    i=$((i+3))
  done
  log "  ⚠️  health check 타임아웃 — 계속 진행합니다"
}

sentry_restore() {
  log "▶ Sentry 복원"
  $COMPOSE up -d --force-recreate --no-deps coupon-service > /dev/null 2>&1
  log "  ✅ coupon-service 재시작 완료 (Sentry 복원됨)"
}

_verify_users_json() {
  local count
  count=$(python3 -c "import json; print(len(json.load(open('$SCRIPT_DIR/users.json'))))" 2>/dev/null || echo "0")
  if [ "$count" -lt "$USER_COUNT" ]; then
    log "❌ users.json 생성 실패 또는 유저 부족 (${count}명) → 중단"
    exit 1
  fi
  log "✅ users.json 생성 완료 (${count}명)"
}

create_coupon() {
  local qty=$1
  local name=$2
  local response
  response=$(curl -s -X POST "$COUPON_SERVICE_URL/api/v1/coupons" \
    -H "Content-Type: application/json" \
    -H "X-Gateway-Secret: $GATEWAY_SECRET" \
    -H "X-User-Id: $ADMIN_USER_ID" \
    -H "X-User-Role: ADMIN" \
    -d "{
      \"name\": \"$name\",
      \"discountType\": \"AMOUNT\",
      \"discountValue\": 1000,
      \"totalQuantity\": $qty,
      \"startedAt\": \"2025-01-01T00:00:00\",
      \"expiredAt\": \"2099-12-31T23:59:59\"
    }")
  echo "$response" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['couponId'])" 2>/dev/null
}

verify_and_save() {
  local stage=$1
  local coupon_id=$2
  local total_qty=$3
  local stage_dir="$RESULT_DIR/$stage"
  mkdir -p "$stage_dir"

  echo ""
  log "[검증] $stage"

  local db_status="N/A"
  local redis_status="N/A"

  DB_COUNT=$(docker exec "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tA \
    -c "SELECT COUNT(*) FROM coupon_db.p_user_coupons WHERE coupon_id = '$coupon_id';" 2>/dev/null || true)
  DB_COUNT=$(echo "$DB_COUNT" | tr -d '[:space:]')

  if [ -n "$DB_COUNT" ] && [ "$DB_COUNT" != "" ]; then
    if [ "$DB_COUNT" -le "$total_qty" ] 2>/dev/null; then
      log "  ✅ DB 발급 건수: ${DB_COUNT}건 / totalQuantity: ${total_qty}"
      db_status="정상 (${DB_COUNT}건)"
    else
      log "  ❌ DB 발급 건수: ${DB_COUNT}건 (초과! totalQuantity: ${total_qty})"
      db_status="초과 (${DB_COUNT}건)"
    fi
  else
    log "  ⚠️  DB 연결 실패"
    db_status="연결 실패"
  fi

  REDIS_STOCK=$(redis-cli GET "coupon:stock:$coupon_id" 2>/dev/null || true)
  REDIS_STOCK=$(echo "$REDIS_STOCK" | tr -d '[:space:]')

  if [ -n "$REDIS_STOCK" ] && [ "$REDIS_STOCK" != "" ]; then
    if [ "$REDIS_STOCK" -ge 0 ] 2>/dev/null; then
      log "  ✅ Redis stock: $REDIS_STOCK"
      redis_status="정상 (stock: $REDIS_STOCK)"
    else
      log "  ❌ Redis stock: $REDIS_STOCK (음수! 정합성 문제)"
      redis_status="오류 (stock: $REDIS_STOCK)"
    fi
  else
    log "  ⚠️  Redis 키 없음 (재고 소진 또는 키 만료)"
    redis_status="키 없음 (재고 소진 또는 키 만료)"
  fi

  # summary.txt 저장
  cat > "$stage_dir/summary.txt" <<EOF
테스트: $stage
태그: ${TAG:-없음}
날짜: $(date '+%Y-%m-%d %H:%M:%S')
쿠폰 ID: $coupon_id
totalQuantity: $total_qty
---
DB 발급 건수: $db_status
Redis stock: $redis_status
EOF

  log "  결과 저장: $stage_dir/"
  log "    - summary.txt (검증 결과 요약)"
  log "    - raw.json    (k6 원본 메트릭)"
}

run_stage() {
  local stage=$1
  local qty=$2
  local vus=${3:-0}
  local stage_dir="$RESULT_DIR/$stage"
  mkdir -p "$stage_dir"

  echo ""
  echo "=========================================="
  log "▶ 단계: $stage"
  echo "=========================================="

  # 쿠폰 생성
  local coupon_id
  coupon_id=$(create_coupon "$qty" "[$stage] 부하테스트 쿠폰")
  if [ -z "$coupon_id" ]; then
    log "❌ 쿠폰 생성 실패 → 중단 (서비스가 실행 중인지 확인하세요)"
    exit 1
  fi
  log "  쿠폰 생성 완료: $coupon_id"

  # k6 실행
  local k6_exit=0
  if [ "$vus" -gt 0 ]; then
    k6 run \
      --env SCENARIO=stress \
      --env VUS="$vus" \
      --env COUPON_ID="$coupon_id" \
      --env BASE_URL="$BASE_URL" \
      --env GATEWAY_SECRET="$GATEWAY_SECRET" \
      --out "json=$stage_dir/raw.json" \
      "$SCRIPT_DIR/03-coupon-issue.js" || k6_exit=$?
  else
    k6 run \
      --env SCENARIO="$stage" \
      --env COUPON_ID="$coupon_id" \
      --env BASE_URL="$BASE_URL" \
      --env GATEWAY_SECRET="$GATEWAY_SECRET" \
      --out "json=$stage_dir/raw.json" \
      "$SCRIPT_DIR/03-coupon-issue.js" || k6_exit=$?
  fi

  # DB/Redis 검증 + summary.txt 저장
  verify_and_save "$stage" "$coupon_id" "$qty"

  # Smoke/Load Test 실패 시 중단
  if [[ "$stage" == "smoke" || "$stage" == "load" ]] && [ "$k6_exit" -ne 0 ]; then
    log "❌ $stage 임계값 초과 → 다음 단계로 넘어가지 않습니다"
    exit 1
  fi

  # Stress Test: 임계값 초과 시 다음 레벨 여부 확인
  if [[ "$stage" == stress-* ]] && [ "$k6_exit" -ne 0 ]; then
    echo ""
    log "⚠️  임계값 초과 감지 (에러율 1% 초과)"
    read -rp "다음 Stress 레벨로 계속할까요? [y/N] " answer
    if [[ ! "$answer" =~ ^[Yy]$ ]]; then
      log "Stress Test 중단 — 현재까지 결과: $RESULT_DIR"
      return 1
    fi
  fi

  return 0
}

# ============================================================
# 쿠폰 시나리오
# ============================================================
run_coupon() {
  echo ""
  echo "=========================================="
  echo "  k6 쿠폰 부하 테스트"
  if [ -n "$TAG" ]; then
    echo "  태그: $TAG"
  fi
  echo "  결과 폴더: $RESULT_DIR"
  echo "=========================================="
  echo ""

  # 0. Sentry 비활성화 (테스트 종료/중단 시 자동 복원)
  trap 'sentry_restore' EXIT
  sentry_disable
  echo ""

  # 1. 유저 확인 → 없으면 생성
  if [ -f "$SCRIPT_DIR/users.json" ]; then
    EXISTING=$(python3 -c "import json; print(len(json.load(open('$SCRIPT_DIR/users.json'))))" 2>/dev/null || echo "0")
    if [ "$EXISTING" -ge "$USER_COUNT" ]; then
      log "✅ users.json 존재 (${EXISTING}명) → 유저 생성 스킵"
    else
      log "⚠️  users.json 유저 부족 (${EXISTING}명 < ${USER_COUNT}명) → 재생성"
      rm -f "$SCRIPT_DIR/users.json"
      COUNT=$USER_COUNT ROLE=USER node "$SCRIPT_DIR/generator.js" || { log "❌ 유저 생성 실패"; exit 1; }
    fi
  else
    log "▶ users.json 없음 → 유저 ${USER_COUNT}명 생성 중..."
    log "  (X-Load-Test 헤더로 Rate Limit 우회 — 약 1분 소요)"
    COUNT=$USER_COUNT ROLE=USER node "$SCRIPT_DIR/generator.js" || { log "❌ 유저 생성 실패"; exit 1; }
    _verify_users_json
  fi

  echo ""

  # 2. 단계별 테스트 (smoke/load 실패 시 run_stage 내부에서 exit)
  run_stage "smoke"       $COUPON_LOAD_QTY
  run_stage "load"        $COUPON_LOAD_QTY
  run_stage "stress-200"  $COUPON_STRESS_QTY  200 || true
  run_stage "stress-400"  $COUPON_STRESS_QTY  400 || true
  run_stage "stress-600"  $COUPON_STRESS_QTY  600 || true
  run_stage "stress-800"  $COUPON_STRESS_QTY  800 || true
  run_stage "stress-1000" $COUPON_STRESS_QTY 1000 || true
  run_stage "spike"       $COUPON_LOAD_QTY        || true

  # 3. 완료 → Sentry 복원 후 trap 해제
  trap - EXIT
  sentry_restore
  echo ""
  echo "=========================================="
  log "  모든 테스트 완료"
  log "  결과 위치: $RESULT_DIR"
  echo "=========================================="
  echo ""

  # 4. Cleanup 여부 확인
  log "⚠️  cleanup 전 DB/Redis/Grafana 검증을 먼저 완료하세요."
  read -rp "테스트 유저를 정리(cleanup)할까요? [y/N] " answer
  if [[ "$answer" =~ ^[Yy]$ ]]; then
    node "$SCRIPT_DIR/cleanup.js"
  else
    log "cleanup 스킵 — 나중에 'node k6/cleanup.js' 로 실행하세요."
  fi
}

# ============================================================
# 진입점
# ============================================================
case "$SCENARIO_TYPE" in
  coupon) run_coupon ;;
  *)
    echo "사용법: ./k6/run.sh <scenario> [태그]"
    echo "지원 시나리오: coupon"
    echo ""
    echo "예시:"
    echo "  ./k6/run.sh coupon               # 태그 없이 실행"
    echo "  ./k6/run.sh coupon pool-size-10  # 개선 전"
    echo "  ./k6/run.sh coupon pool-size-50  # 개선 후"
    exit 1
    ;;
esac
