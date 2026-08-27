import { getTierDisplay } from '../constants/plans'

const currencyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 0,
})

// Consumes com.gigshield.policy.dto.PolicyTierInfoResponse directly:
// { tier, weeklyPremiumInr, payoutRatio, estimatedPayoutInr, description }
export default function PlanCard({ plan, isCurrent, onSelect }) {
  const display = getTierDisplay(plan.tier)

  return (
    <article className={`plan-card ${isCurrent ? 'plan-card--current' : ''}`}>
      <div className="plan-card__header">
        <div>
          <h3 className="plan-card__title">{display.label}</h3>
          <p className="plan-card__meta">{plan.description}</p>
        </div>
        {isCurrent ? <span className="plan-card__badge">Current plan</span> : <span className="pill pill--neutral">Weekly</span>}
      </div>

      <p className="plan-card__price">₹{plan.weeklyPremiumInr}/week</p>

      <ul className="plan-card__features">
        {display.features.map((feature) => (
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
            <span>Estimated payout</span>
            <strong>{currencyFormatter.format(plan.estimatedPayoutInr)}</strong>
          </div>
          <div className="plan-card__detail">
            <span>Payout ratio</span>
            <strong>{Math.round(plan.payoutRatio * 100)}% of weekly income</strong>
          </div>
        </div>
        <button className="button button--primary" type="button" onClick={() => onSelect(plan)}>
          {isCurrent ? 'Continue with this plan' : `Select ${display.label}`}
        </button>
      </div>
    </article>
  )
}
