#!/usr/bin/env node
/**
 * OMC 샘플 데이터 시딩 스크립트
 * 실행:  node scripts/seed-data.js
 */

const BASE           = 'http://localhost:8080'
const GATEWAY_SECRET = 'local-secret'
const ADMIN_SECRET   = 'local-admin-secret'
const ADMIN = {
  email:    'admin@omc.kr',
  password: 'Admin1234!',
  nickname: 'OMC 관리자',
}

// ─── 유틸 ────────────────────────────────────────────────────
function kst(date) {
  const d = new Date(date.getTime() + 9 * 3_600_000)
  return d.toISOString().slice(0, 19)
}
function addH(h) { return new Date(Date.now() + h * 3_600_000) }
function addD(d) { return new Date(Date.now() + d * 86_400_000) }

async function api(method, path, body, token, extraHeaders = {}) {
  const headers = { 'Content-Type': 'application/json', ...extraHeaders }
  if (token) headers['Authorization'] = `Bearer ${token}`
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  let data
  try { data = JSON.parse(text) } catch { data = { raw: text } }
  if (!res.ok && res.status !== 409) {
    const msg = data?.message ?? data?.raw ?? JSON.stringify(data)
    throw Object.assign(new Error(msg.slice(0, 300)), { status: res.status })
  }
  return { status: res.status, data }
}

const c = {
  green:  (s) => `\x1b[32m${s}\x1b[0m`,
  yellow: (s) => `\x1b[33m${s}\x1b[0m`,
  red:    (s) => `\x1b[31m${s}\x1b[0m`,
  gray:   (s) => `\x1b[90m${s}\x1b[0m`,
  bold:   (s) => `\x1b[1m${s}\x1b[0m`,
}
function section(e, t) { console.log(`\n${e}  ${c.bold(t)}`) }
function ok(msg)   { console.log(`  ${c.green('✓')} ${msg}`) }
function skip(msg) { console.log(`  ${c.yellow('~')} ${msg}`) }
function fail(msg) { console.log(`  ${c.red('✗')} ${msg}`) }

// ─── 어드민 로그인 ────────────────────────────────────────────
async function setupAdmin() {
  section('👤', '어드민 계정 설정')
  const { status } = await api('POST', '/api/v1/users/admin/signup', ADMIN, null,
    { 'X-Gateway-Secret': GATEWAY_SECRET, 'X-Admin-Secret': ADMIN_SECRET },
  ).catch(e => ({ status: e.status ?? 0 }))
  if (status === 409) skip('이미 존재하는 계정')
  else if (status === 200 || status === 201) ok(`어드민 생성: ${ADMIN.email}`)
  else skip('계정 생성 — 로그인 시도')

  const loginRes = await api('POST', '/api/v1/users/login', { email: ADMIN.email, password: ADMIN.password })
  const token = loginRes.data?.data?.accessToken ?? loginRes.data?.accessToken
  if (!token) throw new Error('로그인 실패: ' + JSON.stringify(loginRes.data).slice(0, 200))
  ok('로그인 성공')
  return token
}

// ─── 전체 삭제 ───────────────────────────────────────────────
async function deleteAll(token, label, listPath, idField, deletePath) {
  try {
    const r = await api('GET', `${listPath}?page=0&size=200`, null, token)
    // 다양한 응답 구조에 대응
    const d = r.data
    const content =
      d?.data?.data?.content ?? d?.data?.content ?? d?.content ??
      d?.data?.data ?? d?.data ?? []
    const items = Array.isArray(content) ? content : []
    if (items.length === 0) { skip(`${label} — 없음`); return }
    let cnt = 0
    for (const item of items) {
      const id = item[idField]
      if (!id) continue
      await api('DELETE', `${deletePath}/${id}`, null, token).catch(() => {})
      cnt++
    }
    ok(`${label} ${cnt}개 삭제`)
  } catch (e) {
    fail(`${label} 삭제 오류: ${e.message.slice(0, 80)}`)
  }
}

async function resetData(token) {
  section('🗑️ ', '기존 데이터 전체 초기화')
  await deleteAll(token, '래플',  '/api/v1/raffles',  'raffleId', '/api/v1/admin/raffles')
  await deleteAll(token, '드롭',  '/api/v1/drops',    'dropId',   '/api/v1/admin/drops')
  await deleteAll(token, '상품',  '/api/v1/products', 'productId','/api/v1/admin/products')
  await deleteAll(token, '쿠폰',  '/api/v1/coupons',  'couponId', '/api/v1/admin/coupons')
}

