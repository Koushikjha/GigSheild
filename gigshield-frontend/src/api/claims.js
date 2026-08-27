import { client } from './client'

// GET /api/v1/claims?page= -> Page<ClaimResponse>
// content[]: { id, policyId, triggerEvent, payoutAmount, fraudScore, status, adminNote, eventOccurredAt, createdAt, processedAt }
export function getClaims(page = 0) {
  return client.get('/api/v1/claims', { params: { page } }).then((response) => response.data)
}

// POST /api/v1/claims/cancelled-order { cancelledAt, note? } -> ClaimResponse
// Worker-initiated only — see EventType.ORDER_CANCELLED on the backend for why
// this is never auto-created from the disruption pipeline. cancelledAt must be
// within the last 72h and not in the future; the backend corroborates
// genuineness (ML trigger-check + risk-score) as of that exact timestamp,
// using the worker's own registered coordinates, before it's ever approved.
export function reportCancelledOrder(cancelledAt, note) {
  return client
    .post('/api/v1/claims/cancelled-order', { cancelledAt, note })
    .then((response) => response.data)
}
