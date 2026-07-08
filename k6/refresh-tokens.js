// users.json의 토큰을 재발급하는 스크립트
// 실행: node k6/refresh-tokens.js

const http  = require('http');
const fs    = require('fs');
const path  = require('path');

const BASE           = process.env.BASE_URL        || 'http://localhost:8080';
const GATEWAY_SECRET = process.env.GATEWAY_SECRET  || 'local-secret';
const PASSWORD       = 'Test1234!';
const INTERVAL_MS    = parseInt(process.env.REQUEST_INTERVAL_MS || '200', 10);
const OUTPUT_PATH    = path.join(__dirname, 'users.json');

function request(method, url, body, headers = {}) {
  return new Promise((resolve, reject) => {
    const parsed = new URL(url);
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
    const req = http.request(options, (res) => {
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

async function run() {
  const users = JSON.parse(fs.readFileSync(OUTPUT_PATH, 'utf-8'));
  console.log(`[refresh] ${users.length}명 토큰 재발급 시작`);

  let success = 0, failed = 0;

  for (let i = 0; i < users.length; i++) {
    const { email } = users[i];
    const res = await request('POST', `${BASE}/api/v1/users/login`,
      { email, password: PASSWORD },
      { 'X-Gateway-Secret': GATEWAY_SECRET }
    );

    const token = res.body?.data?.accessToken;
    if (token) {
      users[i].token = token;
      success++;
    } else {
      console.warn(`[${i}] 로그인 실패 (${res.status}): ${email}`);
      failed++;
    }

    if ((i + 1) % 100 === 0 || i + 1 === users.length) {
      console.log(`[refresh] 진행률: ${i + 1}/${users.length} (성공: ${success}, 실패: ${failed})`);
    }

    if (i + 1 < users.length) {
      await new Promise(r => setTimeout(r, INTERVAL_MS));
    }
  }

  fs.writeFileSync(OUTPUT_PATH, JSON.stringify(users, null, 2), 'utf-8');
  console.log(`[refresh] 완료. 성공: ${success}, 실패: ${failed}`);
}

run().catch(console.error);
