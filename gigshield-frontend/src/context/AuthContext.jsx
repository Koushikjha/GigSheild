import { useCallback, useEffect, useMemo, useState } from 'react'
import * as authApi from '../api/auth'
import { getWorkerDashboard } from '../api/dashboard'
import { AUTH_EXPIRED_EVENT, clearTokens, getAccessToken, setTokens } from '../api/client'
import AuthContext from './authContext'

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
      // Session may have expired during the request.
    } finally {
      setWorkerLoading(false)
    }
  }, [])

  useEffect(() => {
    if (isAuthenticated) {
      refreshWorker()
    }
  }, [isAuthenticated, refreshWorker])

  useEffect(() => {
    const handleExpired = () => {
      setIsAuthenticated(false)
      setWorker(null)
    }

    window.addEventListener(AUTH_EXPIRED_EVENT, handleExpired)

    return () => {
      window.removeEventListener(AUTH_EXPIRED_EVENT, handleExpired)
    }
  }, [])

  const sendOtp = useCallback((phone) => authApi.sendOtp(phone), [])

  const verifyOtp = useCallback(
    async (phone, otp, registration = {}) => {
      const tokens = await authApi.verifyOtp(phone, otp, registration)

      setTokens(tokens)
      setIsAuthenticated(true)

      await refreshWorker()

      return tokens
    },
    [refreshWorker],
  )

  const logout = useCallback(() => {
    authApi.logout().catch(() => {})
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
