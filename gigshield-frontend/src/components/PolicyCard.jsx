import { getTierDisplay } from '../constants/plans'

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

const getStatusClass = (status) => {
  if (status === 'ACTIVE') {
    return 'pill pill--active'
  }

  if (status === 'EXPIRED' || status === 'CANCELLED') {
    return 'pill pill--expired'
  }

  return 'pill pill--neutral'
}

// Consumes com.gigshield.policy.dto.PolicyResponse directly:
// { id, tier, premiumPaid, maxPayoutAmount, startDate, endDate, status, razorpayOrderId, createdAt }
export default function PolicyCard({ policy, dense = false }) {
  const cardClassName = dense ? 'policy-card policy-card--dense' : 'policy-card'
  const tierDisplay = getTierDisplay(policy.tier)

  return (
    <article className={cardClassName}>
      <div className="policy-card__header">
        <div>
          <h3 className="policy-card__title">{tierDisplay.label} plan</h3>
          <p className="policy-card__meta">
            {dateFormatter.format(new Date(`${policy.startDate}T00:00:00`))} → {dateFormatter.format(new Date(`${policy.endDate}T00:00:00`))}
          </p>
        </div>
        <span className={getStatusClass(policy.status)}>{policy.status}</span>
      </div>

      <div className="policy-card__details">
        <div className="policy-card__detail">
          <span>Premium paid</span>
          <strong>{currencyFormatter.format(Number(policy.premiumPaid))}</strong>
        </div>
        <div className="policy-card__detail">
          <span>Max payout</span>
          <strong>{currencyFormatter.format(Number(policy.maxPayoutAmount))}</strong>
        </div>
        <div className="policy-card__detail">
          <span>Tier</span>
          <strong>{tierDisplay.label}</strong>
        </div>
      </div>
    </article>
  )
}
