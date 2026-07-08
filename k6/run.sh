#!/bin/bash
# k6/run.sh — 쿠폰 부하 테스트 자동화
#
# 사용법:
#   ./k6/run.sh coupon <command> [태그]
#
# Commands:
#   load-start    부하 테스트용 최소 서비스만 기동 (CPU 절약)
#   load-stop     모든 서비스 종료
#   setup         유저 준비 + Sentry 비활성화 (개별 단계 실행 전 1회만)
#   restore       Sentry 복원 (개별 단계 실행 모두 끝난 후)
#   all [tag]     setup + 전체 단계 + restore (한 번에 전부)
#   smoke [tag]   smoke 단계만 실행 (setup 없이)
#   load [tag]    load 단계만 실행
#   load-repeat [N] [tag]  load를 N번 반복 (기본 10번, 매 회차 새 쿠폰)
#   stress-200 [tag] ~ stress-1000 [tag]
#   spike [tag]   spike 단계만 실행
#
# 권장 워크플로우:
#   # 전체 자동 실행
#   ./k6/run.sh coupon all pool-size-10
#
#   # 단계별 수동 실행
#   ./k6/run.sh coupon setup                     # 1회
#   ./k6/run.sh coupon smoke   pool-size-10
#   ./k6/run.sh coupon load    pool-size-10
#   ./k6/run.sh coupon load-repeat 10 pool-size-10   # load 10회 반복
#   ./k6/run.sh coupon stress-400 pool-size-10
#   ./k6/run.sh coupon restore                   # 마지막에 1회

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
PROJECT_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
SCENARIO_TYPE=${1:-"coupon"}
COMMAND=${2:-""}    # setup | restore | all | smoke | load | stress-* | spike
ARG3=${3:-""}
ARG4=${4:-""}
TAG=${ARG3}
LOAD_REPEAT_ROUNDS=${LOAD_REPEAT:-10}
COUPON_SCALE=${COUPON_SCALE:-1}


COMPOSE="docker compose -f $PROJECT_ROOT/docker-compose.yml -f $PROJECT_ROOT/docker-compose.services.yml"

# ============================================================
# 설정값 — 필요 시 수정
# ============================================================
BASE_URL="http://localhost"
GATEWAY_SECRET="local-secret"
ADMIN_USER_ID="00000000-0000-0000-0000-000000000001"

COUPON_LOAD_QTY=${COUPON_LOAD_QTY:-1000}        # Smoke / Load / Spike Test 쿠폰 수량
LOAD_REPEAT=${LOAD_REPEAT:-10}                  # load-repeat 반복 횟수
COUPON_STRESS_QTY=${COUPON_STRESS_QTY:-10000}   # Stress Test 쿠폰 수량 (재고 소진이 아닌 서버 한계 탐색)
USER_COUNT=${USER_COUNT:-1000}                   # 필요 유저 수
REQUEST_INTERVAL_MS=${REQUEST_INTERVAL_MS:-50}   # 유저 생성 요청 간격 (ms) — 클수록 안정, 느림
STAGE_COOLDOWN_SEC=${STAGE_COOLDOWN_SEC:-60}     # all 단계 사이 안정화 대기
FORCE_GC_BEFORE_STAGE=${FORCE_GC_BEFORE_STAGE:-true}
P95_LIMIT_MS=${P95_LIMIT_MS:-10000}
AUTO_CLEANUP=${AUTO_CLEANUP:-false}

if [ "$COMMAND" = "load-repeat" ]; then
  if [[ "$ARG3" =~ ^[0-9]+$ ]]; then
    LOAD_REPEAT_ROUNDS="$ARG3"
    TAG="$ARG4"
  elif [ -n "$ARG3" ]; then
    LOAD_REPEAT_ROUNDS="$LOAD_REPEAT"
    TAG="$ARG3"
  else
    LOAD_REPEAT_ROUNDS="$LOAD_REPEAT"
    TAG=""
  fi
fi

POSTGRES_CONTAINER="omc-postgres"
POSTGRES_USER="omc"
POSTGRES_DB="omc"

# ============================================================
# 결과 폴더 설정
# ============================================================
DATETIME=$(date +%Y-%m-%d_%H%M%S)
if [ -n "$TAG" ]; then
  RESULT_DIR="$SCRIPT_DIR/results/${DATETIME}_${TAG}"
else
  RESULT_DIR="$SCRIPT_DIR/results/${DATETIME}"
fi

# ============================================================
# 공통 함수
# ============================================================

log() {
  echo "[$(date +%H:%M:%S)] $*"
}

coupon_service_url() {
  local index=${1:-1}
  local mapping
  mapping=$($COMPOSE port --index "$index" coupon-service 8087 2>/dev/null | head -n 1)
  if [ -z "$mapping" ]; then
    return 1
  fi
  echo "http://localhost:${mapping##*:}"
}

wait_gateway_route() {
  log "  ⏳ Gateway → coupon-service 경로 활성화 대기 중..."
  local gw_wait=0
  local consecutive=0
  while [ $gw_wait -lt 60 ]; do
    local status
    status=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/v1/coupons/health-check" \
      -H "X-Gateway-Secret: $GATEWAY_SECRET" 2>/dev/null)
    if [ "$status" != "503" ] && [ "$status" != "000" ] && [ "$status" != "404" ]; then
      consecutive=$((consecutive + 1))
      if [ "$consecutive" -ge 3 ]; then
        log "  ✅ Gateway → coupon-service 경로 정상 (${gw_wait}초 소요)"
        sleep 2
        return 0
      fi
    else
      consecutive=0
    fi
    sleep 1
    gw_wait=$((gw_wait + 1))
  done
  log "  ⚠️  Gateway → coupon-service 경로 갱신 타임아웃"
  return 1
}

prepare_stage() {
  local stage=$1
  if [ "$FORCE_GC_BEFORE_STAGE" = "true" ]; then
    log "  🧹 $stage 시작 전 coupon-service GC 및 안정화"
    local failed=0
    local index url
    for ((index=1; index<=COUPON_SCALE; index++)); do
      url=$(coupon_service_url "$index") || { failed=1; continue; }
      curl --fail --silent --show-error -X POST "$url/actuator/forcegc" >/dev/null || failed=1
    done
    if [ "$failed" -ne 0 ]; then
      log "❌ forcegc 호출 실패"
      return 1
    fi
    sleep 10
  fi
}

