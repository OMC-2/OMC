// Unsplash 라이선스 — 무료 상업적 사용 가능
// 아래 이미지는 seed-data.js의 상품 이미지와 겹치지 않도록 선별됨

// ── seed-data.js product images (참조용 — 플레이스홀더에서 제외) ──
// photo-1600269452121-4f2416e55c28  Jordan 4
// photo-1595950653106-6c9ebd614d3a  Yeezy 350
// photo-1618354691373-d851c5c3a990  Supreme Hoodie
// photo-1509347528160-9a9e33742cdb  Stone Island
// photo-1539185441755-769473a23570  New Balance
// photo-1503341504253-dff4815485f1  Carhartt
// photo-1548036328-c9fa89d128fa    Porter Bag  ← 시드에서 사용
// photo-1523275335684-37898b6baf30  G-Shock

export const PLACEHOLDER_IMAGES = [
  // 스니커즈 — 시드와 겹치지 않는 이미지
  'https://images.unsplash.com/photo-1542838132-92c53300491e?w=600&q=80', // 다크 스니커즈 탑뷰
  'https://images.unsplash.com/photo-1460353581641-37baddab0fa2?w=600&q=80', // 화이트 스니커즈
  'https://images.unsplash.com/photo-1583743814966-8936f5b7be1a?w=600&q=80', // 클린 스니커즈
  'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=600&q=80', // 레드 스니커즈
  // 의류
  'https://images.unsplash.com/photo-1523381210434-271e8be1f52b?w=600&q=80', // 후드
  'https://images.unsplash.com/photo-1556905055-8f358a7a47b2?w=600&q=80', // 자켓
  'https://images.unsplash.com/photo-1521369909029-2afed882baee?w=600&q=80', // 캡
  // 가방 — photo-1548036328... 제거하고 다른 이미지로 교체
  'https://images.unsplash.com/photo-1590874103328-eac38a683ce7?w=600&q=80', // 블랙 백팩
  'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=600&q=80', // 배낭
  // 시계/액세서리 — photo-1523275335684... 제거하고 다른 이미지
  'https://images.unsplash.com/photo-1549972574-8e3e1ed6a347?w=600&q=80', // 다크 시계
  'https://images.unsplash.com/photo-1434389677669-e08b4cac3105?w=600&q=80', // 액세서리
  'https://images.unsplash.com/photo-1588850561407-ed78c282e89b?w=600&q=80', // 볼캡
]

export function getPlaceholderImage(index: number): string {
  return PLACEHOLDER_IMAGES[Math.abs(index) % PLACEHOLDER_IMAGES.length]
}

// 히어로/배경 이미지 (추상/건축 — 브랜드 없음)
export const HERO_IMAGES = {
  main:   'https://images.unsplash.com/photo-1579547944212-c4f4961a8dd8?w=1600&q=90',
  login:  'https://images.unsplash.com/photo-1542838132-92c53300491e?w=800&q=85',
  signup: 'https://images.unsplash.com/photo-1523381210434-271e8be1f52b?w=800&q=85',
  drops:  'https://images.unsplash.com/photo-1556905055-8f358a7a47b2?w=1200&q=80',
}
