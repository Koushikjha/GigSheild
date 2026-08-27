import { getClaimStatusDisplay } from '../constants/claimStatus'

const currencyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 0,
})

const dateFormatter = new Intl.DateTimeFormat('en-GB', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
})

const toneClass = {
  paid: 'pill pill--paid',
  processing: 'pill pill--processing',
  held: 'pill pill--held',
  neutral: 'pill pill--neutral',
}

// A payout, in this system, is just an approved/paid claim — there is no
// separate worker-facing payout ledger endpoint on the backend, so History
// renders paid claims (com.gigshield.claim.dto.ClaimResponse) through this row.
export default function PayoutRow({ payout, compact = false }) {
  const rowClassName = compact ? 'policy-card policy-card--dense' : 'policy-card'
  const statusDisplay = getClaimStatusDisplay(payout.status)

  return (
    <article className={rowClassName}>
      <div className="policy-card__header">
        <div>
          <h3 className="policy-card__title">{payout.triggerEvent}</h3>
          <p className="policy-card__meta">{dateFormatter.format(new Date(payout.createdAt))}</p>
        </div>
        <span className={toneClass[statusDisplay.tone]}>{statusDisplay.label}</span>
      </div>
      <div className="policy-card__details">
        <div className="policy-card__detail">
          <span>Amount</span>
          <strong>{currencyFormatter.format(Number(payout.payoutAmount))}</strong>
        </div>
        <div className="policy-card__detail">
          <span>Event type</span>
          <strong>{payout.triggerEvent}</strong>
        </div>
      </div>
    </article>
  )
}
