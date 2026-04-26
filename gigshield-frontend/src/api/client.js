import axios from 'axios'
import dashboardMock from '../mocks/dashboard.json'
import claimsMock from '../mocks/claims.json'
import historyMock from '../mocks/history.json'
import riskScoreMock from '../mocks/riskScore.json'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'https://mock-api.gigsheild.local'

const delay = (data, config, status = 200) =>
  new Promise((resolve) => {
    window.setTimeout(() => {
      resolve({
        data,
        status,
        statusText: 'OK',
        headers: {},
        config,
        request: {},
      })
    }, 260)
  })

const mockState = {
  dashboardIndex: 0,
  activePolicyOverride: null,
}

const clone = (value) => JSON.parse(JSON.stringify(value))

const parseRequestData = (config) => {
  if (typeof config.data !== 'string' || config.data.length === 0) {
    return config.data ?? {}
  }

  try {
    return JSON.parse(config.data)
  } catch {
    return {}
  }
}

const getZoneStatus = (advance = false) => {
  const sequence = dashboardMock.zoneStatusSequence ?? []

  if (sequence.length === 0) {
    return clone(dashboardMock.zoneStatus ?? {})
  }

  const nextIndex = mockState.dashboardIndex % sequence.length
  const zoneStatus = clone(sequence[nextIndex])

  if (advance) {
    mockState.dashboardIndex = (mockState.dashboardIndex + 1) % sequence.length
  }

  return zoneStatus
}

const getRiskScore = (zoneId, weekStart) => {
  const zoneConfig = riskScoreMock.zones[zoneId] ?? riskScoreMock.zones.default
  const weekConfig = riskScoreMock.weeks[weekStart] ?? riskScoreMock.weeks.default
  const rawScore = Math.round(zoneConfig.baseScore * weekConfig.multiplier + weekConfig.shift)
  const riskScore = Math.max(5, Math.min(95, rawScore))
  const premium = Math.max(
    15,
    Math.min(30, Math.round(zoneConfig.basePremium + weekConfig.premiumShift + riskScore * 0.08)),
  )

  return {
    policyId: `${zoneId}-${weekStart}`,
    zoneId,
    zoneName: zoneConfig.name,
    weekStart,
    weekLabel: weekConfig.label,
    riskScore,
    riskLevel: riskScore < 35 ? 'low' : riskScore < 65 ? 'medium' : 'high',
    premium,
    coveragePercent: 0.35,
    coveredEvents: zoneConfig.coveredEvents,
    forecast: weekConfig.forecast,
  }
}

const toDateString = (value) => {
  if (!value) {
    return ''
  }

  const date = new Date(`${value}T00:00:00`)

  if (Number.isNaN(date.getTime())) {
    return ''
  }

  return date.toISOString().slice(0, 10)
}

const getWeekEndDate = (weekStart, fallbackDate) => {
  const startDate = new Date(`${weekStart}T00:00:00`)

  if (Number.isNaN(startDate.getTime())) {
    return fallbackDate
  }

  const endDate = new Date(startDate)
  endDate.setDate(startDate.getDate() + 6)

  return endDate.toISOString().slice(0, 10)
}

const mockAdapter = async (config) => {
  const requestUrl = new URL(config.url, config.baseURL || API_BASE_URL)
  const path = requestUrl.pathname
  const method = (config.method || 'get').toLowerCase()
  const params = config.params || Object.fromEntries(requestUrl.searchParams.entries())

  if (method === 'get' && path === '/api/v1/dashboard') {
    const baseDashboard = clone(dashboardMock)
    const resolvedPolicy = mockState.activePolicyOverride ? clone(mockState.activePolicyOverride) : baseDashboard.activePolicy

    return delay(
      {
        ...baseDashboard,
        activePolicy: resolvedPolicy,
        zoneStatus: getZoneStatus(false),
      },
      config,
    )
  }

  if (method === 'get' && path === '/api/v1/dashboard/zone-status') {
    return delay(getZoneStatus(true), config)
  }

  if (method === 'get' && path === '/api/v1/claims') {
    return delay(clone(claimsMock), config)
  }

  if (method === 'get' && path === '/api/v1/policies/history') {
    return delay(clone(historyMock), config)
  }

  if (method === 'get' && path === '/api/v1/risk/score') {
    return delay(getRiskScore(params.zoneId || riskScoreMock.defaultZoneId, params.weekStart || riskScoreMock.defaultWeekStart), config)
  }

  if (method === 'post' && path.startsWith('/api/v1/payments/order/')) {
    const policyId = path.split('/').pop()
    const body = parseRequestData(config)
    const premium = Number(body.premium ?? 24)
    const basePolicy = dashboardMock.activePolicy ?? {}
    const startDate = toDateString(body.weekStart) || basePolicy.startDate
    const coverageAmount = Number(body.coverageAmount ?? basePolicy.coverageAmount)

    mockState.activePolicyOverride = {
      ...basePolicy,
      policyId,
      planName: body.planName || basePolicy.planName,
      zone: body.zoneName || basePolicy.zone,
      coverageAmount: Number.isFinite(coverageAmount) ? coverageAmount : basePolicy.coverageAmount,
      premiumPaid: Math.round(premium),
      status: 'active',
      startDate,
      endDate: getWeekEndDate(startDate, basePolicy.endDate),
    }

    return delay(
      {
        policyId,
        orderId: `order_${policyId}`,
        amount: Math.round(premium * 100),
        currency: 'INR',
        key: 'rzp_test_mock_key',
        name: 'GigShield',
        description: body.zoneName ? `Weekly cover for ${body.zoneName}` : 'Weekly cover for your zone',
        prefill: {
          name: 'GigShield Worker',
          email: 'worker@gigsheild.local',
          contact: '9999999999',
        },
      },
      config,
    )
  }

  return delay({ message: 'Unknown mock endpoint', path, method }, config, 404)
}

export const client = axios.create({
  baseURL: API_BASE_URL,
  adapter: mockAdapter,
})