cooldown_between_stages() {
  if [ "$STAGE_COOLDOWN_SEC" -gt 0 ] 2>/dev/null; then
    log "  ⏳ 단계 간 cooldown ${STAGE_COOLDOWN_SEC}초"
    sleep "$STAGE_COOLDOWN_SEC"
  fi
  wait_gateway_route
}

sentry_disable() {
  log "▶ Sentry 비활성화 (부하 테스트 중 에러 알림 차단)"
  local scale_opt=""
  if [ "$COUPON_SCALE" -gt 1 ] 2>/dev/null; then
    scale_opt="--scale coupon-service=$COUPON_SCALE"
  fi
  SENTRY_DSN="" $COMPOSE up -d --force-recreate --no-deps $scale_opt coupon-service > /dev/null 2>&1
  # Spring Boot 초기화 대기 (최대 90초)
  local i=0
  local coupon_url
  coupon_url=$(coupon_service_url 1) || true
  while [ $i -lt 90 ]; do
    coupon_url=$(coupon_service_url 1) || true
    if [ -n "$coupon_url" ] && curl -s "$coupon_url/actuator/health/readiness" 2>/dev/null | grep -q '"UP"'; then
      log "  ✅ coupon-service 준비 완료 (Sentry 비활성화됨)"
      break
    fi
    sleep 3
    i=$((i+3))
  done
  if [ $i -ge 90 ]; then
    log "  ⚠️  health check 타임아웃 — 계속 진행합니다"
  fi

  # Eureka 등록 + 게이트웨이 캐시 갱신 대기 (최대 60초)
  wait_gateway_route
}

