import { useEffect, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { getTiers, purchasePolicy } from '../api/policies'
import { createOrder } from '../api/payments'
import PremiumCalc from '../components/PremiumCalc'
import { getTierDisplay } from '../constants/plans'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'

const RAZORPAY_KEY_ID = import.meta.env.VITE_RAZORPAY_KEY_ID || ''

const loadRazorpayScript = () =>
  new Promise((resolve, reject) => {
    if (typeof window !== 'undefined' && typeof window.Razorpay === 'function') {
      resolve(true)
      return
    }

    const existingScript = document.querySelector('script[data-gigshield-razorpay]')

    if (existingScript) {
      existingScript.addEventListener('load', () => resolve(true), { once: true })
      existingScript.addEventListener('error', () => reject(new Error('Razorpay script failed to load')), { once: true })
      return
    }

    const script = document.createElement('script')
    script.src = 'https://checkout.razorpay.com/v1/checkout.js'
    script.async = true
    script.dataset.gigshieldRazorpay = 'true'
    script.onload = () => resolve(true)
    script.onerror = () => reject(new Error('Razorpay script failed to load'))
    document.body.appendChild(script)
  })

const currencyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 0,
})

export default function BuyPolicy() {
  const location = useLocation()
  const navigate = useNavigate()
  const { worker, refreshWorker } = useAuth()
  const { pushToast } = useToast()
  const [tiers, setTiers] = useState([])
  const [loadingTiers, setLoadingTiers] = useState(true)
  const [processing, setProcessing] = useState(false)
  const fromPlans = Boolean(location.state?.fromPlans)
  const selectedTier = location.state?.selectedTier

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
          pushToast('error', 'Could not load plan pricing', 'Please go back and try again.')
        }
      })
      .finally(() => {
        if (active) {
          setLoadingTiers(false)
        }
      })

    return () => {
      active = false
    }
  }, [pushToast])

  if (!fromPlans || !selectedTier) {
    return <Navigate to="/plans" replace />
  }

  const plan = tiers.find((tier) => tier.tier === selectedTier)
  const tierDisplay = getTierDisplay(selectedTier)

  const openCheckout = async (order, policyId) => {
    if (!RAZORPAY_KEY_ID) {
      // No public key configured — simulate success so the demo flow still
      // completes end to end (the real activation always happens through
      // the Razorpay webhook -> PaymentService.handleWebhook, never here).
      return { paymentId: `pay_test_${policyId}` }
    }

    const scriptReady = await loadRazorpayScript()
    if (!scriptReady || typeof window.Razorpay !== 'function') {
      throw new Error('Razorpay checkout could not be loaded')
    }

    return new Promise((resolve, reject) => {
      const checkout = new window.Razorpay({
        key: RAZORPAY_KEY_ID,
        amount: order.amountInr * 100,
        currency: order.currency,
        name: 'GigShield',
        description: `${tierDisplay.label} weekly cover`,
        order_id: order.razorpayOrderId,
        prefill: { name: worker?.fullName || '' },
        theme: { color: '#1D9E75' },
        modal: { ondismiss: () => reject(new Error('Checkout dismissed')) },
        handler: (response) => resolve(response),
      })
      checkout.open()
    })
  }

  const handlePay = async () => {
    if (!plan) {
      pushToast('warning', 'Plan not ready', 'Wait for pricing to load before paying.')
      return
    }

    setProcessing(true)
    try {
      const policy = await purchasePolicy(selectedTier)
      const order = await createOrder(policy.id)
      const paymentResult = await openCheckout(order, policy.id)

      pushToast(
        'success',
        'Payment submitted',
        `${tierDisplay.label} cover is activating. Reference ${paymentResult.paymentId || paymentResult.razorpay_payment_id || order.razorpayOrderId}.`,
      )
      await refreshWorker()
      navigate('/', { replace: true })
    } catch (error) {
      const dismissed = error.message === 'Checkout dismissed'
      const backendMessage = error.response?.data?.message

      pushToast(
        dismissed ? 'warning' : 'error',
        dismissed ? 'Checkout closed' : 'Payment failed',
        dismissed ? 'No payment was captured. You can try again.' : backendMessage || 'The order could not be completed. Please retry.',
      )
    } finally {
      setProcessing(false)
    }
  }

  return (
    <div className="page">
      <header className="page__header">
        <p className="page__eyebrow">Buy policy</p>
        <h1 className="page__title">Review your plan, see the live risk snapshot, and pay once</h1>
        <p className="page__subtitle">
          GigShield prices weekly cover from your city's live risk score and monitors it for RAIN/AQI triggers automatically. No auto-renewal.
        </p>
      </header>

      <div className="grid grid--two">
        <div className="stack">
          <section className="surface-card stack">
            <div className="section-header">
              <div>
                <p className="section__eyebrow">Selected plan</p>
                <h2 className="section__title">{tierDisplay.label} — {tierDisplay.badge}</h2>
              </div>
              <span className="pill pill--teal">{worker?.city || 'Your city'}</span>
            </div>

            <p className="muted">{plan?.description}</p>

            <p className="muted">
              Registered worker: <strong>{worker?.fullName}</strong> · City: <strong>{worker?.city}</strong>
            </p>
          </section>

          {plan ? (
            <PremiumCalc tierPremium={plan.weeklyPremiumInr} tierPayout={plan.estimatedPayoutInr} tierLabel={tierDisplay.label} />
          ) : (
            <div className="surface-card muted">{loadingTiers ? 'Loading plan pricing…' : 'Plan not found.'}</div>
          )}
        </div>

        <aside className="surface-card stack">
          <div className="section-header">
            <div>
              <p className="section__eyebrow">Payment summary</p>
              <h2 className="section__title">Review the weekly cover</h2>
            </div>
            <span className="pill pill--neutral">Razorpay</span>
          </div>

          <div className="policy-card__details">
            <div className="policy-card__detail">
              <span>Plan</span>
              <strong>{tierDisplay.label}</strong>
            </div>
            <div className="policy-card__detail">
              <span>City</span>
              <strong>{worker?.city || '—'}</strong>
            </div>
            <div className="policy-card__detail">
              <span>Premium</span>
              <strong>{plan ? currencyFormatter.format(plan.weeklyPremiumInr) : '—'}</strong>
            </div>
            <div className="policy-card__detail">
              <span>Max payout</span>
              <strong>{plan ? currencyFormatter.format(plan.estimatedPayoutInr) : '—'}</strong>
            </div>
          </div>

          <button className="button button--primary" type="button" onClick={handlePay} disabled={processing || !plan}>
            {processing ? 'Processing…' : plan ? `Pay ${currencyFormatter.format(plan.weeklyPremiumInr)} with Razorpay` : 'Loading…'}
          </button>

          <div className="surface-card" style={{ padding: 16, background: 'rgba(29, 158, 117, 0.06)' }}>
            <strong>No auto-renewal.</strong>
            <p className="muted" style={{ marginTop: 8 }}>
              You choose each week. Skip anytime.
            </p>
          </div>
        </aside>
      </div>
    </div>
  )
}
