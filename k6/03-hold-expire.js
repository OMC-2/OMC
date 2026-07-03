import http from 'k6/http';
import { check } from 'k6';
import { Rate, Counter } from 'k6/metrics';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';

// Hold 만료 후 재고 복구 검증
//
// =====================================================================
// 시나리오 개요
// =====================================================================
//
// Phase 1 (hold_preempt): HOLD_COUNT명이 선점 요청 → hold 상태로 재고 점유
//                         (결제를 하지 않으므로 holdTtlSec 뒤 자동 만료)
//
// 대기: holdTtlSec 경과 + HoldExpireScheduler 실행 여유 (기본 15초 버퍼)
//
// Phase 2 (hold_recover): 다른 HOLD_COUNT명이 동일 드롭 선점 시도
//                         → 복구된 재고로 202 Accepted를 받아야 함
//
// 검증 기준:
//   - hold_preempt_success count == HOLD_COUNT  (Phase1 전원 선점 성공)
//   - hold_recover_success count == HOLD_COUNT  (복구 재고 전원 재선점 성공)
//
// =====================================================================
// 전제 조건
// =====================================================================
//
//   1. 드롭 생성 시 아래 설정 필수:
//        totalQty  = HOLD_COUNT      (Phase1이 재고를 꽉 채워야 Phase2 검증이 의미 있음)
//        holdTtlSec = HOLD_TTL_SEC   (k6 실행 시 -e HOLD_TTL_SEC 값과 동일)
//
//   2. 드롭이 OPEN 상태이어야 함 (startAt 경과 확인)
//
//   3. users.json에 HOLD_COUNT * 2개 이상의 유저 토큰이 있어야 함
//      - Phase1: users[0] ~ users[HOLD_COUNT - 1]
//      - Phase2: users[HOLD_COUNT] ~ users[HOLD_COUNT * 2 - 1]
//
// =====================================================================
// 실행 예시
// =====================================================================
//
//   # 드롭 생성 (holdTtlSec=15, totalQty=50)
//   START_AT=$(python3 -c "from datetime import datetime, timezone, timedelta; \
//     kst=timezone(timedelta(hours=9)); \
//     print((datetime.now(kst)+timedelta(seconds=15)).strftime('%Y-%m-%dT%H:%M:%S'))")
//
//   # k6 실행 (총 소요 시간: HOLD_TTL_SEC + ~30s)
//   k6 run \
//     -e DROP_ID=<dropId> \
//     -e HOLD_TTL_SEC=15 \
//     -e HOLD_COUNT=50 \
//     k6/03-hold-expire.js
//
// =====================================================================
// 결과 확인 (Redis CLI)
// =====================================================================
//
//   docker exec -it omc-redis redis-cli
//   GET stock:<DROP_ID>       # Phase2 완료 후 0이어야 함
//   SMEMBERS purchased:<DROP_ID>  # HOLD_COUNT * 2개 entries (Phase1 + Phase2 선점자)

const BASE       = __ENV.BASE_URL       || 'http://localhost:8080';
const GW_SECRET  = __ENV.GATEWAY_SECRET || 'local-secret';
const DROP_ID    = __ENV.DROP_ID        || 'CHANGE_ME';
const HOLD_COUNT = parseInt(__ENV.HOLD_COUNT    || '50', 10);
const HOLD_TTL_SEC = parseInt(__ENV.HOLD_TTL_SEC || '15', 10);

// Phase2 시작 시각: holdTtlSec 만료 + HoldExpireScheduler 실행 여유 15초
const PHASE2_START = `${HOLD_TTL_SEC + 15}s`;

const holdPreemptSuccess  = new Counter('hold_preempt_success');   // Phase1 선점 성공 수
const holdRecoverSuccess  = new Counter('hold_recover_success');   // Phase2 복구 후 선점 성공 수
const holdPreemptFail     = new Counter('hold_preempt_fail');      // Phase1 예상 못한 실패
const holdRecoverFail     = new Counter('hold_recover_fail');      // Phase2 예상 못한 실패 (복구 미완료 신호)
const holdRecoveryRate    = new Rate('hold_recovery_rate');        // Phase2 성공률 (복구 검증)

const userData = new SharedArray('users', function () {
  return JSON.parse(open('./users.json'));
});

export const options = {
  scenarios: {
    hold_preempt: {
      executor: 'shared-iterations',
      vus: HOLD_COUNT,
      iterations: HOLD_COUNT,
      startTime: '0s',
      maxDuration: '60s',
      exec: 'runHoldPreempt',
    },
    hold_recover: {
      executor: 'shared-iterations',
      vus: HOLD_COUNT,
      iterations: HOLD_COUNT,
      startTime: PHASE2_START,
      maxDuration: '60s',
      exec: 'runHoldRecover',
    },
  },
  thresholds: {
    hold_preempt_success: [`count>=${HOLD_COUNT}`],           // Phase1 전원 선점 성공
    hold_recover_success: [`count>=${HOLD_COUNT}`],           // Phase2 전원 재선점 성공 (복구 완료 증명)
    hold_recovery_rate:   ['rate>=1'],                        // 복구 후 선점 실패 0건
  },
};

export function setup() {
  const preemptTokens = [];
  const recoverTokens = [];
  for (let i = 0; i < HOLD_COUNT; i++) {
    preemptTokens.push(userData[i].token);
  }
  for (let i = HOLD_COUNT; i < HOLD_COUNT * 2; i++) {
    recoverTokens.push(userData[i].token);
  }
  return { preemptTokens, recoverTokens };
}

// =====================================================================
// Phase 1: 선점만 수행 (결제 없이 hold 상태 유지 → TTL 만료 후 재고 복구 유도)
// =====================================================================
export function runHoldPreempt(data) {
  // iterationInTest: 시나리오 내 반복 번호 (0-based, 50회 = 0~49 보장)
  const idx   = exec.scenario.iterationInTest;
  const token = data.preemptTokens[idx];

  const res = http.post(
    `${BASE}/api/v1/drops/${DROP_ID}/purchase`,
    null,
    { headers: { 'Authorization': `Bearer ${token}`, 'X-Gateway-Secret': GW_SECRET } }
  );

  check(res, { '[Phase1] 202 선점 성공': r => r.status === 202 });

  if (res.status === 202) {
    holdPreemptSuccess.add(1);
  } else {
    holdPreemptFail.add(1);
    console.warn(`[hold_expire][Phase1] 예상 못한 응답: status=${res.status} body=${res.body}`);
  }
}

// =====================================================================
// Phase 2: 다른 유저로 선점 시도 → 복구된 재고로 성공해야 함
// =====================================================================
export function runHoldRecover(data) {
  // iterationInTest: 시나리오 내 반복 번호 (0-based, 50회 = 0~49 보장)
  const idx   = exec.scenario.iterationInTest;
  const token = data.recoverTokens[idx];

  const res = http.post(
    `${BASE}/api/v1/drops/${DROP_ID}/purchase`,
    null,
    { headers: { 'Authorization': `Bearer ${token}`, 'X-Gateway-Secret': GW_SECRET } }
  );

  const ok = res.status === 202;
  check(res, { '[Phase2] 202 선점 성공 (복구 재고)': r => r.status === 202 });

  holdRecoveryRate.add(ok);

  if (ok) {
    holdRecoverSuccess.add(1);
  } else {
    holdRecoverFail.add(1);
    console.warn(`[hold_expire][Phase2] 예상 못한 응답: status=${res.status} body=${res.body} — hold 미복구?`);
  }
}
