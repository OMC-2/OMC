#!/usr/bin/env node
/**
 * OMC 샘플 데이터 시딩 스크립트 v2
 * 실행:  node scripts/seed-data.js
 *
 * 생성 목록:
 *   상품 8개 (스니커즈 4 / 어패럴 2 / 가방 1 / 시계 1)
 *   드롭 4개 (2개 즉시 LIVE, 2개 UPCOMING)
 *   래플 4개 (2개 강제 OPEN, 2개 UPCOMING)
 *   쿠폰 4개 (정액 3 / 정률 1)
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
function addS(s) { return new Date(Date.now() + s * 1000) }
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
  cyan:   (s) => `\x1b[36m${s}\x1b[0m`,
}
function section(e, t) { console.log(`\n${e}  ${c.bold(t)}`) }
function ok(msg)   { console.log(`  ${c.green('✓')} ${msg}`) }
function skip(msg) { console.log(`  ${c.yellow('~')} ${msg}`) }
function fail(msg) { console.log(`  ${c.red('✗')} ${msg}`) }
function info(msg) { console.log(`  ${c.cyan('ℹ')} ${msg}`) }

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
  await deleteAll(token, '쿠폰',  '/api/v1/coupons',  'couponId', '/api/v1/coupons')
  await deleteAll(token, '상품',  '/api/v1/products', 'productId','/api/v1/admin/products')
}

// ─── 상품 8개 ────────────────────────────────────────────────
// 이미지: Unsplash 무료 상업 라이선스 — 제품 유사 이미지
const PRODUCTS = [
  // ── 드롭용 상품 4개 ──
  {
    name: 'Nike Air Jordan 4 Retro "Military Blue"',
    description: '1989년 첫 출시 이후 스니커 씬의 아이콘으로 자리잡은 Air Jordan 4. 메시 패널과 플라스틱 윙 아일렛, MILITARY BLUE 컬러웨이의 클래식 농구화. 한정 수량 드롭.',
    price: 289_000, brand: 'Nike', category: 'Sneakers',
    imageUrl: 'https://images.unsplash.com/photo-1600269452121-4f2416e55c28?w=800&q=85',
    initialQuantity: 100,
  },
  {
    name: 'Adidas Yeezy Boost 350 V2 "Onyx"',
    description: 'Kanye West × Adidas의 콜라보 클래식. 올블랙 프라임니트 어퍼와 풀-BOOST 미드솔 탑재. 어떤 룩과도 매칭되는 스텔스 컬러웨이. 재입고 불가.',
    price: 369_000, brand: 'Adidas', category: 'Sneakers',
    imageUrl: 'https://images.unsplash.com/photo-1595950653106-6c9ebd614d3a?w=800&q=85',
    initialQuantity: 80,
  },
  {
    name: 'Supreme Box Logo Hoodie FW24 "Ash Grey"',
    description: '2024 F/W 시즌 Supreme 박스 로고 후디. 340g 헤비 프리미엄 플리스, 스크린 프린팅 아치 로고. 한 시즌 한 번 드롭 — 재고 소진 시 재입고 없음.',
    price: 520_000, brand: 'Supreme', category: 'Apparel',
    imageUrl: 'https://images.unsplash.com/photo-1618354691373-d851c5c3a990?w=800&q=85',
    initialQuantity: 50,
  },
  {
    name: 'Stone Island Ghost Piece Crewneck "Natural"',
    description: '스톤 아일랜드의 시그니처 Ghost Piece 기법. 아우터 원단 하에 감춰진 컴파스 패치로 진정한 마니아만 아는 디테일. 가먼트 다잉으로 완성된 독창적 컬러.',
    price: 490_000, brand: 'Stone Island', category: 'Apparel',
    imageUrl: 'https://images.unsplash.com/photo-1509347528160-9a9e33742cdb?w=800&q=85',
    initialQuantity: 30,
  },
  // ── 래플용 상품 4개 ──
  {
    name: 'New Balance 990v6 Made in USA "Grey"',
    description: '미국 메사추세츠 공장에서 핸드크래프트된 990 시리즈의 6번째 진화. 피그스킨 + 서지나일 메시 어퍼, ENCAP + C-CAP 미드솔 조합. 아메리칸 매뉴팩처링의 정수.',
    price: 239_000, brand: 'New Balance', category: 'Sneakers',
    imageUrl: 'https://images.unsplash.com/photo-1539185441755-769473a23570?w=800&q=85',
    initialQuantity: 100,
  },
  {
    name: 'Carhartt WIP Detroit Jacket "Blue Stone Washed"',
    description: '1904년 디트로이트에서 시작된 워크웨어 헤리티지. 12oz 덕 캔버스에 WIP만의 스톤워시 가공을 더한 아이코닉 재킷. 빈티지 인디고 컬러웨이, 극한의 내구성.',
    price: 269_000, brand: 'Carhartt WIP', category: 'Apparel',
    imageUrl: 'https://images.unsplash.com/photo-1503341504253-dff4815485f1?w=800&q=85',
    initialQuantity: 80,
  },
  {
    name: 'Porter-Yoshida & Co. Tanker Shoulder Bag "Black"',
    description: '1935년 설립 이후 일본 가방 장인 정신의 정수. 항공기 파일럿 재킷에서 영감받은 탱커 시리즈. 내구성 나일론 + 핸드 스티칭. 매일의 EDC에 완벽한 동반자.',
    price: 289_000, brand: 'Porter', category: 'Bag',
    imageUrl: 'https://images.unsplash.com/photo-1548036328-c9fa89d128fa?w=800&q=85',
    initialQuantity: 50,
  },
  {
    name: 'G-SHOCK DW-5600BB "All Black Stealth"',
    description: '1983년 초대 G-SHOCK의 DNA를 계승한 풀 블랙 스텔스 에디션. 200m 방수, 20년 배터리, 충격/진동/저온 내성. 가장 클린한 G-SHOCK 컬러웨이.',
    price: 129_000, brand: 'Casio G-SHOCK', category: 'Accessories',
    imageUrl: 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=800&q=85',
    initialQuantity: 100,
  },
]

async function seedProducts(token) {
  section('📦', '상품 8개 생성')
  const created = []
  for (const p of PRODUCTS) {
    try {
      const r = await api('POST', '/api/v1/admin/products', p, token)
      const productId = r.data?.data?.productId ?? r.data?.productId
      created.push({ ...p, productId })
      ok(`${p.name.slice(0, 48)}  ${c.gray('₩' + p.price.toLocaleString())}`)
    } catch (e) {
      fail(`${p.name.slice(0, 40)}: ${e.message.slice(0, 80)}`)
      created.push({ ...p, productId: null })
    }
  }
  return created
}

// ─── 드롭 4개 ────────────────────────────────────────────────
// products[0,1]: 시작 5초 후 → 시딩 직후 LIVE 전환
// products[2,3]: 2/5시간 후 → UPCOMING (데모 중 예약 상품 시연용)
async function seedDrops(token, products) {
  section('🔥', '드롭 4개 생성')
  const configs = [
    {
      product: products[0],
      startAt: kst(addS(5)),    // ← 5초 후 → 즉시 LIVE
      endAt:   kst(addH(24)),
      totalQty: 20, holdTtlSec: 600,
      tag: '즉시 LIVE',
    },
    {
      product: products[1],
      startAt: kst(addS(8)),    // ← 8초 후 → 즉시 LIVE
      endAt:   kst(addH(24)),
      totalQty: 15, holdTtlSec: 600,
      tag: '즉시 LIVE',
    },
    {
      product: products[2],
      startAt: kst(addH(2)),
      endAt:   kst(addH(48)),
      totalQty: 10, holdTtlSec: 300,
      tag: 'UPCOMING (+2h)',
    },
    {
      product: products[3],
      startAt: kst(addH(5)),
      endAt:   kst(addH(72)),
      totalQty: 5,  holdTtlSec: 180,
      tag: 'UPCOMING (+5h)',
    },
  ]
  for (const d of configs) {
    if (!d.product?.productId) { fail(`productId 없음`); continue }
    try {
      await api('POST', '/api/v1/admin/drops', {
        productId:  d.product.productId,
        startAt:    d.startAt,
        endAt:      d.endAt,
        totalQty:   d.totalQty,
        holdTtlSec: d.holdTtlSec,
      }, token)
      ok(`[${d.tag}] ${d.product.name.slice(0, 44)}  ${c.gray('수량 ' + d.totalQty)}`)
    } catch (e) {
      fail(`${d.product.name.slice(0, 36)}: ${e.message.slice(0, 100)}`)
    }
  }
  info('드롭 1·2는 시딩 직후 5-10초 내 LIVE 전환 (프론트 새로고침)')
}

// ─── 래플 4개 ────────────────────────────────────────────────
// products[4,5]: 강제 OPEN → 즉시 응모 가능
// products[6,7]: UPCOMING
async function seedRaffles(token, products) {
  section('🎰', '래플 4개 생성')
  const configs = [
    {
      product: products[4],
      name: 'New Balance 990v6 Made in USA 한정 래플',
      winnerCount: 10, endedAt: kst(addD(7)),
      open: true, tag: 'OPEN',
    },
    {
      product: products[5],
      name: 'Carhartt WIP Detroit Jacket 래플',
      winnerCount: 5, endedAt: kst(addD(5)),
      open: true, tag: 'OPEN',
    },
    {
      product: products[6],
      name: 'Porter Tanker Shoulder Bag 한정 래플',
      winnerCount: 3, endedAt: kst(addD(14)),
      open: false, tag: 'UPCOMING',
    },
    {
      product: products[7],
      name: 'G-SHOCK DW-5600BB Stealth 에디션 래플',
      winnerCount: 2, endedAt: kst(addD(10)),
      open: false, tag: 'UPCOMING',
    },
  ]
  for (const rf of configs) {
    if (!rf.product?.productId) { fail(`productId 없음`); continue }
    try {
      const startedAt = rf.open ? kst(addS(2)) : kst(addH(3))
      const r = await api('POST', '/api/v1/admin/raffles', {
        productId:   rf.product.productId,
        name:        rf.name,
        winnerCount: rf.winnerCount,
        startedAt,
        endedAt: rf.endedAt,
      }, token)
      const raffleId = r.data?.data?.raffleId ?? r.data?.raffleId
      if (rf.open && raffleId) {
        await api('POST', `/api/v1/admin/raffles/${raffleId}/status`, { status: 'OPEN' }, token)
          .catch(e => fail(`  OPEN 상태 변경 실패: ${e.message.slice(0, 60)}`))
      }
      ok(`[${rf.tag}] ${rf.name}  ${c.gray('당첨 ' + rf.winnerCount + '명')}`)
    } catch (e) {
      fail(`${rf.name.slice(0, 40)}: ${e.message.slice(0, 100)}`)
    }
  }
}

// ─── 쿠폰 4개 ────────────────────────────────────────────────
async function seedCoupons(token) {
  section('🎁', '쿠폰 4개 생성')
  const configs = [
    {
      name: '신규 가입 기념 5,000원',
      discountType: 'AMOUNT', discountValue: 5_000,
      totalQuantity: 50,
      startedAt: kst(addD(-1)), expiredAt: kst(addD(14)),
      imageUrl: 'https://images.unsplash.com/photo-1607082348824-0a96f2a4b9da?w=600&q=80',
    },
    {
      name: '드롭 구매 10,000원 할인',
      discountType: 'AMOUNT', discountValue: 10_000,
      totalQuantity: 30,
      startedAt: kst(addD(-1)), expiredAt: kst(addD(7)),
      imageUrl: 'https://images.unsplash.com/photo-1472851294608-062f824d29cc?w=600&q=80',
    },
    {
      name: '래플 10% 할인 (최대 30,000원)',
      discountType: 'RATE', discountValue: 0.1,
      maxDiscountAmount: 30_000,
      totalQuantity: 20,
      startedAt: kst(addD(-1)), expiredAt: kst(addD(10)),
      imageUrl: 'https://images.unsplash.com/photo-1549465220-1a8b9238cd48?w=600&q=80',
    },
    {
      name: '프리미엄 멤버 20,000원',
      discountType: 'AMOUNT', discountValue: 20_000,
      totalQuantity: 10,
      startedAt: kst(addD(-1)), expiredAt: kst(addD(5)),
      imageUrl: 'https://images.unsplash.com/photo-1483985988355-763728e1935b?w=600&q=80',
    },
  ]
  for (const cp of configs) {
    try {
      await api('POST', '/api/v1/coupons', cp, token)
      const discText = cp.discountType === 'AMOUNT'
        ? `₩${cp.discountValue.toLocaleString()}`
        : `${cp.discountValue * 100}%`
      ok(`${cp.name}  ${c.gray(discText + ' | 수량 ' + cp.totalQuantity)}`)
    } catch (e) {
      fail(`${cp.name}: ${e.message.slice(0, 100)}`)
    }
  }
}

// ─── 메인 ────────────────────────────────────────────────────
async function main() {
  console.log(c.bold('\n🌱  OMC 샘플 데이터 시딩 v2'))
  console.log(c.gray('─'.repeat(56)))
  try {
    const token = await setupAdmin()
    await resetData(token)
    const products = await seedProducts(token)
    await seedDrops(token, products)
    await seedRaffles(token, products)
    await seedCoupons(token)

    console.log('\n' + c.gray('─'.repeat(56)))
    console.log(c.green(c.bold('✅  시딩 완료!')))
    console.log('')
    console.log(c.bold('  📋  데이터 요약'))
    console.log(`  ${c.cyan('상품')}  8개 (스니커즈4 / 어패럴2 / 가방1 / 시계1)`)
    console.log(`  ${c.cyan('드롭')}  4개 — 1·2번: 약 10초 후 LIVE | 3·4번: UPCOMING`)
    console.log(`  ${c.cyan('래플')}  4개 — 1·2번: OPEN (즉시 응모 가능) | 3·4번: UPCOMING`)
    console.log(`  ${c.cyan('쿠폰')}  4개 — 정액 3개 + 정률 1개 (10%)`)
    console.log('')
    console.log(c.bold('  🔗  접속 링크'))
    console.log(`  ${c.gray('프론트:')} http://localhost:5173`)
    console.log(`  ${c.gray('어드민:')} http://localhost:5173/admin`)
    console.log(`  ${c.gray('Swagger:')} http://localhost:8080/swagger-ui.html`)
    console.log('')
    console.log(`  ${c.yellow('⚠')}  드롭 새로고침 후 LIVE 표시 확인`)
    console.log(`  ${c.yellow('⚠')}  테스트 결제: 마이페이지에서 카드 등록 먼저!\n`)
  } catch (e) {
    console.error('\n' + c.red('❌  시딩 실패: ' + e.message))
    console.error(c.gray('   백엔드가 실행 중인지 확인: ./docker-up.sh\n'))
    process.exit(1)
  }
}

main()
