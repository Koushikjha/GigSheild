import { client } from './client'

// GET /api/v1/policies/tiers -> PolicyTierInfoResponse[]
// [{ tier: 'STANDARD'|'GOLD'|'PREMIUM', weeklyPremiumInr, payoutRatio, estimatedPayoutInr, description }]
export function getTiers() {
  return client.get('/api/v1/policies/tiers').then((response) => response.data)
}

// POST /api/v1/policies -> PolicyResponse (status PENDING_PAYMENT until Razorpay webhook activates it)
export function purchasePolicy(tier, weeklyIncomeEstimate) {
  return client
    .post('/api/v1/policies', { tier, weeklyIncomeEstimate })
    .then((response) => response.data)
}

// GET /api/v1/policies/active -> PolicyResponse (404/500 if none — callers should catch)
export function getActivePolicy() {
  return client.get('/api/v1/policies/active').then((response) => response.data)
}

// GET /api/v1/policies/history?page= -> Page<PolicyResponse>
export function getPolicyHistory(page = 0) {
  return client.get('/api/v1/policies/history', { params: { page } }).then((response) => response.data)
}

// GET /api/v1/risk/score -> RiskScoreResponse for the authenticated worker
// { riskScore, recommendedPremium, riskBand }
export function getRiskScore() {
  return client.get('/api/v1/risk/score').then((response) => response.data)
}
