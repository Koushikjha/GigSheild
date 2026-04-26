import { client } from './client'

export function getHistory() {
  return client.get('/api/v1/policies/history').then((response) => response.data)
}

export function buyPolicy(policyId, payload) {
  return client.post(`/api/v1/payments/order/${policyId}`, payload).then((response) => response.data)
}

export function getRiskScore(zoneId, weekStart) {
  return client
    .get('/api/v1/risk/score', {
      params: {
        zoneId,
        weekStart,
      },
    })
    .then((response) => response.data)
}