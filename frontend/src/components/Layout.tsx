import { useState } from 'react'
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'
import { ShoppingBag, Ticket, Package, User, LogOut, Menu, X, ShoppingCart, Settings } from 'lucide-react'

export function Layout() {
  const { isAuthenticated, user, logout } = useAuthStore()
  const navigate = useNavigate()
  const location = useLocation()
  const [mobileOpen, setMobileOpen] = useState(false)

  const handleLogout = () => { logout(); navigate('/login') }

  const navLinks = [
    { to: '/products', label: 'PRODUCTS', icon: Package },
    { to: '/drops', label: 'DROPS', icon: ShoppingBag },
    { to: '/raffles', label: 'RAFFLE', icon: Ticket },
  ]

  const isActive = (path: string) => location.pathname.startsWith(path)

  return (
    <div className="min-h-screen bg-white">
      {/* Top banner */}
      <div className="bg-black py-2 text-center text-xs tracking-widest text-white/70">
        FREE SHIPPING ON ORDERS OVER &#8361;100,000
      </div>

      <header className="sticky top-0 z-50 border-b border-gray-100 bg-white/95 backdrop-blur-md">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 lg:px-8">

          {/* Logo */}
          <Link to="/" className="flex items-center gap-1">
            <span className="text-2xl font-black tracking-tighter text-black">SOLD</span>
            <span className="text-2xl font-black tracking-tighter text-red-500">OUT</span>
          </Link>

          {/* Desktop Nav */}
          <nav className="hidden md:flex items-center gap-8">
            {navLinks.map(({ to, label }) => (
              <Link
                key={to}
                to={to}
                className={`text-xs font-bold tracking-widest transition-colors ${
                  isActive(to) ? 'text-black border-b-2 border-black pb-0.5' : 'text-gray-400 hover:text-black'
                }`}
              >
                {label}
              </Link>
            ))}
          </nav>

          {/* Right actions */}
          <div className="hidden md:flex items-center gap-4">
            {isAuthenticated ? (
              <>
                <Link to="/orders" className="text-gray-500 hover:text-black transition-colors">
                  <ShoppingCart size={20} />
                </Link>
                <Link
                  to="/mypage"
                  className="flex items-center gap-1.5 text-xs font-bold tracking-wide text-gray-700 hover:text-black transition-colors"
                >
                  <User size={16} />
                  {(user?.nickname ?? user?.username ?? 'MY PAGE').toUpperCase()}
                </Link>
                <Link to="/admin" className="text-gray-300 hover:text-black transition-colors" title="관리자">
                  <Settings size={16} />
                </Link>
                <button
                  onClick={handleLogout}
                  className="flex items-center gap-1 text-xs text-gray-400 hover:text-red-500 transition-colors"
                >
                  <LogOut size={15} />
                </button>
              </>
            ) : (
              <>
                <Link to="/login" className="text-xs font-bold tracking-wide text-gray-500 hover:text-black transition-colors">
                  LOGIN
                </Link>
                <Link
                  to="/signup"
                  className="rounded-none bg-black px-5 py-2 text-xs font-bold tracking-widest text-white hover:bg-gray-800 transition-colors"
                >
                  JOIN
                </Link>
              </>
            )}
          </div>

          {/* Mobile toggle */}
          <button className="md:hidden p-1" onClick={() => setMobileOpen(!mobileOpen)}>
            {mobileOpen ? <X size={22} /> : <Menu size={22} />}
          </button>
        </div>

        {/* Mobile Nav */}
        {mobileOpen && (
          <div className="md:hidden border-t border-gray-100 bg-white">
            <div className="px-4 py-4 space-y-1">
              {navLinks.map(({ to, label, icon: Icon }) => (
                <Link
                  key={to}
                  to={to}
                  onClick={() => setMobileOpen(false)}
                  className="flex items-center gap-3 rounded px-3 py-3 text-sm font-bold tracking-wider text-gray-700 hover:bg-gray-50"
                >
                  <Icon size={16} />{label}
                </Link>
              ))}
              <div className="border-t border-gray-100 pt-3 mt-3">
                {isAuthenticated ? (
                  <>
                    <Link to="/orders" onClick={() => setMobileOpen(false)} className="flex items-center gap-3 px-3 py-3 text-sm font-bold tracking-wider text-gray-700 hover:bg-gray-50"><ShoppingCart size={16} />ORDERS</Link>
                    <Link to="/mypage" onClick={() => setMobileOpen(false)} className="flex items-center gap-3 px-3 py-3 text-sm font-bold tracking-wider text-gray-700 hover:bg-gray-50"><User size={16} />MY PAGE</Link>
                    <button onClick={() => { handleLogout(); setMobileOpen(false) }} className="flex w-full items-center gap-3 px-3 py-3 text-sm font-bold text-red-500 hover:bg-gray-50"><LogOut size={16} />LOGOUT</button>
                  </>
                ) : (
                  <>
                    <Link to="/login" onClick={() => setMobileOpen(false)} className="block px-3 py-3 text-sm font-bold tracking-wider text-gray-700">LOGIN</Link>
                    <Link to="/signup" onClick={() => setMobileOpen(false)} className="block px-3 py-3 text-sm font-bold tracking-wider text-gray-700">JOIN</Link>
                  </>
                )}
              </div>
            </div>
          </div>
        )}
      </header>

      <main className="mx-auto max-w-7xl px-4 lg:px-8 py-8">
        <Outlet />
      </main>

      {/* Footer */}
      <footer className="mt-20 border-t border-gray-100 bg-black py-12 text-center">
        <div className="flex items-center justify-center gap-1 mb-3">
          <span className="text-xl font-black tracking-tighter text-white">SOLD</span>
          <span className="text-xl font-black tracking-tighter text-red-500">OUT</span>
        </div>
        <p className="text-xs text-gray-500 tracking-widest">&#169; 2025 SOLDOUT. ALL RIGHTS RESERVED.</p>
      </footer>
    </div>
  )
}
