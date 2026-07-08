// Unsplash 라이선스 — 무료 상업적 사용 가능
// 제품처럼 보이는 이미지 (신발, 의류, 가방, 시계 등 - 로고 미노출)

export const PLACEHOLDER_IMAGES = [
  // 신발 / 스니커즈 계열 (로고 안 보이는 각도)
  'https://images.unsplash.com/photo-1542838132-92c53300491e?w=600&q=80', // 다크 스니커즈 탑뷰
  'https://images.unsplash.com/photo-1460353581641-37baddab0fa2?w=600&q=80', // 화이트 스니커즈
  'https://images.unsplash.com/photo-1583743814966-8936f5b7be1a?w=600&q=80', // 클린 스니커즈
  'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=600&q=80', // 레드 스니커즈
  // 의류 (후드/자켓)
  'https://images.unsplash.com/photo-1523381210434-271e8be1f52b?w=600&q=80', // 후드 티셔츠
  'https://images.unsplash.com/photo-1556905055-8f358a7a47b2?w=600&q=80', // 자켓/아우터
  'https://images.unsplash.com/photo-1521369909029-2afed882baee?w=600&q=80', // 캡
  // 가방 / 액세서리
  'https://images.unsplash.com/photo-1548036328-c9fa89d128fa?w=600&q=80', // 가죽 백
  'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=600&q=80', // 백팩
  'https://images.unsplash.com/photo-1592492152545-9695d3f473f4?w=600&q=80', // 시계
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