sentry_restore() {
  log "▶ Sentry 복원"
  local scale_opt=""
  if [ "$COUPON_SCALE" -gt 1 ] 2>/dev/null; then
    scale_opt="--scale coupon-service=$COUPON_SCALE"
  fi
  if ! $COMPOSE up -d --force-recreate --no-deps $scale_opt coupon-service > /dev/null 2>&1; then
    log "  ❌ coupon-service 재생성 실패"
    return 1
  fi

  local waited=0
  local coupon_url
  while [ "$waited" -lt 90 ]; do
    coupon_url=$(coupon_service_url 1) || true
    if [ -n "$coupon_url" ] && curl -s "$coupon_url/actuator/health/readiness" 2>/dev/null | grep -q '"UP"'; then
      wait_gateway_route || return 1
      log "  ✅ coupon-service 준비 완료 (Sentry 복원됨)"
      return 0
    fi
    sleep 3
    waited=$((waited + 3))
  done
  log "  ❌ coupon-service Sentry 복원 후 준비 타임아웃"
  return 1
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
  local response coupon_url
  coupon_url=$(coupon_service_url 1) || {
    log "❌ coupon-service 할당 포트를 찾지 못했습니다" >&2
    return 1
  }
  response=$(curl -s -X POST "$coupon_url/api/v1/coupons" \
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

# k6 setup()과 handleSummary()가 별도 JS 컨텍스트로 실행되어 모듈 변수를 공유할 수 없음.
# 티켓 캐시(ticket_cache.json)를 bash+Python에서 직접 관리한다.
ensure_ticket_cache() {
  local coupon_id=${1:-00000000-0000-0000-0000-000000000001}
  local cache_file="$SCRIPT_DIR/ticket_cache.json"
  local ttl=86400
  local now
  now=$(date +%s)

  # [자동 검증] users.json이 존재할 때 캐시된 Access Token의 유효성을 테스트해 세션 만료 시 자동 복구
  if [ -f "$SCRIPT_DIR/users.json" ]; then
    local test_token
    test_token=$(python3 -c "import json; d=json.load(open('$SCRIPT_DIR/users.json')); print(d[0].get('token','')) if d else print('')" 2>/dev/null)

    if [ -n "$test_token" ]; then
      local test_code
      test_code=$(curl -s -o /dev/null -w "%{http_code}" \
        -H "Authorization: Bearer $test_token" \
        "$BASE_URL/api/v1/coupons/me" 2>/dev/null || echo "000")

      if [ "$test_code" = "401" ] || [ "$test_code" = "403" ] || [ "$test_code" = "000" ]; then
        log "  ⚠️  세션 만료 감지 (HTTP $test_code) → Keycloak/DB가 초기화되었을 가능성이 있습니다."
        log "  🧹 기존 만료 캐시(users.json / ticket_cache.json)를 리셋하고 유저를 신규 가입시킵니다..."
        rm -f "$SCRIPT_DIR/users.json"
        rm -f "$cache_file"
        
        # 자동으로 setup 단계 재실행하여 유효한 신규 토큰 발급 완수
        coupon_setup || { log "❌ 자동 셋업 재기동 실패"; return 1; }
      fi
    else
      log "  ⚠️  캐시된 토큰 없음 → 리셋 및 신규 가입 진행"
      rm -f "$SCRIPT_DIR/users.json"
      rm -f "$cache_file"
      coupon_setup || { log "❌ 자동 셋업 재기동 실패"; return 1; }
    fi
  fi

  if [ -f "$cache_file" ]; then
    local issued_at count age valid_count
    issued_at=$(python3 -c "import json; print(json.load(open('$cache_file'))['issued_at'])" 2>/dev/null || echo 0)
    # None이 아닌 유효한 티켓 개수만 카운트
    valid_count=$(python3 -c "import json; print(len([t for t in json.load(open('$cache_file'))['tickets'] if t]))" 2>/dev/null || echo 0)
    age=$(( now - issued_at ))
    if [ "$age" -lt "$ttl" ] && [ "$valid_count" -ge "$USER_COUNT" ]; then
      log "  ✅ 티켓 캐시 유효 ($(( age / 3600 ))시간 전 발급, ${valid_count}개)"
      return 0
    fi
    log "  ⚠️  티켓 캐시 만료 또는 유효 티켓 부족(${valid_count}/${USER_COUNT}) — 재발급 중..."
  else
    log "  ℹ️  티켓 캐시 없음 — 발급 중..."
  fi

  local retry_count=0
  local max_retries=15
  while [ $retry_count -lt $max_retries ]; do
    log "  🔑 [시도 $((retry_count + 1))/$max_retries] AES 티켓 발급 시작..."

    # Gateway가 Keycloak 인증 정보를 게이트웨이 기동 직후에 안전하게 땡겨오도록 1회성 더미 요청 후 3초 대기
    curl -s -o /dev/null -H "Authorization: Bearer dummy" "$BASE_URL/api/v1/coupons/dummy/ticket" 2>/dev/null || true
    sleep 3

    # python 스크립트 실행하여 티켓 발급
    local out
    out=$(python3 - "$cache_file" "$SCRIPT_DIR/users.json" "$BASE_URL" "$GATEWAY_SECRET" "$coupon_id" "$USER_COUNT" <<'PYEOF'
import json, sys, time
import urllib.request

cache_file, users_file, base_url, gateway_secret, coupon_id, user_count = sys.argv[1:7]
user_count = int(user_count)
users = json.load(open(users_file))[:user_count]
tickets = []
success = 0

for i, user in enumerate(users):
    req = urllib.request.Request(
        f'{base_url}/api/v1/coupons/{coupon_id}/ticket',
        method='POST',
        headers={
            'Authorization': f'Bearer {user["token"]}',
            'X-Gateway-Secret': gateway_secret,
            'Content-Type': 'application/json',
        }
    )
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            data = json.loads(resp.read())
            tickets.append(data['data']['ticket'])
            success += 1
    except Exception as e:
        if i < 3:
            print(f"  [Error] User {i} ticket issue failed: {e}", file=sys.stderr)
        tickets.append(None)

cache = {'issued_at': int(time.time()), 'tickets': tickets}
json.dump(cache, open(cache_file, 'w'), indent=2)
print(f'SUCCESS_COUNT:{success}')
PYEOF
)

    local success_cnt
    success_cnt=$(echo "$out" | grep "SUCCESS_COUNT:" | cut -d':' -f2 || echo 0)
    log "  -> 발급 결과: ${success_cnt}/${USER_COUNT}개 성공"

    if [ "$success_cnt" -eq "$USER_COUNT" ]; then
      log "  ✅ 모든 AES 티켓 발급 성공!"
      return 0
    fi

    log "  ⚠️ 티켓 유실 발생 (${success_cnt}/${USER_COUNT}). Eureka 및 Gateway 라우팅 대기 중... (10초 후 재시도)"
    retry_count=$((retry_count + 1))
    sleep 10
  done

  log "❌ 치명적 오류: 최대 재시도 횟수를 초과하여 AES 티켓 발급에 실패했습니다."
  exit 1
}

warmup_gateway_ticket_path() {
  local warmup_qty=2000
  local warmup_rounds=2   # 티켓 1000개짜리 2라운드 = 2000회 요청
  log "▶ Gateway JIT 워밍업 — AES 복호화 경로 (${warmup_qty}회)"

  # 워밍업 전용 쿠폰 생성
  local warmup_coupon_id
  warmup_coupon_id=$(create_coupon "$warmup_qty" "[JIT-Warmup] Gateway AES path")
  if [ -z "$warmup_coupon_id" ]; then
    log "  ⚠️  워밍업 쿠폰 생성 실패 — 스킵"
    return 0
  fi
  log "  워밍업 쿠폰: $warmup_coupon_id (재고 ${warmup_qty}개)"

  wait_gateway_route

  # 티켓은 userId만 담긴 구조라 coupon_id 무관하게 재사용 가능
  ensure_ticket_cache "$warmup_coupon_id"

  log "  POST /api/v1/coupons/{id}/issue + X-Coupon-Ticket 경로 ${warmup_qty}회 워밍업 중 (동시 50)..."
  python3 - "$SCRIPT_DIR/ticket_cache.json" "$warmup_coupon_id" "$BASE_URL" "$warmup_rounds" <<'PYEOF'
import json, sys
from concurrent.futures import ThreadPoolExecutor, as_completed
import urllib.request

cache_file, coupon_id, base_url, rounds_str = sys.argv[1:5]
rounds = int(rounds_str)
tickets = [t for t in json.load(open(cache_file))['tickets'] if t]
all_tickets = (tickets * rounds)[:len(tickets) * rounds]
url = f'{base_url}/api/v1/coupons/{coupon_id}/issue'

def fire(ticket):
    req = urllib.request.Request(url, method='POST', headers={'X-Coupon-Ticket': ticket})
    try:
        with urllib.request.urlopen(req, timeout=10):
            pass
    except Exception:
        pass

done = 0
total = len(all_tickets)
with ThreadPoolExecutor(max_workers=50) as ex:
    futs = [ex.submit(fire, t) for t in all_tickets]
    for _ in as_completed(futs):
        done += 1
        if done % 500 == 0:
            print(f'  JIT 워밍업 진행: {done}/{total}', flush=True)
print(f'  ✅ Gateway AES 경로 JIT 워밍업 완료 ({done}회 요청)')
PYEOF
  log "  ✅ Gateway JIT 워밍업 완료"
}

verify_and_save() {
  local stage=$1
  local coupon_id=$2
  local total_qty=$3
  local expected_count=${4:-$total_qty}   # 예상 발급 건수 (stress: VUS 수, 그 외: totalQuantity)
  local stage_dir="$RESULT_DIR/$stage"
  local validation_failed=0
  mkdir -p "$stage_dir"

  echo ""
  log "[검증] $stage"

  local db_status="N/A"
  local redis_status="N/A"

  # Kafka consumer 처리 완료 대기 (최대 90초 폴링)
  local max_wait=90
  local poll_interval=3
  local waited=0
  log "  ⏳ Kafka consumer 대기 중... (최대 ${max_wait}초)"
  while [ "$waited" -lt "$max_wait" ]; do
    local _cnt
    _cnt=$(docker exec "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tA \
      -c "SELECT COUNT(*) FROM coupon_db.p_user_coupons WHERE coupon_id = '$coupon_id';" 2>/dev/null | tr -d '[:space:]')
    if [ -n "$_cnt" ] && [ "$_cnt" -ge "$expected_count" ] 2>/dev/null; then
      log "  ✅ Kafka consumer 완료 (${_cnt}/${expected_count}건, ${waited}초 소요)"
      break
    fi
    sleep "$poll_interval"
    waited=$((waited + poll_interval))
    if [ $((waited % 15)) -eq 0 ]; then
      log "  ⏳ Kafka consumer 대기 중... DB=${_cnt:-?}/${expected_count} (${waited}초)"
    fi
  done

  DB_COUNT=$(docker exec "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tA \
    -c "SELECT COUNT(*) FROM coupon_db.p_user_coupons WHERE coupon_id = '$coupon_id';" 2>/dev/null || true)
  DB_COUNT=$(echo "$DB_COUNT" | tr -d '[:space:]')

  if [ -n "$DB_COUNT" ] && [ "$DB_COUNT" != "" ]; then
    if [ "$DB_COUNT" -gt "$total_qty" ] 2>/dev/null; then
      log "  ❌ DB 발급 건수: ${DB_COUNT}건 (초과! totalQuantity: ${total_qty})"
      db_status="초과 (${DB_COUNT}건 / 예상: ${expected_count}건)"
      validation_failed=1
    elif [ "$DB_COUNT" -eq "$expected_count" ] 2>/dev/null; then
      log "  ✅ DB 발급 건수: ${DB_COUNT}건 / 예상: ${expected_count}건"
      db_status="정상 (${DB_COUNT}건 / 예상: ${expected_count}건)"
    else
      log "  ⚠️  DB 발급 건수: ${DB_COUNT}건 / 예상: ${expected_count}건 (일부 요청 실패)"
      db_status="부족 (${DB_COUNT}건 / 예상: ${expected_count}건)"
      validation_failed=1
    fi
  else
    log "  ⚠️  DB 연결 실패"
    db_status="연결 실패"
    validation_failed=1
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
      validation_failed=1
    fi
  else
    log "  ⚠️  Redis 키 없음 (재고 소진 또는 키 만료)"
    redis_status="키 없음 (재고 소진 또는 키 만료)"
  fi

  # k6-summary.json 파싱
  local k6_metrics=""
  if [ -f "$stage_dir/k6-summary.json" ]; then
    k6_metrics=$(python3 - "$stage_dir/k6-summary.json" <<'PYEOF'
import json, sys
try:
    d = json.load(open(sys.argv[1]))
    m = d.get('metrics', {})
    dur  = m.get('http_req_duration', {})
    fail = m.get('http_req_failed',   {})
    reqs = m.get('http_reqs',         {})
    it   = m.get('iterations',        {})
    vus  = m.get('vus_max',           {})
    total_reqs = int(reqs.get('count', 0))
    fail_rate  = fail.get('value', 0) * 100
    print(f"총 요청수  : {total_reqs}건")
    print(f"처리량     : {reqs.get('rate', 0):.1f} req/s")
    print(f"에러율     : {fail_rate:.2f}%")
    print(f"응답 avg   : {dur.get('avg', 0):.0f}ms")
    print(f"응답 p90   : {dur.get('p(90)', 0):.0f}ms")
    print(f"응답 p95   : {dur.get('p(95)', 0):.0f}ms")
    print(f"응답 max   : {dur.get('max', 0):.0f}ms")
    print(f"최대 VU    : {int(vus.get('max', 0))}명")
except Exception as e:
    print(f"(메트릭 파싱 실패: {e})")
PYEOF
)
  fi

  # summary.txt 저장
  cat > "$stage_dir/summary.txt" <<EOF
테스트: $stage
태그: ${TAG:-없음}
날짜: $(date '+%Y-%m-%d %H:%M:%S')
쿠폰 ID: $coupon_id
쿠폰 수량(totalQuantity): $total_qty
예상 발급 건수: $expected_count
---
[k6 메트릭]
$k6_metrics
---
[DB / Redis]
DB 발급 건수: $db_status
Redis stock: $redis_status
EOF

  # report.html 생성
  if [ -f "$stage_dir/k6-summary.json" ]; then
    python3 - "$stage_dir/k6-summary.json" "$stage_dir/report.html" \
      "$stage" "${TAG:-없음}" "$coupon_id" "$total_qty" "$expected_count" \
      "$db_status" "$redis_status" <<'PYEOF'
import json, sys
from datetime import datetime

summary_path, out_path, stage, tag, coupon_id, total_qty, expected, db_status, redis_status = sys.argv[1:]

try:
    d = json.load(open(summary_path))
    m = d.get('metrics', {})
    dur  = m.get('http_req_duration', {})
    fail = m.get('http_req_failed',   {})
    reqs = m.get('http_reqs',         {})
    vus  = m.get('vus_max',           {})

    total      = int(reqs.get('count', 0))
    rate       = reqs.get('rate', 0)
    err_pct    = fail.get('value', 0) * 100
    avg     = dur.get('avg', 0)
    p90     = dur.get('p(90)', 0)
    p95     = dur.get('p(95)', 0)
    p99     = dur.get('p(99)', 0)
    max_ms  = dur.get('max', 0)
    max_vu  = int(vus.get('max', 0))

    err_color = '#e74c3c' if err_pct >= 5 else ('#f39c12' if err_pct >= 1 else '#27ae60')
    p95_color = '#e74c3c' if p95 >= 5000 else ('#f39c12' if p95 >= 2000 else '#27ae60')
    db_ok = '부족' not in db_status and '초과' not in db_status and '실패' not in db_status
    db_color = '#27ae60' if db_ok else '#e74c3c'

    html = f"""<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<title>k6 부하 테스트 보고서 — {stage}</title>
<style>
  body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; margin: 0; background: #f5f6fa; color: #2c3e50; }}
  .header {{ background: #2c3e50; color: #fff; padding: 32px 40px; }}
  .header h1 {{ margin: 0 0 8px; font-size: 24px; }}
  .header .meta {{ font-size: 13px; opacity: 0.7; }}
  .container {{ max-width: 900px; margin: 32px auto; padding: 0 24px; }}
  .card {{ background: #fff; border-radius: 8px; padding: 24px; margin-bottom: 20px; box-shadow: 0 1px 4px rgba(0,0,0,.08); }}
  .card h2 {{ margin: 0 0 20px; font-size: 15px; text-transform: uppercase; letter-spacing: .5px; color: #7f8c8d; border-bottom: 1px solid #ecf0f1; padding-bottom: 10px; }}
  .grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 16px; }}
  .metric {{ text-align: center; padding: 16px; background: #f8f9fa; border-radius: 6px; }}
  .metric .value {{ font-size: 28px; font-weight: 700; margin-bottom: 4px; }}
  .metric .label {{ font-size: 12px; color: #7f8c8d; }}
  table {{ width: 100%; border-collapse: collapse; }}
  td, th {{ padding: 10px 12px; text-align: left; border-bottom: 1px solid #ecf0f1; font-size: 14px; }}
  th {{ font-weight: 600; color: #7f8c8d; font-size: 12px; text-transform: uppercase; }}
  .badge {{ display: inline-block; padding: 3px 10px; border-radius: 12px; font-size: 12px; font-weight: 600; color: #fff; }}
</style>
</head>
<body>
<div class="header">
  <h1>k6 부하 테스트 보고서 &nbsp;·&nbsp; {stage}</h1>
  <div class="meta">태그: {tag} &nbsp;|&nbsp; 쿠폰 ID: {coupon_id} &nbsp;|&nbsp; 생성일: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}</div>
</div>
<div class="container">

  <div class="card">
    <h2>핵심 지표</h2>
    <div class="grid">
      <div class="metric">
        <div class="value">{total:,}</div>
        <div class="label">총 요청수</div>
      </div>
      <div class="metric">
        <div class="value">{rate:.1f}</div>
        <div class="label">처리량 (req/s)</div>
      </div>
      <div class="metric">
        <div class="value" style="color:{err_color}">{err_pct:.2f}%</div>
        <div class="label">에러율</div>
      </div>
      <div class="metric">
        <div class="value" style="color:{p95_color}">{p95/1000:.2f}s</div>
        <div class="label">p95 응답시간</div>
      </div>
      <div class="metric">
        <div class="value">{max_vu}</div>
        <div class="label">최대 VU</div>
      </div>
    </div>
  </div>

  <div class="card">
    <h2>응답시간 분포</h2>
    <table>
      <tr><th>지표</th><th>값</th></tr>
      <tr><td>평균 (avg)</td><td>{avg/1000:.3f}s</td></tr>
      <tr><td>90th percentile (p90)</td><td>{p90/1000:.3f}s</td></tr>
      <tr><td>95th percentile (p95)</td><td style="color:{p95_color};font-weight:600">{p95/1000:.3f}s</td></tr>
      <tr><td>99th percentile (p99)</td><td>{p99/1000:.3f}s</td></tr>
      <tr><td>최대 (max)</td><td>{max_ms/1000:.3f}s</td></tr>
    </table>
  </div>

  <div class="card">
    <h2>데이터 정합성</h2>
    <table>
      <tr><th>항목</th><th>결과</th></tr>
      <tr><td>쿠폰 수량 (totalQuantity)</td><td>{total_qty}</td></tr>
      <tr><td>예상 발급 건수</td><td>{expected}건</td></tr>
      <tr><td>DB 발급 건수</td><td style="color:{db_color};font-weight:600">{db_status}</td></tr>
      <tr><td>Redis stock</td><td>{redis_status}</td></tr>
    </table>
  </div>

</div>
</body>
</html>"""
    open(out_path, 'w').write(html)
except Exception as e:
    print(f"(HTML 생성 실패: {e})", file=sys.stderr)
PYEOF
  fi

  log "  결과 저장: $stage_dir/"
  log "    - summary.txt  (텍스트 요약)"
  log "    - report.html  (HTML 보고서)"
  log "    - raw.json     (k6 원본 메트릭)"
  return "$validation_failed"
}

run_stage() {
  local stage=$1
  local qty=$2
  local vus=${3:-0}
  local expected_override=${4:-""}
  local coupon_override=${5:-""}
  local stage_dir="$RESULT_DIR/$stage"
  mkdir -p "$stage_dir"

  echo ""

  prepare_stage "$stage" || return 1
  echo "=========================================="
  log "▶ 단계: $stage"
  echo "=========================================="

  # 예상 발급 건수 (description + verify 양쪽에서 사용)
  local expected
  if [ -n "$expected_override" ]; then
    expected=$expected_override
  elif [ "$vus" -gt 0 ] 2>/dev/null; then
    expected=$vus
  else
    expected=$qty
  fi

  # 단계 설명 출력
  local purpose duration vu_desc
  case "$stage" in
    smoke)
      purpose="스크립트·인증·라우팅 정상 동작 확인"
      vu_desc="5명 × 1회 (총 5회 요청)"
      duration="약 30초 이내"
      ;;
    load)
      purpose="정상 부하 기준 응답시간 측정 (개선 전/후 비교 기준점)"
      vu_desc="1000명 동시 도착 (1인 1회, 총 1000회 요청)"
      duration="약 2분"
      ;;
    stress-*)
      purpose="서버 한계치 탐색 — 고유 사용자 동시 발급"
      vu_desc="${vus}명 동시 도착 (1인 1회)"
      duration="최대 2분"
      ;;
    spike)
      purpose="순간 트래픽 급증 시 대응 확인"
      vu_desc="200명 즉시 동시 도착 (1인 1회)"
      duration="최대 30초"
      ;;
  esac

  echo ""
  echo "  ┌─────────────────────────────────────────────"
  echo "  │  테스트: $stage"
  echo "  │  목적  : $purpose"
  echo "  │  시간  : $duration"
  echo "  │  VU    : $vu_desc"
  echo "  │  쿠폰  : 총 ${qty}개 준비 → 예상 ${expected}개 발급 (1인 1발급)"
  echo "  └─────────────────────────────────────────────"
  echo ""

  # 쿠폰 생성
  local coupon_id
  if [ -n "$coupon_override" ]; then
    coupon_id="$coupon_override"
    log "  (미리 생성된 쿠폰 사용: $coupon_id)"
  else
    coupon_id=$(create_coupon "$qty" "[$stage] 부하테스트 쿠폰")
    if [ -z "$coupon_id" ]; then
      log "❌ 쿠폰 생성 실패 → 중단 (서비스가 실행 중인지 확인하세요)"
      return 1
    fi
    log "  쿠폰 생성 완료: $coupon_id"
  fi

  # k6 실행 전 게이트웨이 경로 대기
  wait_gateway_route || return 1

  # 티켓 캐시 확인/갱신 (bash에서 관리 — k6 컨텍스트 격리 우회)
  ensure_ticket_cache "$coupon_id"

  # k6 실행 — load-round-* 는 k6 SCENARIO=load 로 변환
  local k6_scenario="$stage"
  if [[ "$stage" == load-round-* ]]; then
    k6_scenario="load"
  elif [[ "$stage" == stress-* ]]; then
    k6_scenario="stress"
  fi

  local k6_exit=0
  k6 run \
    --env SCENARIO="$k6_scenario" \
    --env VUS="$expected" \
    --env P95_LIMIT="$P95_LIMIT_MS" \
    --env COUPON_ID="$coupon_id" \
    --env BASE_URL="$BASE_URL" \
    --env GATEWAY_SECRET="$GATEWAY_SECRET" \
    --env SKIP_SLEEP="${SKIP_SETUP_SLEEP:-false}" \
    --out "json=$stage_dir/raw.json" \
    --summary-export "$stage_dir/k6-summary.json" \
    "$SCRIPT_DIR/03-coupon-issue.js" || k6_exit=$?

  # DB/Redis 검증 + summary.txt 저장
  local verify_exit=0
  verify_and_save "$stage" "$coupon_id" "$qty" "$expected" || verify_exit=$?

  # Smoke/Load Test 실패 시 중단
  if [[ "$stage" == "smoke" || "$stage" == "load" ]] && [ "$k6_exit" -ne 0 ]; then
    log "❌ $stage 임계값 초과 → 다음 단계로 넘어가지 않습니다"
    return 1
  fi

  if [ "$k6_exit" -ne 0 ] || [ "$verify_exit" -ne 0 ]; then
    log "❌ $stage 실패 (k6=$k6_exit, 정합성=$verify_exit)"
    return 1
  fi

  return 0
}