// ─── 상품 4개 ────────────────────────────────────────────────
const PRODUCTS = [
  {
    name: 'Nike Air Jordan 4 Retro "Military Blue"',
    description: '1989년 첫 출시 이후 수십 년간 스니커 씬의 아이콘으로 자리잡은 Air Jordan 4. 메시 패널과 플라스틱 윙 아일렛, 공기창 미드솔이 결합된 클래식 농구화.',
    price: 289_000, brand: 'Nike', category: 'Sneakers',
    imageUrl: 'https://images.unsplash.com/photo-1600269452121-4f2416e55c28?w=800&q=80',
    initialQuantity: 100,
  },
  {
    name: 'Adidas Yeezy Boost 350 V2 "Onyx"',
    description: 'Kanye West × Adidas의 콜라보 클래식. 올블랙 프라임니트 어퍼와 풀 BOOST 미드솔로 하루종일 착용해도 편안함.',
    price: 369_000, brand: 'Adidas', category: 'Sneakers',
    imageUrl: 'https://images.unsplash.com/photo-1556906781-9a412961a28c?w=800&q=80',
    initialQuantity: 80,
  },
  {
    name: 'Supreme Box Logo Hoodie FW24 "Black"',
    description: '2024 F/W 시즌 Supreme 박스 로고 후디. 340g 헤비 프리미엄 플리스, 스크린 프린팅 로고. 드롭 한정 — 재입고 없음.',
    price: 580_000, brand: 'Supreme', category: 'Apparel',
    imageUrl: 'https://images.unsplash.com/photo-1556821840-3a63f15732ce?w=800&q=80',
    initialQuantity: 50,
  },
  {
    name: 'Stone Island Compass Patch Fleece Jacket',
    description: '스톤 아일랜드 시그니처 컴파스 패치. 고급 기어드 플리스에 독점 가먼트 다잉. 미니멀한 디자인 속 극한의 기술력.',
    price: 890_000, brand: 'Stone Island', category: 'Apparel',
    imageUrl: 'https://images.unsplash.com/photo-1551028719-00167b16eac5?w=800&q=80',
    initialQuantity: 30,
  },
]

async function seedProducts(token) {
  section('📦', '상품 4개 생성')
  const created = []
  for (const p of PRODUCTS) {
    try {
      const r = await api('POST', '/api/v1/admin/products', p, token)
      const productId = r.data?.data?.productId ?? r.data?.productId
      created.push({ ...p, productId })
      ok(`${p.name}  ${c.gray('₩' + p.price.toLocaleString())}`)
    } catch (e) {
      fail(`${p.name}: ${e.message.slice(0, 100)}`)
      created.push({ ...p, productId: null })
    }
  }
  return created
}

// ─── 드롭 4개 ────────────────────────────────────────────────
// @Future 제약: startAt은 미래여야 함. 5분 후 시작으로 설정하면 5분 뒤 자동 LIVE 전환.
async function seedDrops(token, products) {
  section('🔥', '드롭 4개 생성')
  const configs = [
    { product: products[0], startAt: kst(addH(5/60)),  endAt: kst(addH(8)),   totalQty: 20, holdTtlSec: 300, tag: '5분 후 LIVE' },
    { product: products[1], startAt: kst(addH(10/60)), endAt: kst(addH(6)),   totalQty: 15, holdTtlSec: 300, tag: '10분 후 LIVE' },
    { product: products[2], startAt: kst(addH(2)),     endAt: kst(addH(10)),  totalQty: 10, holdTtlSec: 180, tag: 'UPCOMING' },
    { product: products[3], startAt: kst(addD(1)),     endAt: kst(addD(1.5)), totalQty: 5,  holdTtlSec: 120, tag: 'UPCOMING' },
  ]
  for (const d of configs) {
    if (!d.product?.productId) { fail(`productId 없음`); continue }
    try {
      await api('POST', '/api/v1/admin/drops', {
        productId: d.product.productId,
        startAt: d.startAt, endAt: d.endAt,
        totalQty: d.totalQty, holdTtlSec: d.holdTtlSec,
      }, token)
      ok(`[${d.tag}] ${d.product.name}  ${c.gray('수량 ' + d.totalQty)}`)
    } catch (e) {
      fail(`${d.product.name}: ${e.message.slice(0, 100)}`)
    }
  }
}

