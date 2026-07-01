// 부하 테스트용 유저 삭제기 (Node.js 스크립트) — v2
//
// User Service에 별도 Admin 삭제 API가 없어서, 로컬 개발 환경 전용으로
// 1) Keycloak Admin REST API로 Keycloak 유저 직접 삭제
// 2) docker exec psql로 user_db.p_users 직접 삭제
// 두 단계를 수행한다. 로컬 docker compose 환경에서만 동작 (docker CLI 필요, 원격/운영 불가).
//
// 대상 범위: generator.js가 실제로 생성한 유저만 정리한다.
//   - users.json이 있으면 그 목록 사용 (가장 정확)
//   - 없으면 DB에서 generator.js의 생성 패턴(loadtest_<timestamp>_<index>@test.com)에만
//     정확히 매칭해서 조회 — loadtest_admin처럼 수동으로 만든 계정은 대상이 아님
//
// ⚠️ 이 스크립트는 항상 사람이 검증을 마친 뒤 "의도적으로" 실행해야 합니다.
//   k6 실행 뒤에 자동으로 이어붙이는 파이프라인/스크립트를 만들지 마세요.
//   부하테스트 실패 원인을 DB/Redis로 추적하려면 데이터가 남아있어야 하는데,
//   생성→테스트→정리를 하나로 묶으면 실패 시 처음부터 재현해야 하는 상황이 생깁니다.
//   (02-product-inventory.js 상단 "전체 실행 순서" 주석 참고)
//
// 실행 방법:
//   node k6/cleanup.js
//
// 환경변수 (선택):
//   KEYCLOAK_URL=http://localhost:8180
//   KEYCLOAK_REALM=omc
//   KEYCLOAK_ADMIN_USER=admin
//   KEYCLOAK_ADMIN_PASSWORD=admin
//   POSTGRES_CONTAINER=omc-postgres
//   POSTGRES_USER=omc
//   POSTGRES_DB=omc

const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const KEYCLOAK_URL = process.env.KEYCLOAK_URL || 'http://localhost:8180';
const KEYCLOAK_REALM = process.env.KEYCLOAK_REALM || 'omc';
const KEYCLOAK_ADMIN_USER = process.env.KEYCLOAK_ADMIN_USER || 'admin';
const KEYCLOAK_ADMIN_PASSWORD = process.env.KEYCLOAK_ADMIN_PASSWORD || 'admin';
const POSTGRES_CONTAINER = process.env.POSTGRES_CONTAINER || 'omc-postgres';
const POSTGRES_USER = process.env.POSTGRES_USER || 'omc';
const POSTGRES_DB = process.env.POSTGRES_DB || 'omc';
const USERS_FILE = path.join(__dirname, 'users.json');

function request(method, url, body, headers = {}) {
  return new Promise((resolve, reject) => {
    const parsed = new URL(url);
    const lib = parsed.protocol === 'https:' ? https : http;
    const data = body ? (typeof body === 'string' ? body : JSON.stringify(body)) : null;
    const options = {
      hostname: parsed.hostname,
      port: parsed.port,
      path: parsed.pathname + parsed.search,
      method,
      headers: { ...headers, ...(data ? { 'Content-Length': Buffer.byteLength(data) } : {}) },
    };
    const req = lib.request(options, (res) => {
      let raw = '';
      res.on('data', (c) => (raw += c));
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, body: raw ? JSON.parse(raw) : null });
        } catch {
          resolve({ status: res.statusCode, body: raw });
        }
      });
    });
    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}

// Keycloak master realm의 부트스트랩 admin(admin-cli, password grant)으로 토큰 발급
async function getKeycloakAdminToken() {
  const body = `grant_type=password&client_id=admin-cli&username=${encodeURIComponent(
      KEYCLOAK_ADMIN_USER)}&password=${encodeURIComponent(KEYCLOAK_ADMIN_PASSWORD)}`;
  const res = await request(
      'POST',
      `${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token`,
      body,
      { 'Content-Type': 'application/x-www-form-urlencoded' },
  );
  if (!res.body || !res.body.access_token) {
    throw new Error(`Keycloak admin 토큰 발급 실패: ${JSON.stringify(res.body)}`);
  }
  return res.body.access_token;
}

