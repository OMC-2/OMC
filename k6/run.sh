#!/bin/bash
# k6/run.sh — 쿠폰 부하 테스트 자동화
#
# 사용법:
#   ./k6/run.sh coupon <command> [태그]
#
# Commands:
#   setup         유저 준비 + Sentry 비활성화 (개별 단계 실행 전 1회만)
#   restore       Sentry 복원 (개별 단계 실행 모두 끝난 후)
#   all [tag]     setup + 전체 단계 + restore (한 번에 전부)
#   smoke [tag]   smoke 단계만 실행 (setup 없이)
#   load [tag]    load 단계만 실행
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
#   ./k6/run.sh coupon stress-400 pool-size-10
#   ./k6/run.sh coupon restore                   # 마지막에 1회

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
PROJECT_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
SCENARIO_TYPE=${1:-"coupon"}
COMMAND=${2:-""}    # setup | restore | all | smoke | load | stress-* | spike
TAG=${3:-""}

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
  local expected_count=${4:-$total_qty}   # 예상 발급 건수 (stress: VUS 수, 그 외: totalQuantity)
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
    if [ "$DB_COUNT" -gt "$total_qty" ] 2>/dev/null; then
      log "  ❌ DB 발급 건수: ${DB_COUNT}건 (초과! totalQuantity: ${total_qty})"
      db_status="초과 (${DB_COUNT}건 / 예상: ${expected_count}건)"
    elif [ "$DB_COUNT" -eq "$expected_count" ] 2>/dev/null; then
      log "  ✅ DB 발급 건수: ${DB_COUNT}건 / 예상: ${expected_count}건"
      db_status="정상 (${DB_COUNT}건 / 예상: ${expected_count}건)"
    else
      log "  ⚠️  DB 발급 건수: ${DB_COUNT}건 / 예상: ${expected_count}건 (일부 요청 실패)"
      db_status="부족 (${DB_COUNT}건 / 예상: ${expected_count}건)"
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
}

run_stage() {
  local stage=$1
  local qty=$2
  local vus=${3:-0}
  local expected_override=${4:-""}
  local stage_dir="$RESULT_DIR/$stage"
  mkdir -p "$stage_dir"

  echo ""
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
      vu_desc="0 → 100명 ramp-up → 1분 유지 → 0명"
      duration="약 2분"
      ;;
    stress-*)
      purpose="서버 한계치 탐색 — 에러율·응답시간 변화 관찰"
      vu_desc="0 → ${vus}명 ramp-up → 1분 유지 → 0명"
      duration="약 1분 30초"
      ;;
    spike)
      purpose="순간 트래픽 급증 시 대응 확인"
      vu_desc="0 → 200명 (5초 급증) → 30초 유지 → 0명"
      duration="약 40초"
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
      --summary-export "$stage_dir/k6-summary.json" \
      "$SCRIPT_DIR/03-coupon-issue.js" || k6_exit=$?
  else
    k6 run \
      --env SCENARIO="$stage" \
      --env COUPON_ID="$coupon_id" \
      --env BASE_URL="$BASE_URL" \
      --env GATEWAY_SECRET="$GATEWAY_SECRET" \
      --out "json=$stage_dir/raw.json" \
      --summary-export "$stage_dir/k6-summary.json" \
      "$SCRIPT_DIR/03-coupon-issue.js" || k6_exit=$?
  fi

  # DB/Redis 검증 + summary.txt 저장
  verify_and_save "$stage" "$coupon_id" "$qty" "$expected"

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
# 쿠폰 시나리오 — 유저 준비 + Sentry 비활성화 (setup 전용)
# ============================================================
coupon_setup() {
  local NEEDED_ROLE="USER"

  echo ""
  echo "=========================================="
  log "  [setup] 유저 준비 + Sentry 비활성화"
  echo "=========================================="
  echo ""

  # Sentry 비활성화 (restore는 사용자가 수동으로 실행)
  sentry_disable
  echo ""

  # 유저 확인 → role 불일치/부족이면 재생성, 맞으면 토큰만 갱신
  if [ -f "$SCRIPT_DIR/users.json" ]; then
    EXISTING=$(python3 -c "import json; print(len(json.load(open('$SCRIPT_DIR/users.json'))))" 2>/dev/null || echo "0")
    EXISTING_ROLE=$(python3 -c "import json; d=json.load(open('$SCRIPT_DIR/users.json')); print(d[0].get('role','UNKNOWN')) if d else print('UNKNOWN')" 2>/dev/null || echo "UNKNOWN")

    if [ "$EXISTING_ROLE" != "$NEEDED_ROLE" ]; then
      log "⚠️  users.json role 불일치 (${EXISTING_ROLE} ≠ ${NEEDED_ROLE}) → 재생성"
      rm -f "$SCRIPT_DIR/users.json"
      COUNT=$USER_COUNT ROLE=$NEEDED_ROLE node "$SCRIPT_DIR/generator.js" || { log "❌ 유저 생성 실패"; exit 1; }
      _verify_users_json
    elif [ "$EXISTING" -lt "$USER_COUNT" ]; then
      log "⚠️  users.json 유저 부족 (${EXISTING}명 < ${USER_COUNT}명) → 재생성"
      rm -f "$SCRIPT_DIR/users.json"
      COUNT=$USER_COUNT ROLE=$NEEDED_ROLE node "$SCRIPT_DIR/generator.js" || { log "❌ 유저 생성 실패"; exit 1; }
      _verify_users_json
    else
      log "✅ users.json 존재 (${EXISTING}명, ROLE=${EXISTING_ROLE}) → 토큰 갱신 중..."
      log "  (약 1분 소요)"
      RELOGIN=true node "$SCRIPT_DIR/generator.js" || { log "❌ 토큰 갱신 실패"; exit 1; }
    fi
  else
    log "▶ users.json 없음 → 유저 ${USER_COUNT}명 생성 중..."
    log "  (X-Load-Test 헤더로 Rate Limit 우회 — 약 1분 소요)"
    COUNT=$USER_COUNT ROLE=$NEEDED_ROLE node "$SCRIPT_DIR/generator.js" || { log "❌ 유저 생성 실패"; exit 1; }
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
    smoke)        run_stage "smoke"       $COUPON_LOAD_QTY  0  5 ;;
    load)         run_stage "load"        $COUPON_LOAD_QTY ;;
    stress-200)   run_stage "stress-200"  $COUPON_STRESS_QTY  200  || true ;;
    stress-400)   run_stage "stress-400"  $COUPON_STRESS_QTY  400  || true ;;
    stress-600)   run_stage "stress-600"  $COUPON_STRESS_QTY  600  || true ;;
    stress-800)   run_stage "stress-800"  $COUPON_STRESS_QTY  800  || true ;;
    stress-1000)  run_stage "stress-1000" $COUPON_STRESS_QTY 1000  || true ;;
    spike)        run_stage "spike"       $COUPON_LOAD_QTY         || true ;;
  esac

  echo ""
  log "  결과 위치: $RESULT_DIR"
}

