import axios from 'axios'

// Real GigShield backend (Spring Boot). Previously this file shipped a fake
// axios adapter that intercepted every request and served hand-written mock
// JSON — nothing in the app ever actually reached the backend. That adapter
// has been removed; this is now a plain axios client wired to the real API.
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

const ACCESS_TOKEN_KEY = 'gigshield_access_token'
const REFRESH_TOKEN_KEY = 'gigshield_refresh_token'

export function getAccessToken() {
  try {
    return window.localStorage.getItem(ACCESS_TOKEN_KEY)
  } catch {
    return null
  }
}

export function getRefreshToken() {
  try {
    return window.localStorage.getItem(REFRESH_TOKEN_KEY)
  } catch {
    return null
  }
}

export function setTokens({ accessToken, refreshToken }) {
  try {
    if (accessToken) {
      window.localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
    }
    if (refreshToken) {
      window.localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
    }
  } catch {
    // localStorage unavailable (private browsing etc.) — session just won't persist across reloads.
  }
}

export function clearTokens() {
  try {
    window.localStorage.removeItem(ACCESS_TOKEN_KEY)
    window.localStorage.removeItem(REFRESH_TOKEN_KEY)
  } catch {
    // no-op
  }
}

// Dispatched whenever the session is forcibly ended (refresh failed, 403
// banned account, etc.) so AuthContext can react without every call site
// having to know about it.
export const AUTH_EXPIRED_EVENT = 'gigshield:auth-expired'

export const client = axios.create({
  baseURL: API_BASE_URL,
})

client.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let refreshPromise = null

function endSession() {
  clearTokens()
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT))
  }
}

async function refreshAccessToken() {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    throw new Error('No refresh token available')
  }

  // axios directly (not `client`) — avoids recursing through this same
  // response interceptor while the refresh call itself is in flight.
  const response = await axios.post(`${API_BASE_URL}/api/v1/auth/refresh`, { refreshToken })
  setTokens(response.data)
  return response.data.accessToken
}

client.interceptors.response.use(
  (response) => response,
  async (error) => {
    const { config, response } = error

    if (!response || response.status !== 401 || config?._retried) {
      if (response?.status === 403) {
        // Banned account — JwtFilter returns 403 with no path to recover.
        endSession()
      }
      return Promise.reject(error)
    }

    config._retried = true

    try {
      refreshPromise = refreshPromise || refreshAccessToken()
      const newAccessToken = await refreshPromise
      refreshPromise = null

      config.headers = config.headers || {}
      config.headers.Authorization = `Bearer ${newAccessToken}`
      return client(config)
    } catch (refreshError) {
      refreshPromise = null
      endSession()
      return Promise.reject(refreshError)
    }
  },
)