# ============================================================
# 인프라 제어 — 부하 테스트용 최소 서비스만 기동
# ============================================================
load_infra_start() {
  echo ""
  echo "=========================================="
  log "  [load-start] 부하 테스트용 서비스 기동"
  echo "  시작: gateway + coupon-service + 필수 인프라"
  echo "        (keycloak, postgres, redis, kafka, zookeeper, eureka, config)"
  echo "  스킵: user/drop/order/product/payment/raffle/notification"
  echo "        zipkin/prometheus/grafana/loki/promtail"
  echo "        kafka-ui/kafka-rest-proxy/toss-wiremock"
  echo "=========================================="
  echo ""

  log "▶ 기존 실행 중인 서비스 모두 중지..."
  $COMPOSE down

  log "▶ 필수 서비스만 기동 중 (compose가 의존성 자동 해결)..."
  local scale_opt=""
  if [ "$COUPON_SCALE" -gt 1 ] 2>/dev/null; then
    scale_opt="--scale coupon-service=$COUPON_SCALE"
  fi
  $COMPOSE up -d $scale_opt gateway coupon-service nginx

  echo ""
  log "  ✅ 기동 명령 완료"
  log "  ⏳ keycloak 초기화 + gateway 헬스체크 통과까지 최대 2분 소요됩니다"
  log "  ℹ️  준비 확인: curl -s http://localhost/actuator/health"
}