# ============================================================
# 쿠폰 시나리오 — 전체 실행 (setup + 전체 단계 + restore)
# ============================================================
coupon_all() {
  coupon_setup

  echo ""
  echo "=========================================="
  echo "  k6 쿠폰 부하 테스트 — 전체 실행"
  if [ -n "$TAG" ]; then
    echo "  태그: $TAG"
  fi
  echo "  결과 폴더: $RESULT_DIR"
  echo "=========================================="
  echo ""

  # 중단/완료 시 Sentry 복원
  trap 'sentry_restore' EXIT

  # 전체 단계 (smoke/load 실패 시 중단)
  run_stage "smoke"       $COUPON_LOAD_QTY  0  5
  run_stage "load"        $COUPON_LOAD_QTY
  run_stage "stress-200"  $COUPON_STRESS_QTY  200  || true
  run_stage "stress-400"  $COUPON_STRESS_QTY  400  || true
  run_stage "stress-600"  $COUPON_STRESS_QTY  600  || true
  run_stage "stress-800"  $COUPON_STRESS_QTY  800  || true
  run_stage "stress-1000" $COUPON_STRESS_QTY 1000  || true
  run_stage "spike"       $COUPON_LOAD_QTY         || true

  trap - EXIT
  sentry_restore
  echo ""
  echo "=========================================="
  log "  모든 테스트 완료"
  log "  결과 위치: $RESULT_DIR"
  echo "=========================================="
  echo ""

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
  coupon)
    case "$COMMAND" in
      setup)   coupon_setup ;;
      restore) sentry_restore ;;
      all)     coupon_all ;;
      smoke|load|stress-200|stress-400|stress-600|stress-800|stress-1000|spike)
               coupon_run_stage "$COMMAND" ;;
      "")
        echo "사용법: ./k6/run.sh coupon <command> [태그]"
        echo ""
        echo "Commands:"
        echo "  setup             유저 준비 + Sentry 비활성화 (개별 실행 전 1회)"
        echo "  restore           Sentry 복원 (개별 실행 모두 끝난 후)"
        echo "  all [태그]        setup + 전체 단계 + restore"
        echo "  smoke [태그]      smoke 단계만"
        echo "  load [태그]       load 단계만"
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
