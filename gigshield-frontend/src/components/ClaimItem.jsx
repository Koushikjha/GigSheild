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

const steps = ['Detected', 'Verified', 'Paying', 'Done']

const getStatusClass = (status) => {
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

export default function ClaimItem({ claim }) {
  const currentStep = Math.max(1, Math.min(4, Number(claim.currentStep) || 1))

  return (
    <article className="claim-item">
      <div className="claim-item__header">
        <div>
          <h3 className="claim-item__title">{claim.eventType}</h3>
          <p className="claim-item__meta">{dateFormatter.format(new Date(`${claim.date}T00:00:00`))} · {claim.zone}</p>
        </div>
        <span className={getStatusClass(claim.status)}>{claim.status}</span>
      </div>

      <div className="claim-item__details">
        <div className="claim-item__detail">
          <span>Severity</span>
          <strong>{claim.severity}</strong>
        </div>
        <div className="claim-item__detail">
          <span>Amount</span>
          <strong>{currencyFormatter.format(Number(claim.amount))}</strong>
        </div>
      </div>

      {claim.status.toLowerCase() === 'processing' ? (
        <div className="claim-item__tracker">
          <p className="claim-item__meta">Detected → Verified → Paying → Done</p>
          <div className="stepper" aria-label="Claim progress tracker">
            {steps.map((step, index) => {
              const stepNumber = index + 1
              const stepClassName =
                stepNumber < currentStep
                  ? 'stepper__step stepper__step--complete'
                  : stepNumber === currentStep
                    ? 'stepper__step stepper__step--current'
                    : 'stepper__step'

              return (
                <div key={step} className={stepClassName}>
                  <span className="stepper__dot" aria-hidden="true" />
                  <strong>{step}</strong>
                </div>
              )
            })}
          </div>
        </div>
      ) : null}

      {(claim.status.toLowerCase() === 'under review' || claim.status.toLowerCase() === 'held') ? (
        <div className="claim-item__review">Your payout is under review. Usually resolved in 24 hours. No action needed.</div>
      ) : null}

      {claim.details ? <p className="muted">{claim.details}</p> : null}
    </article>
  )
}