load_infra_stop() {
  echo ""
  log "▶ [load-stop] 모든 서비스 종료..."
  $COMPOSE down
  log "  ✅ 종료 완료"
}

# ------------------------------------------------------------
# 헬스체크 대기 함수 (setup 시 사용)
# ------------------------------------------------------------
wait_container_healthy() {
  local container=$1
  local timeout=${2:-180}
  local elapsed=0

  log "  ⏳ $container 준비 대기 중..."
  until docker inspect "$container" --format '{{.State.Health.Status}}' 2>/dev/null | grep -q "^healthy$"; do
    if [ "$elapsed" -ge "$timeout" ]; then
      log "  ❌ $container 헬스체크 타임아웃 (${timeout}s 초과)"
      exit 1
    fi
    sleep 5
    elapsed=$((elapsed + 5))
  done
  log "  ✅ $container 준비 완료"
}

# ============================================================
# 쿠폰 시나리오 — 유저 준비 + Sentry 비활성화 (setup 전용)
# ============================================================
coupon_setup() {
  local NEEDED_ROLE="USER"

  # keycloak 및 user-service 상태 확인 후 미기동 시 기동
  local keycloak_status=$(docker inspect -f '{{.State.Status}}' omc-keycloak 2>/dev/null || echo "not_found")
  local user_status=$(docker inspect -f '{{.State.Status}}' omc-user-service 2>/dev/null || echo "not_found")

  if [ "$keycloak_status" != "running" ] || [ "$user_status" != "running" ]; then
    log "⚠️  keycloak 또는 user-service가 실행 중이 아닙니다."
    log "▶ keycloak 및 user-service 기동을 시작합니다..."
    $COMPOSE up -d keycloak user-service

    wait_container_healthy omc-keycloak 180
    wait_container_healthy omc-user-service 180
  fi

  echo ""
  echo "=========================================="
  log "  [setup] 유저 준비 + Sentry 비활성화"
  echo "=========================================="
  echo ""

  # Sentry 비활성화 (restore는 사용자가 수동으로 실행)
  sentry_disable || { log "❌ Sentry 비활성화/라우팅 준비 실패"; return 1; }
  echo ""

  # 사전 검증: users.json이 있으면 캐시된 Access Token의 유효성을 테스트해 세션 만료 여부를 자동 확인
  if [ -f "$SCRIPT_DIR/users.json" ]; then
    local test_token
    test_token=$(python3 -c "import json; d=json.load(open('$SCRIPT_DIR/users.json')); print(d[0].get('token','')) if d else print('')" 2>/dev/null)

    if [ -n "$test_token" ]; then
      log "  🧪 기존 Access Token 유효성 사전 검증 (API 호출)..."
      local test_code
      test_code=$(curl -s -o /dev/null -w "%{http_code}" \
        -H "Authorization: Bearer $test_token" \
        "$BASE_URL/api/v1/coupons/me" 2>/dev/null || echo "000")

      if [ "$test_code" = "401" ] || [ "$test_code" = "403" ] || [ "$test_code" = "000" ]; then
        log "  ⚠️  사전 토큰 검증 실패 (HTTP $test_code) → Keycloak/DB 리셋 감지"
        log "  🧹 기존 캐시(users.json / ticket_cache.json)를 자동 제거합니다."
        rm -f "$SCRIPT_DIR/users.json"
        rm -f "$SCRIPT_DIR/ticket_cache.json"
      else
        log "  ✅ 사전 토큰 검증 성공 (HTTP $test_code)"
      fi
    else
      log "  ⚠️  캐시된 토큰 없음 → 리셋 처리"
      rm -f "$SCRIPT_DIR/users.json"
      rm -f "$SCRIPT_DIR/ticket_cache.json"
    fi
  fi

  # 유저 확인 → role 불일치/부족이면 재생성, 맞으면 토큰만 갱신
  if [ -f "$SCRIPT_DIR/users.json" ]; then
    EXISTING=$(python3 -c "import json; print(len(json.load(open('$SCRIPT_DIR/users.json'))))" 2>/dev/null || echo "0")
    EXISTING_ROLE=$(python3 -c "import json; d=json.load(open('$SCRIPT_DIR/users.json')); print(d[0].get('role','UNKNOWN')) if d else print('UNKNOWN')" 2>/dev/null || echo "UNKNOWN")

    if [ "$EXISTING_ROLE" != "$NEEDED_ROLE" ]; then
      log "⚠️  users.json role 불일치 (${EXISTING_ROLE} ≠ ${NEEDED_ROLE}) → 재생성"
      rm -f "$SCRIPT_DIR/users.json"
      COUNT=$USER_COUNT ROLE=$NEEDED_ROLE REQUEST_INTERVAL_MS=$REQUEST_INTERVAL_MS node "$SCRIPT_DIR/generator.js" || { log "❌ 유저 생성 실패"; exit 1; }
      _verify_users_json
    elif [ "$EXISTING" -lt "$USER_COUNT" ]; then
      log "⚠️  users.json 유저 부족 (${EXISTING}명 < ${USER_COUNT}명) → 재생성"
      rm -f "$SCRIPT_DIR/users.json"
      COUNT=$USER_COUNT ROLE=$NEEDED_ROLE REQUEST_INTERVAL_MS=$REQUEST_INTERVAL_MS node "$SCRIPT_DIR/generator.js" || { log "❌ 유저 생성 실패"; exit 1; }
      _verify_users_json
    else
      log "✅ users.json 존재 (${EXISTING}명, ROLE=${EXISTING_ROLE}) → 토큰 갱신 중..."
      log "  (약 1분 소요)"
      RELOGIN=true node "$SCRIPT_DIR/generator.js" || { log "❌ 토큰 갱신 실패"; exit 1; }
    fi
  else
    log "▶ users.json 없음 → 유저 ${USER_COUNT}명 생성 중..."
    log "  (X-Load-Test 헤더로 Rate Limit 우회, 간격 ${REQUEST_INTERVAL_MS}ms)"
    COUNT=$USER_COUNT ROLE=$NEEDED_ROLE REQUEST_INTERVAL_MS=$REQUEST_INTERVAL_MS node "$SCRIPT_DIR/generator.js" || { log "❌ 유저 생성 실패"; exit 1; }
    _verify_users_json
  fi

  echo ""
  log "✅ setup 완료 — 이제 개별 단계를 실행하세요:"
  log "   ./k6/run.sh coupon smoke   [태그]"
  log "   ./k6/run.sh coupon load    [태그]"
  log "   ./k6/run.sh coupon stress-400 [태그]"
  log "   ..."
  log "   ./k6/run.sh coupon restore   # 모두 끝난 후"
}

