import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// k6 부하 테스트 설정
export const options = {
    // 시나리오: 선착순 대규모 응모 (Spike Test)
    stages: [
        { duration: '10s', target: 50 },   // 10초 동안 50명으로 웜업
        { duration: '30s', target: 1000 }, // 30초 동안 1,000명의 동시 접속자(VU) 쏟아내기
        { duration: '20s', target: 1000 }, // 20초 동안 1,000명 유지 (최고 부하)
        { duration: '10s', target: 0 },    // 10초 동안 0명으로 쿨다운
    ],
    thresholds: {
        // 성공 기준(SLA) 설정
        http_req_duration: ['p(95)<500'], // 95%의 요청이 500ms 이내에 완료되어야 함
        http_req_failed: ['rate<0.01'],   // 에러율이 1% 미만이어야 함
    },
};

// 테스트 환경 설정
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8086'; // raffle-service URL
const RAFFLE_ID = '123e4567-e89b-12d3-a456-426614174000'; // TestDataLoader에 정의된 기본 래플 ID

export default function () {
    // 1. 매 요청마다 새로운 가상 유저(User ID) 생성
    const userId = uuidv4();

    // 2. HTTP 헤더 설정
    const headers = {
        'Content-Type': 'application/json',
        'X-User-Id': userId, // Controller의 @RequestHeader("X-User-Id") 처리용
        'X-User-Role': 'USER', // Gateway Auth Bypass
        'X-Gateway-Secret': 'local-secret' // Gateway Auth Bypass
    };

    // 3. 응모 요청 Body 생성
    const payload = JSON.stringify({
        billingKeyId: `bk_${userId}`,     // 테스트용 더미 빌링키
        couponId: null,                   // 쿠폰 없음
        originalAmount: 100000,           // 10만원
        discountAmount: 0,
        finalAmount: 100000
    });

    // 4. API 호출
    const res = http.post(`${BASE_URL}/api/v1/raffles/${RAFFLE_ID}/entries`, payload, { headers });

    // 5. 검증 (응답 코드가 201 Created 인지 확인)
    check(res, {
        'is status 201': (r) => r.status === 201,
        'is status 400 (if duplicate/sold out)': (r) => r.status === 400 || r.status === 409,
    });

    // 6. 유저의 행동 패턴 모사 (100ms ~ 300ms 대기)
    sleep(Math.random() * 0.2 + 0.1);
}
