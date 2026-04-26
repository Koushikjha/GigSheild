import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'

const workerZones = [
  { id: 'central-market', name: 'Central Market', latitude: 28.6139, longitude: 77.209 },
  { id: 'harbor-bridge', name: 'Harbor Bridge', latitude: 19.0421, longitude: 72.8236 },
  { id: 'metro-ring', name: 'Metro Ring', latitude: 12.9716, longitude: 77.5946 },
  { id: 'south-dock', name: 'South Dock', latitude: 13.0827, longitude: 80.2707 },
]

const initialWorker = {
  name: 'Aarav Sharma',
  zoneId: 'central-market',
  zone: 'Central Market',
  weeklyIncome: 4200,
}

const initialSession = {
  isAuthenticated: false,
  worker: initialWorker,
  currentPlan: 'Standard',
  currentWeekStart: '2026-03-30',
}

const storageKey = 'gigsheild-session'

function toDisplayName(rawValue) {
  if (!rawValue) {
    return ''
  }

  return rawValue
    .split(/[._-]+/)
    .map((part) => part.trim())
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
    .join(' ')
}

function getNameFromEmail(email) {
  if (!email || typeof email !== 'string') {
    return ''
  }

  const localPart = email.split('@')[0]
  return toDisplayName(localPart)
}

function readStoredSession() {
  try {
    const rawSession = window.localStorage.getItem(storageKey)

    if (!rawSession) {
      return initialSession
    }

    const parsedSession = JSON.parse(rawSession)

    return {
      ...initialSession,
      ...parsedSession,
      worker: {
        ...initialWorker,
        ...(parsedSession.worker ?? {}),
      },
    }
  } catch {
    return initialSession
  }
}

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const storedSession = readStoredSession()
  const [isAuthenticated, setIsAuthenticated] = useState(storedSession.isAuthenticated)
  const [worker, setWorker] = useState(storedSession.worker)
  const [currentPlan, setCurrentPlan] = useState(storedSession.currentPlan)
  const [currentWeekStart, setCurrentWeekStart] = useState(storedSession.currentWeekStart)

  const login = useCallback(({ name, email } = {}) => {
    const providedName = name?.trim()
    const emailDerivedName = getNameFromEmail(email)

    setWorker((currentWorker) => ({
      ...currentWorker,
      name: providedName || emailDerivedName || currentWorker.name,
    }))
    setIsAuthenticated(true)
  }, [])

  const register = useCallback(({ name, zoneId, zone, weeklyIncome }) => {
    setWorker((currentWorker) => ({
      ...currentWorker,
      name: name?.trim() || currentWorker.name,
      zoneId: zoneId || currentWorker.zoneId,
      zone: zone || currentWorker.zone,
      weeklyIncome: Number(weeklyIncome) > 0 ? Number(weeklyIncome) : currentWorker.weeklyIncome,
    }))
    setIsAuthenticated(true)
  }, [])

  const logout = useCallback(() => {
    setIsAuthenticated(false)
  }, [])

  const updateZone = useCallback((zoneId) => {
    const zone = workerZones.find((entry) => entry.id === zoneId)

    if (!zone) {
      return
    }

    setWorker((currentWorker) => ({
      ...currentWorker,
      zoneId: zone.id,
      zone: zone.name,
    }))
  }, [])

  useEffect(() => {
    try {
      window.localStorage.setItem(
        storageKey,
        JSON.stringify({
          isAuthenticated,
          worker,
          currentPlan,
          currentWeekStart,
        }),
      )
    } catch {
    }
  }, [isAuthenticated, worker, currentPlan, currentWeekStart])

  const contextValue = useMemo(
    () => ({
      isAuthenticated,
      worker,
      setWorker,
      currentPlan,
      setCurrentPlan,
      currentWeekStart,
      setCurrentWeekStart,
      workerZones,
      updateZone,
      login,
      register,
      logout,
    }),
    [isAuthenticated, worker, currentPlan, currentWeekStart, updateZone, login, register, logout],
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