# ============================================================
# 쿠폰 시나리오 — 단일 단계 실행 (setup 없이)
# ============================================================
coupon_run_stage() {
  local stage=$1
  local stage_exit=0

  # users.json 없으면 setup 먼저 안내
  if [ ! -f "$SCRIPT_DIR/users.json" ]; then
    log "❌ users.json 없음 → 먼저 setup을 실행하세요:"
    log "   ./k6/run.sh coupon setup"
    exit 1
  fi

  echo ""
  echo "=========================================="
  echo "  k6 쿠폰 부하 테스트 — $stage"
  if [ -n "$TAG" ]; then
    echo "  태그: $TAG"
  fi
  echo "  결과 폴더: $RESULT_DIR"
  echo "=========================================="

  case "$stage" in
    smoke)        run_stage "smoke"       $COUPON_LOAD_QTY  0  5 || stage_exit=$? ;;
    load)         run_stage "load"        $COUPON_LOAD_QTY || stage_exit=$? ;;
    load-repeat)
      local rounds=${LOAD_REPEAT_ROUNDS:-$LOAD_REPEAT}
      log "▶ load-repeat: ${rounds}회 반복 (쿠폰 ${COUPON_LOAD_QTY}개 × ${rounds}회)"

      # Gateway AES 복호화 경로 JIT 워밍업 (기존 warmup은 GET/JWT 경로만 워밍업해서 미적용)
      warmup_gateway_ticket_path

      # [준비] 10개 쿠폰 일괄 선발급
      log "  ▶ [준비] 10라운드용 쿠폰 ${rounds}개 일괄 선발급 중..."
      declare -a precreated_coupons
      for i in $(seq 1 "$rounds"); do
        local c_id=$(create_coupon "$COUPON_LOAD_QTY" "[load-round-${i}] 부하테스트 쿠폰")
        precreated_coupons+=("$c_id")
      done

      # [격리] 부하 중 불필요한 서비스 모두 중지하여 CPU 회수
      #  - 중단/종료 시 반드시 복구 (EXIT trap)
      trap '$COMPOSE start keycloak user-service drop-service order-service payment-service product-service raffle-service >/dev/null 2>&1 || true' EXIT
      log "  ▶ [격리] 부하 중 불필요 서비스 전면 중지 (keycloak, user 등 → 코어 확보)"
      $COMPOSE stop keycloak user-service drop-service order-service payment-service product-service raffle-service >/dev/null 2>&1 || true

      SKIP_SETUP_SLEEP=false
      for i in $(seq 1 "$rounds"); do
        log "  ▶ round ${i}/${rounds}"
        local current_coupon="${precreated_coupons[$((i-1))]}"
        run_stage "load-round-${i}" "$COUPON_LOAD_QTY" "" "" "$current_coupon" || stage_exit=$?
        if [ "$stage_exit" -ne 0 ]; then
          break
        fi
        SKIP_SETUP_SLEEP=true
        if [ "$i" -lt "$rounds" ]; then
          cooldown_between_stages || { stage_exit=1; break; }
        fi
      done

      log "  ▶ keycloak 복구..."
      $COMPOSE start keycloak >/dev/null 2>&1 || true
      trap - EXIT
      ;;
    stress-200)   run_stage "stress-200"  $COUPON_STRESS_QTY  200  || stage_exit=$? ;;
    stress-400)   run_stage "stress-400"  $COUPON_STRESS_QTY  400  || stage_exit=$? ;;
    stress-600)   run_stage "stress-600"  $COUPON_STRESS_QTY  600  || stage_exit=$? ;;
    stress-800)   run_stage "stress-800"  $COUPON_STRESS_QTY  800  || stage_exit=$? ;;
    stress-1000)  run_stage "stress-1000" $COUPON_STRESS_QTY 1000  || stage_exit=$? ;;
    spike)        run_stage "spike"       $COUPON_LOAD_QTY 0 200  || stage_exit=$? ;;
  esac

  echo ""
  log "  결과 위치: $RESULT_DIR"
  return "$stage_exit"
}

