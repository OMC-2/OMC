export interface ApiResponse<T> {
  success: boolean
  status: number
  data: T
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export interface User {
  userId: string
  email: string
  username: string
  role: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
}

export interface Product {
  productId: string
  name: string
  description: string
  price: number
  stockQuantity: number
  imageUrl?: string
  category?: string
  createdAt: string
}

export interface Drop {
  dropId: string
  productId: string
  productName: string
  price: number
  startAt: string
  endAt: string
  stockQuantity: number
  status: 'SCHEDULED' | 'OPEN' | 'CLOSED' | 'SOLD_OUT'
  imageUrl?: string
}

export interface Raffle {
  raffleId: string
  name: string
  productId: string
  productName?: string
  winnerCount: number
  startedAt: string
  endedAt: string
  status: 'SCHEDULED' | 'OPEN' | 'CLOSED'
  imageUrl?: string
  participantsCount?: number
}

export interface RaffleResult {
  userId: string
  username?: string
  isWinner: boolean
  decidedAt: string
}

export interface Order {
  orderId: string
  productId: string
  productName: string
  quantity: number
  totalAmount: number
  status: string
  createdAt: string
}

export interface Coupon {
  couponId: string
  code: string
  discountAmount: number
  discountType: 'FIXED' | 'PERCENT'
  expiresAt: string
  isUsed: boolean
}
