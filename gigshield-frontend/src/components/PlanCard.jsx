const currencyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 0,
})

export default function PlanCard({ plan, isCurrent, onSelect }) {
  return (
    <article className={`plan-card ${isCurrent ? 'plan-card--current' : ''}`}>
      <div className="plan-card__header">
        <div>
          <h3 className="plan-card__title">{plan.name}</h3>
          <p className="plan-card__meta">{plan.description}</p>
        </div>
        {isCurrent ? <span className="plan-card__badge">Current plan</span> : <span className="pill pill--neutral">Weekly</span>}
      </div>

      <p className="plan-card__price">{plan.priceRange}</p>

      <ul className="plan-card__features">
        {plan.features.map((feature) => (
          <li key={feature}>
            <span className="plan-card__check" aria-hidden="true">
              ✓
            </span>
            <span>{feature}</span>
          </li>
        ))}
      </ul>

      <div className="plan-card__footer">
        <div className="plan-card__details">
          <div className="plan-card__detail">
            <span>Coverage</span>
            <strong>{currencyFormatter.format(plan.coverage)}</strong>
          </div>
          <div className="plan-card__detail">
            <span>Best for</span>
            <strong>{plan.bestFor}</strong>
          </div>
        </div>
        <button className="button button--primary" type="button" onClick={() => onSelect(plan)}>
          {isCurrent ? 'Continue with this plan' : plan.ctaLabel}
        </button>
      </div>
    </article>
  )
}