# ============================================================
# 쿠폰 시나리오 — 전체 실행 (setup + 전체 단계 + restore)
# ============================================================
coupon_all() {
  # setup 중 중단되어도 Sentry를 복원한다.
  trap 'sentry_restore' EXIT
  coupon_setup || return 1

  echo ""
  echo "=========================================="
  echo "  k6 쿠폰 부하 테스트 — 전체 실행"
  if [ -n "$TAG" ]; then
    echo "  태그: $TAG"
  fi
  echo "  결과 폴더: $RESULT_DIR"
  echo "=========================================="
  echo ""

  local suite_failed=0
  local failed_stages=()
  local completed=0

  run_all_stage() {
    if [ "$completed" -gt 0 ]; then
      if ! cooldown_between_stages; then
        suite_failed=1
        failed_stages+=("$1(cooldown)")
        return 0
      fi
    fi
    completed=$((completed + 1))
    if ! run_stage "$@"; then
      suite_failed=1
      failed_stages+=("$1")
    fi
  }

  run_all_stage "smoke"       "$COUPON_LOAD_QTY"   0    5
  run_all_stage "load"        "$COUPON_LOAD_QTY"
  run_all_stage "stress-200"  "$COUPON_STRESS_QTY" 200
  run_all_stage "stress-400"  "$COUPON_STRESS_QTY" 400
  run_all_stage "stress-600"  "$COUPON_STRESS_QTY" 600
  run_all_stage "stress-800"  "$COUPON_STRESS_QTY" 800
  run_all_stage "stress-1000" "$COUPON_STRESS_QTY" 1000
  run_all_stage "spike"       "$COUPON_LOAD_QTY"   0    200

  trap - EXIT
  sentry_restore
  echo ""
  echo "=========================================="
  if [ "$suite_failed" -eq 0 ]; then
    log "  ✅ 모든 테스트 PASS"
  else
    log "  ❌ 전체 테스트 FAIL: ${failed_stages[*]}"
  fi
  log "  결과 위치: $RESULT_DIR"
  echo "=========================================="
  echo ""

  if [ "$AUTO_CLEANUP" = "true" ]; then
    node "$SCRIPT_DIR/cleanup.js"
  else
    log "cleanup 스킵 (AUTO_CLEANUP=true로 자동 정리 가능)"
  fi

  return "$suite_failed"
}

