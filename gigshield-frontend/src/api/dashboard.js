import { client } from './client'

// GET /api/v1/dashboard/worker -> WorkerDashboardResponse
// { userId, fullName, city, accountStatus, fraudStrikes, riskBand,
//   recommendedPremium, activePolicy, recentClaimsCount,
//   totalPayoutReceived, pendingClaimsCount }
export function getWorkerDashboard() {
  return client.get('/api/v1/dashboard/worker').then((response) => response.data)
}
