import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Link, Navigate } from 'react-router-dom'
import { authApi } from '../../api/auth'
import { rafflesApi } from '../../api/raffles'
import { ordersApi } from '../../api/orders'
import { couponsApi } from '../../api/coupons'
import { addressesApi } from '../../api/addresses'
import { Spinner } from '../../components/ui/Spinner'
import { useAuthStore } from '../../store/authStore'
import { formatDate } from '../../lib/utils'
import { LogOut, ChevronRight, CreditCard, Tag, CheckCircle, MapPin, Plus, Trash2, Star, Edit2, X } from 'lucide-react'
import { toast } from '../../components/ui/Toast'

const BILLING_KEY_STORAGE = 'omc_billing_key'

function randomHex(len: number) {
  return Array.from({ length: len }, () => Math.floor(Math.random() * 16).toString(16)).join('')
}

export function MyPage() {
  const { isAuthenticated, logout } = useAuthStore()
  const qc = useQueryClient()
  const [showCardForm, setShowCardForm]       = useState(false)
  const [cardNum, setCardNum]                 = useState('')
  const [cardExpiry, setCardExpiry]           = useState('')
  const [, forceUpdate]                       = useState(0)
  const storedBillingKey = localStorage.getItem(BILLING_KEY_STORAGE) ?? ''

  const { data: profileData, isLoading } = useQuery({
    queryKey: ['profile'],
    queryFn: () => authApi.getProfile(),
    enabled: isAuthenticated,
  })
  const { data: myEntriesData } = useQuery({
    queryKey: ['my-entries'],
    queryFn: () => rafflesApi.getMyEntries(),
    enabled: isAuthenticated,
  })
  const { data: ordersData } = useQuery({
    queryKey: ['orders'],
    queryFn: () => ordersApi.getMyOrders(),
    enabled: isAuthenticated,
  })
  const { data: couponsData } = useQuery({
    queryKey: ['my-coupons'],
    queryFn: () => couponsApi.getMyCoupons(),
    enabled: isAuthenticated,
  })

  const registerCard = () => {
    if (!cardNum.trim() || !cardExpiry.trim()) return
    const customerKey = randomHex(8) + '-' + randomHex(4) + '-' + randomHex(4) + '-' + randomHex(12)
    const billingKeyId = 'test-billing-' + customerKey
    localStorage.setItem(BILLING_KEY_STORAGE, billingKeyId)
    setShowCardForm(false)
    setCardNum('')
    setCardExpiry('')
    forceUpdate(n => n + 1)
    toast.success('결제 수단 등록 완료')
  }

  const [showAddressForm, setShowAddressForm] = useState(false)
  const [addrForm, setAddrForm] = useState({ recipientName: '', phone: '', zipCode: '', address: '', addressDetail: '', isDefault: false })
  const [editAddress, setEditAddress]   = useState<any | null>(null)
  const [editAddrForm, setEditAddrForm] = useState({ recipientName: '', phone: '', zipCode: '', address: '', addressDetail: '' })
  const [showProfileEdit, setShowProfileEdit] = useState(false)
  const [profileForm, setProfileForm]   = useState({ nickname: '', slackId: '' })

  const { data: addressData, refetch: refetchAddresses } = useQuery({
    queryKey: ['addresses'],
    queryFn: () => addressesApi.getAll(),
    enabled: isAuthenticated,
  })

  const createAddressMutation = useMutation({
    mutationFn: () => addressesApi.create({
      recipientName: addrForm.recipientName,
      phone: addrForm.phone,
      zipCode: addrForm.zipCode,
      address: addrForm.address,
      addressDetail: addrForm.addressDetail || undefined,
      isDefault: addrForm.isDefault,
    }),
    onSuccess: () => {
      refetchAddresses()
      setShowAddressForm(false)
      setAddrForm({ recipientName: '', phone: '', zipCode: '', address: '', addressDetail: '', isDefault: false })
      toast.success('주소 추가 완료')
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '주소 추가 실패'),
  })

  const deleteAddressMutation = useMutation({
    mutationFn: (id: string) => addressesApi.delete(id),
    onSuccess: () => { refetchAddresses(); toast.success('주소 삭제 완료') },
  })

  const setDefaultAddressMutation = useMutation({
    mutationFn: (id: string) => addressesApi.setDefault(id),
    onSuccess: () => { refetchAddresses(); toast.success('기본 주소 설정 완료') },
  })

  const updateAddressMutation = useMutation({
    mutationFn: () => addressesApi.update(editAddress.addressId, {
      recipientName: editAddrForm.recipientName || undefined,
      phone: editAddrForm.phone || undefined,
      zipCode: editAddrForm.zipCode || undefined,
      address: editAddrForm.address || undefined,
      addressDetail: editAddrForm.addressDetail || undefined,
    }),
    onSuccess: () => {
      refetchAddresses()
      setEditAddress(null)
      toast.success('주소 수정 완료')
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '주소 수정 실패'),
  })

  const updateProfileMutation = useMutation({
    mutationFn: () => authApi.updateProfile({ nickname: profileForm.nickname || undefined, slackId: profileForm.slackId || undefined }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['profile'] })
      setShowProfileEdit(false)
      toast.success('프로필 수정 완료')
    },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '수정 실패'),
  })

  const deleteAccountMutation = useMutation({
    mutationFn: () => authApi.deleteAccount(),
    onSuccess: () => { logout() },
    onError: (e: any) => toast.error(e?.response?.data?.message ?? '탈퇴 실패'),
  })

  if (!isAuthenticated) return <Navigate to="/login" replace />
  if (isLoading) return <Spinner className="py-20" />

  const profile     = profileData?.data?.data
  const myEntries   = myEntriesData?.data?.data?.content ?? []
  const orders      = ordersData?.data?.data?.content ?? []
  const coupons     = couponsData?.data?.data?.content ?? []
  const addresses   = addressData?.data?.data?.content ?? []
  const displayName = (profile?.nickname ?? profile?.username ?? profile?.email ?? 'USER').toString().toUpperCase()

  return (
    <div className="mx-auto max-w-2xl">
      <div className="mb-8 border-b border-gray-100 pb-6">
        <p className="text-[10px] font-bold tracking-[0.3em] text-gray-400 mb-1">ACCOUNT</p>
        <h1 className="text-3xl font-black tracking-tight">MY PAGE</h1>
      </div>

      {/* 프로필 카드 */}
      <div className="mb-8 bg-black p-8 relative overflow-hidden">
        <div className="absolute inset-0 bg-[url('https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=600&q=60')] bg-cover opacity-10" />
        <div className="relative flex items-center justify-between">
          <div>
            <p className="text-[10px] font-bold tracking-[0.3em] text-white/40 mb-1">MEMBER</p>
            <p className="text-2xl font-black tracking-tight text-white">{displayName}</p>
            <p className="text-sm text-white/40 mt-1">{profile?.email}</p>
          </div>
          <button onClick={logout} className="flex items-center gap-1.5 text-[10px] font-bold tracking-widest text-white/30 hover:text-red-400 transition-colors">
            <LogOut size={14} />LOGOUT
          </button>
        </div>
        <div className="relative mt-6 grid grid-cols-3 gap-4">
          {[
            { label: 'RAFFLES', value: myEntries.length },
            { label: 'COUPONS', value: coupons.length },
            { label: 'ORDERS',  value: orders.length },
          ].map(({ label, value }) => (
            <div key={label} className="border border-white/10 p-3 text-center">
              <p className="text-2xl font-black text-white">{value}</p>
              <p className="text-[10px] text-white/30 tracking-wider mt-1">{label}</p>
            </div>
          ))}
        </div>
      </div>

      {/* 결제 수단 */}
      <div className="mb-8 border border-gray-100">
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
          <div className="flex items-center gap-2">
            <CreditCard size={14} className="text-gray-400" />
            <p className="text-xs font-black tracking-widest text-gray-900">결제 수단</p>
          </div>
          {storedBillingKey
            ? <button onClick={() => setShowCardForm(v => !v)} className="text-[10px] font-bold text-gray-400 hover:text-black">변경</button>
            : <button onClick={() => setShowCardForm(v => !v)} className="bg-black px-3 py-1.5 text-[10px] font-black text-white hover:bg-red-500 transition-colors">+ 등록</button>
          }
        </div>
        {storedBillingKey && !showCardForm && (
          <div className="flex items-center gap-3 px-5 py-3">
            <CheckCircle size={14} className="text-green-500 shrink-0" />
            <div>
              <p className="text-xs font-bold text-gray-800">결제 수단 등록됨</p>
              <p className="text-[10px] font-mono text-gray-400 mt-0.5">{storedBillingKey.slice(0, 28)}...</p>
            </div>
          </div>
        )}
        {showCardForm && (
          <div className="px-5 py-4 space-y-3">
            <div>
              <label className="block text-[10px] font-bold text-gray-400 tracking-wider mb-1">카드 번호</label>
              <input
                type="text"
                maxLength={19}
                placeholder="0000 0000 0000 0000"
                value={cardNum}
                onChange={e => {
                  const v = e.target.value.replace(/\D/g, '').slice(0, 16)
                  setCardNum(v.replace(/(.{4})/g, '$1 ').trim())
                }}
                className="w-full border border-gray-200 px-3 py-2 text-sm font-mono outline-none focus:border-black tracking-widest"
              />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-[10px] font-bold text-gray-400 tracking-wider mb-1">유효기간</label>
                <input
                  type="text"
                  maxLength={5}
                  placeholder="MM/YY"
                  value={cardExpiry}
                  onChange={e => {
                    const v = e.target.value.replace(/\D/g, '').slice(0, 4)
                    setCardExpiry(v.length > 2 ? v.slice(0, 2) + '/' + v.slice(2) : v)
                  }}
                  className="w-full border border-gray-200 px-3 py-2 text-sm font-mono outline-none focus:border-black"
                />
              </div>
              <div>
                <label className="block text-[10px] font-bold text-gray-400 tracking-wider mb-1">CVC</label>
                <input type="text" maxLength={3} placeholder="000"
                  className="w-full border border-gray-200 px-3 py-2 text-sm font-mono outline-none focus:border-black" />
              </div>
            </div>
            <div className="flex gap-2 pt-1">
              <button onClick={() => setShowCardForm(false)} className="flex-1 border border-gray-200 py-2 text-xs font-bold text-gray-400 hover:text-black transition-colors">
                취소
              </button>
              <button
                onClick={registerCard}
                disabled={cardNum.replace(/\s/g, '').length < 16 || cardExpiry.length < 5}
                className="flex-1 bg-black py-2 text-xs font-black text-white hover:bg-red-500 disabled:bg-gray-200 disabled:text-gray-400 transition-colors"
              >
                등록
              </button>
            </div>
          </div>
        )}
      </div>

      {/* 쿠폰 */}
      <div className="mb-8 border border-gray-100">
        <div className="flex items-center gap-2 px-5 py-4 border-b border-gray-100">
          <Tag size={14} className="text-gray-400" />
          <p className="text-xs font-black tracking-widest text-gray-900">쿠폰</p>
        </div>
        <div className="px-5 py-4 border-b border-gray-100">
          <p className="text-[10px] text-gray-400 mb-3">선착순 쿠폰을 발급받으세요</p>
          <Link to="/coupons" className="inline-flex items-center gap-2 bg-black px-4 py-2.5 text-[10px] font-black tracking-widest text-white hover:bg-red-500 transition-colors">
            <Tag size={11} />쿠폰 발급하기
          </Link>
        </div>
        {coupons.length === 0 ? (
          <div className="px-5 py-6 text-center">
            <p className="text-xs text-gray-300 tracking-wider">보유 쿠폰 없음</p>
          </div>
        ) : (
          <div className="divide-y divide-gray-100">
            {coupons.map((coupon: any) => {
              const isValid = coupon.status === 'AVAILABLE' || (!coupon.status && !coupon.used)
              const discountText = coupon.discountType === 'AMOUNT'
                ? `${Number(coupon.discountValue).toLocaleString()}원 할인`
                : `${(Number(coupon.discountValue) * 100).toFixed(0)}% 할인`
              return (
                <div key={coupon.userCouponId ?? coupon.couponId} className="flex items-center justify-between px-5 py-4">
                  <div>
                    <p className="text-xs font-bold text-gray-900">{coupon.couponName ?? coupon.name}</p>
                    <p className="text-[10px] text-gray-400 mt-0.5">{discountText}</p>
                    {coupon.maxDiscountAmount && coupon.discountType === 'RATE' &&
                      <p className="text-[10px] text-gray-300">최대 {Number(coupon.maxDiscountAmount).toLocaleString()}원</p>}
                    {coupon.expiredAt && <p className="text-[10px] text-gray-300 mt-0.5">~{formatDate(coupon.expiredAt)}</p>}
                  </div>
                  <span className={`shrink-0 text-[10px] font-black tracking-wider px-2 py-1 ${isValid ? 'text-red-500 bg-red-50' : 'text-gray-300 bg-gray-50'}`}>
                    {isValid ? 'VALID' : coupon.status ?? 'USED'}
                  </span>
                </div>
              )
            })}
          </div>
        )}
      </div>

      {/* 주소 */}
      <div className="mb-8 border border-gray-100">
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
          <div className="flex items-center gap-2">
            <MapPin size={14} className="text-gray-400" />
            <p className="text-xs font-black tracking-widest text-gray-900">배송지</p>
          </div>
          <button onClick={() => setShowAddressForm(v => !v)} className="flex items-center gap-1 bg-black px-3 py-1.5 text-[10px] font-black text-white hover:bg-red-500 transition-colors">
            <Plus size={11} />추가
          </button>
        </div>
        {showAddressForm && (
          <div className="px-5 py-4 border-b border-gray-100 bg-gray-50 space-y-2">
            {[
              { label: '수령인 *', key: 'recipientName', placeholder: '홍길동' },
              { label: '연락처 *', key: 'phone',         placeholder: '01012345678' },
              { label: '우편번호 *', key: 'zipCode',     placeholder: '12345' },
              { label: '주소 *',   key: 'address',       placeholder: '서울시 강남구...' },
              { label: '상세주소', key: 'addressDetail', placeholder: '101호' },
            ].map(({ label, key, placeholder }) => (
              <div key={key}>
                <label className="block text-[10px] font-bold text-gray-400 mb-0.5">{label}</label>
                <input type="text" placeholder={placeholder} value={(addrForm as any)[key]}
                  onChange={e => setAddrForm(p => ({ ...p, [key]: e.target.value }))}
                  className="w-full border border-gray-200 px-3 py-1.5 text-xs outline-none focus:border-gray-400" />
              </div>
            ))}
            <label className="flex items-center gap-2 text-xs text-gray-600">
              <input type="checkbox" checked={addrForm.isDefault} onChange={e => setAddrForm(p => ({ ...p, isDefault: e.target.checked }))} />
              기본 배송지로 설정
            </label>
            <div className="flex gap-2 pt-1">
              <button onClick={() => setShowAddressForm(false)} className="flex-1 border border-gray-200 py-2 text-xs font-bold text-gray-400">취소</button>
              <button
                disabled={!addrForm.recipientName || !addrForm.phone || !addrForm.zipCode || !addrForm.address || createAddressMutation.isPending}
                onClick={() => createAddressMutation.mutate()}
                className="flex-1 bg-black py-2 text-xs font-black text-white hover:bg-red-500 disabled:bg-gray-200 disabled:text-gray-400 transition-colors"
              >
                {createAddressMutation.isPending ? '저장 중...' : '저장'}
              </button>
            </div>
          </div>
        )}
        {addresses.length === 0 ? (
          <div className="px-5 py-6 text-center">
            <p className="text-xs text-gray-300 tracking-wider">등록된 배송지 없음</p>
          </div>
        ) : (
          <div className="divide-y divide-gray-100">
            {addresses.map((addr: any) => (
              <div key={addr.addressId} className="flex items-start justify-between px-5 py-4">
                <div>
                  <div className="flex items-center gap-2">
                    <p className="text-xs font-bold text-gray-900">{addr.recipientName}</p>
                    {addr.isDefault && <span className="px-1.5 py-0.5 text-[9px] font-black bg-black text-white tracking-wider">DEFAULT</span>}
                  </div>
                  <p className="text-[10px] text-gray-500 mt-0.5">{addr.phone}</p>
                  <p className="text-[10px] text-gray-500">[{addr.zipCode}] {addr.address} {addr.addressDetail}</p>
                </div>
                <div className="flex gap-2 shrink-0 ml-3">
                  {!addr.isDefault && (
                    <button onClick={() => setDefaultAddressMutation.mutate(addr.addressId)} className="text-gray-300 hover:text-yellow-500 transition-colors" title="기본 배송지로">
                      <Star size={13} />
                    </button>
                  )}
                  <button
                    onClick={() => {
                      setEditAddress(addr)
                      setEditAddrForm({ recipientName: addr.recipientName, phone: addr.phone, zipCode: addr.zipCode, address: addr.address, addressDetail: addr.addressDetail ?? '' })
                    }}
                    className="text-gray-300 hover:text-blue-400 transition-colors" title="수정"
                  >
                    <Edit2 size={13} />
                  </button>
                  <button onClick={() => deleteAddressMutation.mutate(addr.addressId)} className="text-gray-300 hover:text-red-400 transition-colors">
                    <Trash2 size={13} />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 프로필 수정 */}
      <div className="mb-8 border border-gray-100">
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
          <div className="flex items-center gap-2">
            <Edit2 size={14} className="text-gray-400" />
            <p className="text-xs font-black tracking-widest text-gray-900">프로필</p>
          </div>
          <button
            onClick={() => { setShowProfileEdit(v => !v); setProfileForm({ nickname: profile?.nickname ?? '', slackId: profile?.slackId ?? '' }) }}
            className="text-[10px] font-bold text-gray-400 hover:text-black transition-colors"
          >
            {showProfileEdit ? '취소' : '수정'}
          </button>
        </div>
        {showProfileEdit ? (
          <div className="px-5 py-4 space-y-3">
            <div>
              <label className="block text-[10px] font-bold text-gray-400 mb-1">닉네임</label>
              <input type="text" placeholder={profile?.nickname ?? '닉네임'} value={profileForm.nickname}
                onChange={e => setProfileForm(p => ({ ...p, nickname: e.target.value }))}
                className="w-full border border-gray-200 px-3 py-2 text-xs outline-none focus:border-gray-400" />
            </div>
            <div>
              <label className="block text-[10px] font-bold text-gray-400 mb-1">Slack ID</label>
              <input type="text" placeholder="U0XXXXXXXXX" value={profileForm.slackId}
                onChange={e => setProfileForm(p => ({ ...p, slackId: e.target.value }))}
                className="w-full border border-gray-200 px-3 py-2 text-xs outline-none focus:border-gray-400" />
            </div>
            <button
              disabled={updateProfileMutation.isPending}
              onClick={() => updateProfileMutation.mutate()}
              className="w-full bg-black py-2.5 text-xs font-black text-white hover:bg-red-500 disabled:bg-gray-200 transition-colors"
            >
              {updateProfileMutation.isPending ? '저장 중...' : '저장'}
            </button>
          </div>
        ) : (
          <div className="px-5 py-4 space-y-1">
            <p className="text-xs text-gray-600"><span className="text-gray-400 mr-2">닉네임</span>{profile?.nickname ?? '-'}</p>
            <p className="text-xs text-gray-600"><span className="text-gray-400 mr-2">이메일</span>{profile?.email}</p>
          </div>
        )}
        <div className="px-5 py-3 border-t border-gray-50">
          <button
            onClick={() => { if (confirm('정말 탈퇴하시겠습니까?')) deleteAccountMutation.mutate() }}
            className="text-[10px] text-gray-300 hover:text-red-400 transition-colors"
          >
            회원 탈퇴
          </button>
        </div>
      </div>

      {/* 빠른 이동 */}
      <div className="mb-8 divide-y divide-gray-100 border border-gray-100">
        <Link to="/orders" className="flex items-center justify-between px-5 py-4 hover:bg-gray-50 transition-colors">
          <span className="text-xs font-black tracking-wider">ORDERS</span>
          <ChevronRight size={16} className="text-gray-300" />
        </Link>
        <Link to="/raffles" className="flex items-center justify-between px-5 py-4 hover:bg-gray-50 transition-colors">
          <span className="text-xs font-black tracking-wider">BROWSE RAFFLES</span>
          <ChevronRight size={16} className="text-gray-300" />
        </Link>
        <Link to="/drops" className="flex items-center justify-between px-5 py-4 hover:bg-gray-50 transition-colors">
          <span className="text-xs font-black tracking-wider">BROWSE DROPS</span>
          <ChevronRight size={16} className="text-gray-300" />
        </Link>
      </div>

      {/* 래플 히스토리 */}
      <div className="mb-8">
        <p className="text-xs font-black tracking-widest text-gray-900 mb-4">RAFFLE HISTORY</p>
        {myEntries.length === 0 ? (
          <div className="border border-gray-100 py-10 text-center">
            <p className="text-xs text-gray-300 tracking-wider">NO ENTRIES YET</p>
          </div>
        ) : (
          <div className="divide-y divide-gray-100 border border-gray-100">
            {myEntries.slice(0, 5).map((entry: any) => (
              <Link key={entry.entryId} to={`/raffles/${entry.raffleId}`} className="flex items-center justify-between px-5 py-4 hover:bg-gray-50 transition-colors">
                <div>
                  <p className="text-xs font-bold text-gray-900 truncate max-w-[200px]">{entry.raffleName ?? entry.raffleId}</p>
                  <p className="text-[10px] text-gray-400 mt-0.5">{entry.enteredAt ? formatDate(entry.enteredAt) : ''}</p>
                </div>
                <span className={`text-[10px] font-black tracking-wider px-2 py-1 ${
                  entry.result === 'WIN'  ? 'text-yellow-600 bg-yellow-50'
                  : entry.result === 'LOSE' ? 'text-gray-400 bg-gray-50'
                  : 'text-green-600 bg-green-50'
                }`}>
                  {entry.result === 'WIN' ? '당첨' : entry.result === 'LOSE' ? '미당첨' : 'ENTERED'}
                </span>
              </Link>
            ))}
          </div>
        )}
      </div>

      {/* 최근 주문 */}
      {orders.length > 0 && (
        <div className="mb-8">
          <p className="text-xs font-black tracking-widest text-gray-900 mb-4">RECENT ORDERS</p>
          <div className="divide-y divide-gray-100 border border-gray-100">
            {orders.slice(0, 3).map((order: any) => {
              const label = order.salesType === 'DROP'   ? 'DROP 구매'
                : order.salesType === 'RAFFLE' ? '래플 당첨'
                : order.salesType ?? '주문'
              const pStatus = order.paymentStatus ?? ''
              const statusClass = pStatus === 'PAID'     ? 'text-green-600 bg-green-50'
                : pStatus === 'CANCELED' ? 'text-red-400 bg-red-50'
                : pStatus === 'FAILED'   ? 'text-gray-400 bg-gray-50'
                : 'text-blue-500 bg-blue-50'
              const href = order.orderId ? `/orders/${order.orderId}` : '/orders'
              return (
                <Link key={order.paymentId} to={href} className="flex items-center justify-between px-5 py-4 hover:bg-gray-50 transition-colors">
                  <div>
                    <p className="text-xs font-bold text-gray-900 truncate max-w-[200px]">{label}</p>
                    {order.requestedAt && <p className="text-[10px] text-gray-400 mt-0.5">{formatDate(order.requestedAt)}</p>}
                    {order.finalAmount != null && <p className="text-[10px] text-gray-500 mt-0.5">{Number(order.finalAmount).toLocaleString()}원</p>}
                  </div>
                  <span className={`text-[10px] font-black tracking-wider px-2 py-1 ${statusClass}`}>
                    {pStatus || '-'}
                  </span>
                </Link>
              )
            })}
          </div>
          {orders.length > 3 && (
            <Link to="/orders" className="mt-3 block text-center text-xs font-bold text-gray-400 hover:text-black transition-colors">
              전체 보기 ({orders.length}) →
            </Link>
          )}
        </div>
      )}

      {/* 주소 수정 모달 */}
      {editAddress && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-sm border border-gray-100 bg-white p-6 shadow-xl">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-sm font-black tracking-wider">배송지 수정</h2>
              <button onClick={() => setEditAddress(null)}><X size={16} className="text-gray-300 hover:text-black" /></button>
            </div>
            <div className="space-y-2 mb-4">
              {[
                { label: '수령인', key: 'recipientName', placeholder: '홍길동' },
                { label: '연락처', key: 'phone',         placeholder: '01012345678' },
                { label: '우편번호', key: 'zipCode',     placeholder: '12345' },
                { label: '주소',   key: 'address',       placeholder: '서울시 강남구...' },
                { label: '상세주소', key: 'addressDetail', placeholder: '101호' },
              ].map(({ label, key, placeholder }) => (
                <div key={key}>
                  <label className="block text-[10px] font-bold text-gray-400 mb-0.5">{label}</label>
                  <input
                    type="text"
                    placeholder={placeholder}
                    value={(editAddrForm as any)[key]}
                    onChange={e => setEditAddrForm(p => ({ ...p, [key]: e.target.value }))}
                    className="w-full border border-gray-100 px-3 py-1.5 text-xs outline-none focus:border-gray-300"
                  />
                </div>
              ))}
            </div>
            <div className="flex gap-2">
              <button onClick={() => setEditAddress(null)} className="flex-1 border border-gray-200 py-2 text-xs font-bold text-gray-400 hover:text-black transition-colors">
                취소
              </button>
              <button
                disabled={updateAddressMutation.isPending}
                onClick={() => updateAddressMutation.mutate()}
                className="flex-1 bg-black py-2 text-xs font-black text-white hover:bg-red-500 disabled:bg-gray-200 transition-colors"
              >
                {updateAddressMutation.isPending ? '저장 중...' : '저장'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