# ============================================================
# 진입점
# ============================================================
case "$SCENARIO_TYPE" in
  coupon)
    case "$COMMAND" in
      setup)      coupon_setup ;;
      restore)    sentry_restore ;;
      all)        coupon_all ;;
      load-start) load_infra_start ;;
      load-stop)  load_infra_stop ;;
      smoke|load|load-repeat|stress-200|stress-400|stress-600|stress-800|stress-1000|spike)
               coupon_run_stage "$COMMAND" ;;
      "")
        echo "사용법: ./k6/run.sh coupon <command> [태그]"
        echo ""
        echo "Commands:"
        echo "  load-start        부하 테스트용 최소 서비스만 기동 (CPU 절약)"
        echo "  load-stop         모든 서비스 종료"
        echo "  setup             유저 준비 + Sentry 비활성화 (개별 실행 전 1회)"
        echo "  restore           Sentry 복원 (개별 실행 모두 끝난 후)"
        echo "  all [태그]        setup + 전체 단계 + restore"
        echo "  smoke [태그]      smoke 단계만"
        echo "  load [태그]       load 단계만"
        echo "  load-repeat [N] [태그]  load N회 반복 (기본 LOAD_REPEAT=${LOAD_REPEAT}회, 매 회차 새 쿠폰)"
        echo "  stress-200 [태그] stress 200VU만"
        echo "  stress-400 [태그] stress 400VU만"
        echo "  stress-600 [태그] stress 600VU만"
        echo "  stress-800 [태그] stress 800VU만"
        echo "  stress-1000 [태그]stress 1000VU만"
        echo "  spike [태그]      spike 단계만"
        echo ""
        echo "예시 — 전체 자동 실행:"
        echo "  ./k6/run.sh coupon all pool-size-10"
        echo ""
        echo "예시 — 단계별 수동 실행:"
        echo "  ./k6/run.sh coupon setup"
        echo "  ./k6/run.sh coupon smoke   pool-size-10"
        echo "  ./k6/run.sh coupon stress-400 pool-size-10"
        echo "  ./k6/run.sh coupon restore"
        exit 1
        ;;
      *)
        echo "❌ 알 수 없는 command: $COMMAND"
        echo "   ./k6/run.sh coupon 를 인수 없이 실행하면 도움말이 표시됩니다."
        exit 1
        ;;
    esac
    ;;
  *)
    echo "❌ 알 수 없는 시나리오: $SCENARIO_TYPE"
    echo "지원 시나리오: coupon"
    exit 1
    ;;
esac
