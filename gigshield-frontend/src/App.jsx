import { useEffect, useState } from 'react'
import { BrowserRouter, Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import Sidebar from './components/Sidebar'
import ToastHost from './components/ToastHost'
import { AuthProvider, useAuth } from './context/AuthContext'
import { ToastProvider } from './context/ToastContext'
import BuyPolicy from './pages/BuyPolicy'
import Claims from './pages/Claims'
import Dashboard from './pages/Dashboard'
import History from './pages/History'
import LoginPage from './pages/Login'
import Plans from './pages/Plans'

function RequireAuth() {
  const { isAuthenticated } = useAuth()
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  return <Outlet />
}

function AppLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const { worker } = useAuth()
  const location = useLocation()

  useEffect(() => {
    setSidebarOpen(false)
  }, [location.pathname])

  useEffect(() => {
    document.body.style.overflow = sidebarOpen ? 'hidden' : ''

    return () => {
      document.body.style.overflow = ''
    }
  }, [sidebarOpen])

  return (
    <div className="app-shell">
      <button
        type="button"
        className={`app-shell__backdrop ${sidebarOpen ? 'app-shell__backdrop--open' : ''}`}
        aria-label="Close navigation menu"
        onClick={() => setSidebarOpen(false)}
      />

      <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} onNavigate={() => setSidebarOpen(false)} />

      <div className="app-shell__content">
        <header className="app-topbar">
          <button
            type="button"
            className="menu-button"
            aria-label="Open navigation menu"
            onClick={() => setSidebarOpen(true)}
          >
            ☰
          </button>
          <div className="app-topbar__copy">
            <p className="app-topbar__eyebrow">GigShield</p>
            <strong>{worker.name}</strong>
          </div>
        </header>

        <main className="app-main">
          <Outlet />
        </main>
      </div>
      <ToastHost />
    </div>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <ToastProvider>
          <Routes>
            <Route path="/login" element={<LoginPage initialMode="login" />} />
            <Route path="/register" element={<LoginPage initialMode="register" />} />
            <Route element={<RequireAuth />}>
              <Route element={<AppLayout />}>
                <Route index element={<Dashboard />} />
                <Route path="buy-policy" element={<BuyPolicy />} />
                <Route path="claims" element={<Claims />} />
                <Route path="history" element={<History />} />
                <Route path="plans" element={<Plans />} />
              </Route>
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </ToastProvider>
      </AuthProvider>
    </BrowserRouter>
  )
}
