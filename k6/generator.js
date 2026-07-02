// 부하 테스트용 유저 생성기 (Node.js 스크립트)
//
// k6는 파일 쓰기를 지원하지 않으므로, 이 스크립트는 Node.js로 실행한다.
// 생성된 users.json은 .gitignore에 등록하여 로컬에서만 사용한다.
//
// 실행 방법:
//   node k6/generator.js
//
// 환경변수 (선택):
//   BASE_URL=http://localhost:8080 node k6/generator.js
//   COUNT=500 node k6/generator.js
//   REQUEST_INTERVAL_MS=400 node k6/generator.js  # Gateway RedisRateLimiter(10, 20) 대응, 기본 400ms(≈5 req/sec)
//
// 실행 후 생성 파일:
//   k6/users.json — 이메일/토큰 목록 (Git 비공개, .gitignore 등록 필요)
//
// 이후 부하 테스트 실행:
//   k6 run -e SCENARIO=purchase -e DROP_ID=xxx k6/02-product-inventory.js

const https = require('https');
const http  = require('http');
const fs    = require('fs');
const path  = require('path');

const BASE           = process.env.BASE_URL        || 'http://localhost:8080';
const COUNT          = parseInt(process.env.COUNT  || '1000', 10);
const ADMIN_SECRET   = process.env.ADMIN_SECRET    || 'local-admin-secret';
const GATEWAY_SECRET = process.env.GATEWAY_SECRET  || 'local-secret';
// ROLE=USER → 일반 signup (쿠폰 발급 등 USER 역할 필요한 테스트용)
// ROLE=ADMIN(기본) → admin/signup (drop 구매 등 역할 무관 테스트용)
const ROLE           = process.env.ROLE            || 'ADMIN';
// X-Load-Test 헤더를 전송해 게이트웨이 Rate Limit을 우회하므로 간격을 50ms로 단축
// (게이트웨이 KeyResolver가 Mono.empty() 반환 → deny-empty-key:false → 스킵)
const REQUEST_INTERVAL_MS = parseInt(process.env.REQUEST_INTERVAL_MS || '50', 10);
const LOAD_TEST_SECRET = 'local-loadtest-secret';
const OUTPUT_PATH    = path.join(__dirname, 'users.json');

// HTTP 요청 헬퍼
function request(method, url, body, headers = {}) {
  return new Promise((resolve, reject) => {
    const parsed = new URL(url);
    const lib = parsed.protocol === 'https:' ? https : http;
    const data = body ? JSON.stringify(body) : null;

    const options = {
      hostname: parsed.hostname,
      port:     parsed.port,
      path:     parsed.pathname,
      method,
      headers: {
        'Content-Type': 'application/json',
        ...headers,
        ...(data ? { 'Content-Length': Buffer.byteLength(data) } : {}),
      },
    };

    const req = lib.request(options, (res) => {
      let raw = '';
      res.on('data', (chunk) => raw += chunk);
      res.on('end', () => {
        try { resolve({ status: res.statusCode, body: JSON.parse(raw) }); }
        catch { resolve({ status: res.statusCode, body: raw }); }
      });
    });

    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}

// 유저 1명 생성 + 로그인 → 토큰 반환
async function createUser(index) {
  const email    = `loadtest_${Date.now()}_${index}@test.com`;
  const password = 'Test1234!';
  const nickname = `loadtest_${index}`;

  // 회원가입
  // ROLE=USER → 일반 signup (USER 역할) / ROLE=ADMIN(기본) → admin/signup (ADMIN 역할)
  // X-Load-Test 헤더: 게이트웨이 Rate Limit 우회 (로컬 전용)
  const signup = ROLE === 'USER'
    ? await request(
        'POST',
        `${BASE}/api/v1/users/signup`,
        { email, password, nickname, slackId: '' },
        { 'X-Gateway-Secret': GATEWAY_SECRET, 'X-Load-Test': LOAD_TEST_SECRET }
      )
    : await request(
        'POST',
        `${BASE}/api/v1/users/admin/signup`,
        { email, password, nickname },
        { 'X-Admin-Secret': ADMIN_SECRET, 'X-Gateway-Secret': GATEWAY_SECRET, 'X-Load-Test': LOAD_TEST_SECRET }
      );

  if (signup.status !== 201 && signup.status !== 200) {
    console.warn(`[${index}] 회원가입 실패 (${signup.status}): ${email}`);
    return null;
  }

  // 로그인
  const login = await request(
    'POST',
    `${BASE}/api/v1/users/login`,
    { email, password },
    { 'X-Gateway-Secret': GATEWAY_SECRET, 'X-Load-Test': LOAD_TEST_SECRET }
  );

  const token = login.body?.data?.accessToken;
  if (!token) {
    console.warn(`[${index}] 로그인 실패 (${login.status}): ${email}`);
    return null;
  }

  return { email, token };
}

// 순차 처리: 배치 동시발사 대신 유저 1명(회원가입+로그인 2 request)마다
// REQUEST_INTERVAL_MS만큼 간격을 둬서 Gateway RedisRateLimiter 버스트를 건드리지 않음.
async function run() {
  console.log(`[generator] ${COUNT}명 유저 생성 시작 (요청 간격 ${REQUEST_INTERVAL_MS}ms, 순차 처리)`);
  const users = [];
  let failed  = 0;

  for (let i = 0; i < COUNT; i++) {
    const result = await createUser(i);

    if (result) users.push(result);
    else failed++;

    if ((i + 1) % 50 === 0 || i + 1 === COUNT) {
      console.log(`[generator] 진행률: ${i + 1}/${COUNT} (성공: ${users.length}, 실패: ${failed})`);
    }

    // 다음 유저 처리 전 대기 (마지막 유저는 대기 불필요)
    if (i + 1 < COUNT) {
      await new Promise((r) => setTimeout(r, REQUEST_INTERVAL_MS));
    }
  }

  fs.writeFileSync(OUTPUT_PATH, JSON.stringify(users, null, 2), 'utf-8');
  console.log(`[generator] 완료. ${users.length}명 저장 → ${OUTPUT_PATH}`);
  console.log(`[generator] 실패: ${failed}명`);
  if (failed > 0) {
    console.warn('[generator] 일부 실패. REQUEST_INTERVAL_MS를 늘려 재실행하세요 (예: 600).');
  }
}

run().catch(console.error);