// ─── 래플 4개 ────────────────────────────────────────────────
// @Future 제약: startedAt은 미래여야 함.
// open: true → 생성 직후 강제로 OPEN 상태 변경 → 응모 가능 상태로 표시.
async function seedRaffles(token, products) {
  section('🎰', '래플 4개 생성')
  const configs = [
    { product: products[0], name: 'Nike Air Jordan 4 "Military Blue" 한정 래플', winnerCount: 10, startedAt: kst(addH(5/60)),  endedAt: kst(addD(5)),  open: true,  tag: 'OPEN' },
    { product: products[1], name: 'Adidas Yeezy Boost 350 V2 래플',              winnerCount: 5,  startedAt: kst(addH(10/60)), endedAt: kst(addD(3)),  open: true,  tag: 'OPEN' },
    { product: products[2], name: 'Supreme Box Logo Hoodie FW24 래플',           winnerCount: 3,  startedAt: kst(addH(2)),     endedAt: kst(addD(7)),  open: false, tag: 'UPCOMING' },
    { product: products[3], name: 'Stone Island Fleece Jacket 극한정 래플',      winnerCount: 2,  startedAt: kst(addD(2)),     endedAt: kst(addD(10)), open: false, tag: 'UPCOMING' },
  ]
  for (const rf of configs) {
    if (!rf.product?.productId) { fail(`productId 없음`); continue }
    try {
      const r = await api('POST', '/api/v1/admin/raffles', {
        productId: rf.product.productId, name: rf.name,
        winnerCount: rf.winnerCount, startedAt: rf.startedAt, endedAt: rf.endedAt,
      }, token)
      const raffleId = r.data?.data?.raffleId ?? r.data?.raffleId
      if (rf.open && raffleId) {
        await api('POST', `/api/v1/admin/raffles/${raffleId}/status`, { status: 'OPEN' }, token)
          .catch(e => fail(`  OPEN 상태 변경 실패: ${e.message.slice(0, 60)}`))
      }
      ok(`[${rf.tag}] ${rf.name}  ${c.gray('당첨 ' + rf.winnerCount + '명')}`)
    } catch (e) {
      fail(`${rf.name}: ${e.message.slice(0, 100)}`)
    }
  }
}

// ─── 쿠폰 3개 ────────────────────────────────────────────────
async function seedCoupons(token) {
  section('🎁', '쿠폰 3개 생성')
  const configs = [
    {
      name: '신규 가입 웰컴 1,000원 할인',
      discountType: 'AMOUNT', discountValue: 1_000,
      totalQuantity: 500,
      startedAt: kst(addD(-1)), expiredAt: kst(addD(7)),
      imageUrl: 'https://images.unsplash.com/photo-1607082348824-0a96f2a4b9da?w=600&q=80',
    },
    {
      name: '위켄드 스페셜 5,000원 할인',
      discountType: 'AMOUNT', discountValue: 5_000,
      totalQuantity: 200,
      startedAt: kst(addD(-1)), expiredAt: kst(addD(5)),
      imageUrl: 'https://images.unsplash.com/photo-1472851294608-062f824d29cc?w=600&q=80',
    },
    {
      name: '프리미엄 멤버 10,000원 할인',
      discountType: 'AMOUNT', discountValue: 10_000,
      totalQuantity: 50,
      startedAt: kst(addD(-1)), expiredAt: kst(addD(3)),
      imageUrl: 'https://images.unsplash.com/photo-1549465220-1a8b9238cd48?w=600&q=80',
    },
  ]
  for (const cp of configs) {
    try {
      await api('POST', '/api/v1/coupons', cp, token)
      ok(`${cp.name}  ${c.gray('수량 ' + cp.totalQuantity)}`)
    } catch (e) {
      fail(`${cp.name}: ${e.message.slice(0, 100)}`)
    }
  }
}

// ─── 메인 ────────────────────────────────────────────────────
async function main() {
  console.log(c.bold('\n🌱  OMC 샘플 데이터 시딩'))
  console.log(c.gray('─'.repeat(52)))
  try {
    const token = await setupAdmin()
    await resetData(token)
    const products = await seedProducts(token)
    await seedDrops(token, products)
    await seedRaffles(token, products)
    await seedCoupons(token)
    console.log('\n' + c.gray('─'.repeat(52)))
    console.log(c.green(c.bold('✅  시딩 완료! — 상품/드롭/래플/쿠폰 각 4개')))
    console.log(c.gray('   프론트: http://localhost:5173'))
    console.log(c.gray('   어드민: http://localhost:5173/admin\n'))
  } catch (e) {
    console.error('\n' + c.red('❌  시딩 실패: ' + e.message))
    console.error(c.gray('   백엔드가 실행 중인지 확인: ./docker-up.sh\n'))
    process.exit(1)
  }
}

main()
