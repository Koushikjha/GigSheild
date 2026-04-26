import { useEffect, useState } from 'react'
import { getRiskScore } from '../api/policies'

const currencyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 0,
})

export default function PremiumCalc({ zoneId, weekStart, weeklyIncome, weekLabel, onQuoteChange, fixedPremium }) {
  const [quote, setQuote] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const displayedPremium = Number.isFinite(fixedPremium) ? fixedPremium : quote?.premium

  useEffect(() => {
    let active = true

    const loadQuote = async () => {
      setLoading(true)
      setError('')

      try {
        const response = await getRiskScore(zoneId, weekStart)
        const computedQuote = {
          ...response,
          coverageAmount: Math.round(weeklyIncome * response.coveragePercent),
          weeklyIncome,
        }

        if (!active) {
          return
        }

        setQuote(computedQuote)
        onQuoteChange?.(computedQuote)
      } catch {
        if (!active) {
          return
        }

        setQuote(null)
        setError('Premium data is temporarily unavailable.')
        onQuoteChange?.(null)
      } finally {
        if (active) {
          setLoading(false)
        }
      }
    }

    loadQuote()

    return () => {
      active = false
    }
  }, [zoneId, weekStart, weeklyIncome, onQuoteChange])

  return (
    <section className="premium-calc">
      <div className="premium-calc__header">
        <div>
          <p className="section__eyebrow">Live premium calculator</p>
          <h2 className="section__title">Premium updates as live zone or week changes</h2>
        </div>
        {quote ? <span className="pill pill--teal">Risk {quote.riskLevel}</span> : <span className="pill pill--neutral">Live quote</span>}
      </div>

      <div className="premium-calc__price">
        {loading ? (
          <div className="premium-calc__amount premium-calc__amount--loading">.</div>
        ) : (
          <p className="premium-calc__amount">{Number.isFinite(displayedPremium) ? currencyFormatter.format(displayedPremium) : '—'}</p>
        )}
        <p className="premium-calc__meta">
          {quote ? `${quote.zoneName} · ${weekLabel || quote.weekLabel}` : 'Waiting for premium quote'}
        </p>
      </div>

      {quote ? (
        <div className="premium-calc__breakdown">
          <strong>
            You earn ~{currencyFormatter.format(weeklyIncome)} / week → covered for {currencyFormatter.format(quote.coverageAmount)} → pay {Number.isFinite(displayedPremium) ? currencyFormatter.format(displayedPremium) : '—'}
          </strong>
          <p className="muted">Risk score: {quote.riskScore} · Forecast: {quote.forecast}</p>
        </div>
      ) : (
        <div className="premium-calc__breakdown">
          <strong>Waiting for a live quote.</strong>
          <p className="muted">The calculator updates automatically from live zone and selected week.</p>
        </div>
      )}

      {quote ? (
        <div className="premium-calc__chips">
          {quote.coveredEvents.map((event) => (
            <span className="pill pill--neutral" key={event}>
              {event}
            </span>
          ))}
        </div>
      ) : null}

      {error ? <p className="muted">{error}</p> : null}
    </section>
  )
}