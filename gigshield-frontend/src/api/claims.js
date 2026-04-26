import { client } from './client'

export function getClaims() {
  return client.get('/api/v1/claims').then((response) => response.data)
}