import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

// order 주문 조회 API 부하 테스트
// 흐름: (setup) 로그인 -> accessToken 확보 -> (부하) 주문 조회 반복
//
// 실행 전 준비:
//   1. 실제 존재하는 orderId가 필요 (E2E 한 번 돌려서 만든 주문 ID)
//   2. 그 유저의 email/password로 로그인 가능해야 함
//   3. 환경변수로 전달:
//      k6 run -e USER_EMAIL=xxx -e ORDER_ID=yyy k6/01-order-query.js

const BASE = __ENV.BASE_URL || 'http://localhost:8080';   // gateway
const GATEWAY_SECRET = __ENV.GATEWAY_SECRET || 'local-secret';
const USER_EMAIL = __ENV.USER_EMAIL || 'CHANGE_ME@test.com';
const USER_PW = __ENV.USER_PW || 'password123';
const ORDER_ID = __ENV.ORDER_ID || 'CHANGE_ME';

// 커스텀 메트릭 (k6 자체 측정 - order 메트릭과 별개로 클라이언트 관점)
const queryDuration = new Trend('order_query_duration');
const querySuccess = new Rate('order_query_success');

export const options = {
  // 단계적 부하: 워밍업 -> 증가 -> 유지 -> 감소
  stages: [
    { duration: '30s', target: 20 },   // 30초간 20 VU까지 증가
    { duration: '1m', target: 20 },    // 1분간 20 VU 유지
    { duration: '30s', target: 50 },   // 30초간 50 VU까지 증가
    { duration: '1m', target: 50 },    // 1분간 50 VU 유지 (피크)
    { duration: '30s', target: 0 },    // 30초간 감소
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],       // 95%가 500ms 이내
    order_query_success: ['rate>0.95'],     // 성공률 95% 이상
  },
};

// setup: 부하 시작 전 1회 실행 - 로그인해서 토큰 확보
export function setup() {
  const loginRes = http.post(
    `${BASE}/api/v1/users/login`,
    JSON.stringify({ email: USER_EMAIL, password: USER_PW }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  check(loginRes, { '로그인 성공': (r) => r.status === 200 });

  const token = loginRes.json().data.accessToken;
  if (!token) {
    throw new Error('로그인 실패 - USER_EMAIL/USER_PW 확인. 응답: ' + loginRes.body);
  }
  return { token };   // 이 값이 default 함수의 data로 전달됨
}

// 부하 본체: 각 VU가 반복 실행
export default function (data) {
  const params = {
    headers: {
      'Authorization': `Bearer ${data.token}`,
      'X-Gateway-Secret': GATEWAY_SECRET,
    },
  };

  const res = http.get(`${BASE}/api/v1/orders/${ORDER_ID}`, params);

  const ok = check(res, {
    'status is 200': (r) => r.status === 200,
    'has orderId': (r) => r.json('data.orderId') !== undefined,
  });

  queryDuration.add(res.timings.duration);
  querySuccess.add(ok);

  sleep(0.5);   // 각 요청 사이 0.5초
}
