import { Routes, Route, Navigate } from 'react-router-dom'
import { Layout } from './components/Layout'
import { AdminLayout } from './pages/admin/AdminLayout'
import { AdminDashboard } from './pages/admin/AdminDashboard'
import { AdminProductsPage } from './pages/admin/AdminProductsPage'
import { AdminDropsPage } from './pages/admin/AdminDropsPage'
import { AdminRafflesPage } from './pages/admin/AdminRafflesPage'
import { AdminCouponsPage } from './pages/admin/AdminCouponsPage'
import { AdminPaymentsPage } from './pages/admin/AdminPaymentsPage'
import { AdminDLQPage } from './pages/admin/AdminDLQPage'
import { AdminOutboxPage } from './pages/admin/AdminOutboxPage'
import { HomePage } from './pages/HomePage'
import { LoginPage } from './pages/auth/LoginPage'
import { SignupPage } from './pages/auth/SignupPage'
import { ProductsPage } from './pages/products/ProductsPage'
import { ProductDetailPage } from './pages/products/ProductDetailPage'
import { DropsPage } from './pages/drops/DropsPage'
import { DropDetailPage } from './pages/drops/DropDetailPage'
import { RafflesPage } from './pages/raffles/RafflesPage'
import { RaffleDetailPage } from './pages/raffles/RaffleDetailPage'
import { RaffleWinnersPage } from './pages/raffles/RaffleWinnersPage'
import { OrdersPage } from './pages/orders/OrdersPage'
import { OrderDetailPage } from './pages/orders/OrderDetailPage'
import { MyPage } from './pages/mypage/MyPage'
import { useAuthStore } from './store/authStore'

function RequireAuth({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuthStore()
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" replace />
}

export default function App() {
  return (
    <Routes>
      <Route path="/admin" element={<AdminLayout />}>
        <Route index element={<AdminDashboard />} />
        <Route path="products" element={<AdminProductsPage />} />
        <Route path="drops" element={<AdminDropsPage />} />
        <Route path="raffles" element={<AdminRafflesPage />} />
        <Route path="coupons" element={<AdminCouponsPage />} />
        <Route path="payments" element={<AdminPaymentsPage />} />
        <Route path="dlq" element={<AdminDLQPage />} />
        <Route path="outbox" element={<AdminOutboxPage />} />
      </Route>

      <Route element={<Layout />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/products" element={<ProductsPage />} />
        <Route path="/products/:productId" element={<ProductDetailPage />} />
        <Route path="/drops" element={<DropsPage />} />
        <Route path="/drops/:dropId" element={<DropDetailPage />} />
        <Route path="/raffles" element={<RafflesPage />} />
        <Route path="/raffles/:raffleId" element={<RaffleDetailPage />} />
        <Route path="/raffles/:raffleId/winners" element={<RaffleWinnersPage />} />
        <Route path="/orders" element={<RequireAuth><OrdersPage /></RequireAuth>} />
        <Route path="/orders/:orderId" element={<RequireAuth><OrderDetailPage /></RequireAuth>} />
        <Route path="/mypage" element={<RequireAuth><MyPage /></RequireAuth>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
