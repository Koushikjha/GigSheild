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

const getToneClass = (status) => {
  const normalizedStatus = status.toLowerCase()

  if (normalizedStatus === 'paid') {
    return 'pill pill--paid'
  }

  if (normalizedStatus === 'processing') {
    return 'pill pill--processing'
  }

  if (normalizedStatus === 'under review' || normalizedStatus === 'held') {
    return 'pill pill--held'
  }

  return 'pill pill--neutral'
}

export default function PayoutRow({ payout, compact = false }) {
  const rowClassName = compact ? 'policy-card policy-card--dense' : 'policy-card'

  return (
    <article className={rowClassName}>
      <div className="policy-card__header">
        <div>
          <h3 className="policy-card__title">{payout.eventType}</h3>
          <p className="policy-card__meta">{dateFormatter.format(new Date(`${payout.date}T00:00:00`))} · {payout.zone}</p>
        </div>
        <span className={getToneClass(payout.status)}>{payout.status}</span>
      </div>
      <div className="policy-card__details">
        <div className="policy-card__detail">
          <span>Amount</span>
          <strong>{currencyFormatter.format(Number(payout.amount))}</strong>
        </div>
        <div className="policy-card__detail">
          <span>Event type</span>
          <strong>{payout.eventType}</strong>
        </div>
      </div>
    </article>
  )
}