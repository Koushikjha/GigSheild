import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import PlanCard from '../components/PlanCard'
import { getTiers } from '../api/policies'
import { useAuth } from '../context/useAuth'
import { useToast } from '../context/useToast'

export default function Plans() {
  const navigate = useNavigate()
  const { worker } = useAuth()
  const { pushToast } = useToast()
  const [tiers, setTiers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const currentTier = worker?.activePolicy?.tier

  useEffect(() => {
    let active = true

    getTiers()
      .then((data) => {
        if (active) {
          setTiers(data)
        }
      })
      .catch(() => {
        if (active) {
          setError('Unable to load plans right now.')
          pushToast('error', 'Plans unavailable', 'Could not reach the GigShield backend.')
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
  }, [pushToast])

  const handleSelect = (plan) => {
    pushToast('info', `${plan.tier} selected`, 'Continue with weekly checkout.')
    navigate('/buy-policy', {
      state: {
        fromPlans: true,
        selectedTier: plan.tier,
      },
    })
  }

  return (
    <div className="page">
      <header className="page__header">
        <p className="page__eyebrow">Plans</p>
        <h1 className="page__title">Choose a weekly plan that matches your risk</h1>
        <p className="page__subtitle">Selecting a plan takes you to checkout and Razorpay payment. No auto-renewal.</p>
      </header>

      {error ? <div className="surface-card muted">{error}</div> : null}

      <section className="grid grid--three">
        {tiers.map((plan) => (
          <PlanCard key={plan.tier} plan={plan} isCurrent={plan.tier === currentTier} onSelect={handleSelect} />
        ))}
        {loading ? <div className="surface-card muted">Loading plans…</div> : null}
      </section>

      <footer className="surface-card">
        <strong>No auto-renewal. You choose each week. Skip anytime.</strong>
        <p className="muted" style={{ marginTop: 8 }}>
          Switch tiers any week — your premium and payout are recalculated from your live risk score.
        </p>
      </footer>
    </div>
  )
}