async function deleteKeycloakUserByEmail(token, email) {
  const search = await request(
      'GET',
      `${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/users?email=${encodeURIComponent(email)}&exact=true`,
      null,
      { Authorization: `Bearer ${token}` },
  );

  if (!Array.isArray(search.body) || search.body.length === 0) {
    return { found: false, deleted: false };
  }

  let deleted = 0;
  for (const user of search.body) {
    const del = await request(
        'DELETE',
        `${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}/users/${user.id}`,
        null,
        { Authorization: `Bearer ${token}` },
    );
    if (del.status === 204) deleted++;
  }
  return { found: true, deleted: deleted > 0 };
}

// user_db.p_users에서 이메일 목록으로 직접 삭제 (docker exec psql, 앱 API 안 거침)
function deleteFromDb(emails) {
  if (emails.length === 0) return 0;
  const values = emails.map((e) => `'${e.replace(/'/g, "''")}'`).join(',');
  const sql = `DELETE FROM user_db.p_users WHERE email IN (${values});`;
  execSync(
      `docker exec ${POSTGRES_CONTAINER} psql -U ${POSTGRES_USER} -d ${POSTGRES_DB} -c "${sql}"`,
      { stdio: 'pipe' },
  );
  return emails.length;
}

// users.json이 없을 때(이전 회차의 부분 성공 잔재 등) generator.js가 실제로 생성하는
// 이메일 형식(loadtest_<timestamp>_<index>@test.com)에만 정확히 매칭해 DB에서 직접 조회.
// LIKE 대신 정규식(~)을 써서 loadtest_admin처럼 수동 생성한 계정은 제외한다.
// -t -A: 헤더/정렬 없이 값만 한 줄씩 출력 → 파싱하기 쉬움
function findEmailsByPattern(regexPattern) {
  const sql = `SELECT email FROM user_db.p_users WHERE email ~ '${regexPattern}';`;
  const out = execSync(
      `docker exec ${POSTGRES_CONTAINER} psql -U ${POSTGRES_USER} -d ${POSTGRES_DB} -t -A -c "${sql}"`,
      { encoding: 'utf-8' },
  );
  return out.split('\n').map((s) => s.trim()).filter(Boolean);
}

async function run() {
  let emails = [];
  if (fs.existsSync(USERS_FILE)) {
    const users = JSON.parse(fs.readFileSync(USERS_FILE, 'utf-8'));
    emails = users.map((u) => u.email);
    console.log(`[cleanup] users.json에서 ${emails.length}명 로드`);
  } else {
    console.log('[cleanup] users.json 없음 — DB에서 generator.js 생성 패턴으로 직접 조회');
    // generator.js: `loadtest_${Date.now()}_${index}@test.com` — 숫자_숫자 형태만 매칭
    emails = findEmailsByPattern('^loadtest_[0-9]+_[0-9]+@test\\.com$');
    console.log(`[cleanup] 패턴 매칭 ${emails.length}명 발견`);
    if (emails.length === 0) {
      console.log('[cleanup] 정리할 대상 없음 — 종료');
      return;
    }
  }

  console.log('[cleanup] Keycloak admin 토큰 발급 중...');
  const token = await getKeycloakAdminToken();

  let kcDeleted = 0;
  let kcNotFound = 0;
  for (let i = 0; i < emails.length; i++) {
    const result = await deleteKeycloakUserByEmail(token, emails[i]);
    if (result.deleted) kcDeleted++;
    else if (!result.found) kcNotFound++;

    if ((i + 1) % 50 === 0 || i + 1 === emails.length) {
      console.log(
          `[cleanup] Keycloak 삭제 진행률: ${i + 1}/${emails.length} (삭제: ${kcDeleted}, 이미없음: ${kcNotFound})`,
      );
    }
  }

  console.log('[cleanup] DB(user_db.p_users) 일괄 삭제 중...');
  const dbDeletedCount = deleteFromDb(emails);

  console.log(`[cleanup] 완료. Keycloak 삭제: ${kcDeleted}명 (이미없음: ${kcNotFound}), DB 삭제 시도: ${dbDeletedCount}명`);

  if (fs.existsSync(USERS_FILE)) {
    fs.unlinkSync(USERS_FILE);
    console.log('[cleanup] users.json 삭제 완료');
  }
}

run().catch((err) => {
  console.error('[cleanup] 실패:', err.message);
  process.exit(1);
});
