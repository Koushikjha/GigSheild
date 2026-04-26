import { client } from './client'

export function getDashboardData() {
  return client.get('/api/v1/dashboard').then((response) => response.data)
}

export function getZoneStatus() {
  return client.get('/api/v1/dashboard/zone-status').then((response) => response.data)
}