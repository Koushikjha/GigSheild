// Mirrors com.gigshield.claim.enums.ClaimStatus exactly.
export const CLAIM_STATUS_DISPLAY = {
  PENDING_FRAUD_CHECK: { label: 'Processing', tone: 'processing' },
  AUTO_APPROVED: { label: 'Approved — payout queued', tone: 'processing' },
  FLAGGED_FOR_REVIEW: { label: 'Under review', tone: 'held' },
  ADMIN_APPROVED: { label: 'Approved — payout queued', tone: 'processing' },
  ADMIN_REJECTED: { label: 'Rejected', tone: 'neutral' },
  PAID: { label: 'Paid', tone: 'paid' },
  FAILED: { label: 'Payout failed', tone: 'held' },
}

export function getClaimStatusDisplay(status) {
  return CLAIM_STATUS_DISPLAY[status] || { label: status, tone: 'neutral' }
}

// Mirrors com.gigshield.event.enums.EventType. City-wide types (RAIN, AQI,
// CURFEW, TRAFFIC, WAR) are unchanged; ORDER_CANCELLED is the one worker-
// reported type (see api/claims.js:reportCancelledOrder) and gets a plain
// label instead of showing the raw enum name.
const TRIGGER_EVENT_LABELS = {
  ORDER_CANCELLED: 'Order cancelled',
}

export function getTriggerEventLabel(triggerEvent) {
  return TRIGGER_EVENT_LABELS[triggerEvent] || triggerEvent
}
