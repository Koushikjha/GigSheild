import { getClaimStatusDisplay, getTriggerEventLabel } from '../constants/claimStatus'

const currencyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 0,
})

const dateTimeFormatter = new Intl.DateTimeFormat('en-GB', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

const toneClass = {
  paid: 'pill pill--paid',
  processing: 'pill pill--processing',
  held: 'pill pill--held',
  neutral: 'pill pill--neutral',
}

// Consumes com.gigshield.claim.dto.ClaimResponse directly:
// { id, policyId, triggerEvent, payoutAmount, fraudScore, status, adminNote, eventOccurredAt, createdAt, processedAt }
export default function ClaimItem({ claim }) {
  const statusDisplay = getClaimStatusDisplay(claim.status)

  return (
    <article className="claim-item">
      <div className="claim-item__header">
        <div>
          <h3 className="claim-item__title">{getTriggerEventLabel(claim.triggerEvent)}</h3>
          <p className="claim-item__meta">
            {claim.eventOccurredAt
              ? `Happened ${dateTimeFormatter.format(new Date(claim.eventOccurredAt))} · reported ${dateTimeFormatter.format(new Date(claim.createdAt))}`
              : dateTimeFormatter.format(new Date(claim.createdAt))}
          </p>
        </div>
        <span className={toneClass[statusDisplay.tone]}>{statusDisplay.label}</span>
      </div>

      <div className="claim-item__details">
        <div className="claim-item__detail">
          <span>Payout amount</span>
          <strong>{currencyFormatter.format(Number(claim.payoutAmount))}</strong>
        </div>
        <div className="claim-item__detail">
          <span>Fraud score</span>
          <strong>{claim.fraudScore ?? '—'}/100</strong>
        </div>
      </div>

      {claim.status === 'FLAGGED_FOR_REVIEW' ? (
        <div className="claim-item__review">Your payout is under review. Usually resolved in 24 hours. No action needed.</div>
      ) : null}

      {claim.status === 'ADMIN_REJECTED' && claim.adminNote ? <p className="muted">{claim.adminNote}</p> : null}
    </article>
  )
}
