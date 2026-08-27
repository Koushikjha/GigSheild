import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import * as authApi from '../api/auth'
import { getWorkerDashboard } from '../api/dashboard'
import { AUTH_EXPIRED_EVENT, clearTokens, getAccessToken, setTokens } from '../api/client'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [isAuthenticated, setIsAuthenticated] = useState(() => Boolean(getAccessToken()))
  const [worker, setWorker] = useState(null)
  const [workerLoading, setWorkerLoading] = useState(false)

  const refreshWorker = useCallback(async () => {
    if (!getAccessToken()) {
      return
    }
    setWorkerLoading(true)
    try {
      const dashboard = await getWorkerDashboard()
      setWorker(dashboard)
    } catch {
      // Session may have just expired mid-request — the response
      // interceptor already handles clearing tokens in that case.
    } finally {
      setWorkerLoading(false)
    }
  }, [])

  // Load the worker profile once on mount if a token is already stored
  // (page refresh / returning visit).
  useEffect(() => {
    if (isAuthenticated) {
      refreshWorker()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // React to a forced logout triggered from the axios interceptor (refresh
  // token expired, account banned, etc.) from anywhere in the app.
  useEffect(() => {
    const handleExpired = () => {
      setIsAuthenticated(false)
      setWorker(null)
    }
    window.addEventListener(AUTH_EXPIRED_EVENT, handleExpired)
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, handleExpired)
  }, [])

  // ── Step 1 — request OTP ──────────────────────────────────────────────
  const sendOtp = useCallback((phone) => authApi.sendOtp(phone), [])

  // ── Step 2 — verify OTP, receive JWT pair, load profile ───────────────
  // `registration` (fullName, city, latitude, longitude, platform) is only
  // required the first time this phone number logs in — see api/auth.js.
  const verifyOtp = useCallback(async (phone, otp, registration = {}) => {
    const tokens = await authApi.verifyOtp(phone, otp, registration)
    setTokens(tokens)
    setIsAuthenticated(true)
    await refreshWorker()
    return tokens
  }, [refreshWorker])

  const logout = useCallback(() => {
    authApi.logout().catch(() => {
      // Best-effort — token revocation server-side doesn't block local logout.
    })
    clearTokens()
    setIsAuthenticated(false)
    setWorker(null)
  }, [])

  const contextValue = useMemo(
    () => ({
      isAuthenticated,
      worker,
      workerLoading,
      refreshWorker,
      sendOtp,
      verifyOtp,
      logout,
    }),
    [isAuthenticated, worker, workerLoading, refreshWorker, sendOtp, verifyOtp, logout],
  )

  return <AuthContext.Provider value={contextValue}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)

  if (!context) {
    throw new Error('useAuth must be used within AuthProvider')
  }

  return context
}
