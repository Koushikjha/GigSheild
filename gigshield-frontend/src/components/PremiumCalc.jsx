import { useEffect, useState } from 'react'
import { getRiskScore } from '../api/policies'

const currencyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 0,
})

// The backend prices policies from the live risk score of the worker's
// registered city (PolicyService.resolvePremium, fed by the same ML signal
// GET /api/v1/risk/score surfaces here) — tierPremium already has that
// adjustment baked in, scaled up/down from each tier's flat base price.
export default function PremiumCalc({ tierPremium, tierPayout, tierLabel }) {
  const [risk, setRisk] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true

    getRiskScore()
      .then((data) => {
        if (active) {
          setRisk(data)
        }
      })
      .catch(() => {
        if (active) {
          setError('Live risk data is temporarily unavailable.')
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false)
        }
      })

    return () => {
      active = false
    }
  }, [])

  return (
    <section className="premium-calc">
      <div className="premium-calc__header">
        <div>
          <p className="section__eyebrow">Live risk snapshot</p>
          <h2 className="section__title">Your current zone risk, from live weather + AQI</h2>
        </div>
        {risk ? <span className="pill pill--teal">Risk {risk.riskBand}</span> : <span className="pill pill--neutral">Live quote</span>}
      </div>

      <div className="premium-calc__price">
        {loading ? (
          <div className="premium-calc__amount premium-calc__amount--loading">.</div>
        ) : (
          <p className="premium-calc__amount">{currencyFormatter.format(tierPremium)}</p>
        )}
        <p className="premium-calc__meta">{tierLabel} plan · risk-adjusted weekly premium</p>
      </div>

      <div className="premium-calc__breakdown">
        <strong>
          Pay {currencyFormatter.format(tierPremium)}/week → covered up to {currencyFormatter.format(tierPayout)}
        </strong>
        {risk ? (
          <p className="muted">
            Priced from your city's live risk score: {risk.riskScore}/100 ({risk.riskBand}). Higher risk raises the
            premium, lower risk brings it down from the {tierLabel} base rate.
          </p>
        ) : null}
      </div>

      {error ? <p className="muted">{error}</p> : null}
    </section>
  )
}
