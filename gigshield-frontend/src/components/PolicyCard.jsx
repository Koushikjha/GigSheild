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
  if (status === 'active') {
    return 'pill pill--active'
  }

  if (status === 'expired') {
    return 'pill pill--expired'
  }

  return 'pill pill--neutral'
}

export default function PolicyCard({ policy, dense = false }) {
  const cardClassName = dense ? 'policy-card policy-card--dense' : 'policy-card'

  return (
    <article className={cardClassName}>
      <div className="policy-card__header">
        <div>
          <h3 className="policy-card__title">{policy.planName} · {policy.zone}</h3>
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
          <span>Coverage amount</span>
          <strong>{currencyFormatter.format(Number(policy.coverageAmount))}</strong>
        </div>
        <div className="policy-card__detail">
          <span>Zone</span>
          <strong>{policy.zone}</strong>
        </div>
      </div>
    </article>
  )
}