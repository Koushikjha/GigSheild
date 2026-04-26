import { useCallback, useEffect, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { buyPolicy } from '../api/policies'
import PremiumCalc from '../components/PremiumCalc'
import { findPlanByName } from '../constants/plans'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'

const weekOptions = [
  { label: 'Current week', weekStart: '2026-03-30' },
  { label: 'Next week', weekStart: '2026-04-06' },
]

const toRadians = (value) => (value * Math.PI) / 180

const getDistanceInKm = (originLatitude, originLongitude, targetLatitude, targetLongitude) => {
  const earthRadiusInKm = 6371
  const latitudeDelta = toRadians(targetLatitude - originLatitude)
  const longitudeDelta = toRadians(targetLongitude - originLongitude)
  const originLatitudeInRadians = toRadians(originLatitude)
  const targetLatitudeInRadians = toRadians(targetLatitude)

  const haversineValue =
    Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2) +
    Math.cos(originLatitudeInRadians) * Math.cos(targetLatitudeInRadians) * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2)

  const centralAngle = 2 * Math.atan2(Math.sqrt(haversineValue), Math.sqrt(1 - haversineValue))

  return earthRadiusInKm * centralAngle
}

const getNearestZoneId = (zones, latitude, longitude) => {
  const zonesWithCoordinates = zones.filter(
    (zone) => Number.isFinite(zone.latitude) && Number.isFinite(zone.longitude),
  )

  if (zonesWithCoordinates.length === 0) {
    return null
  }

  const nearestZone = zonesWithCoordinates.reduce(
    (nearest, zone) => {
      const distance = getDistanceInKm(latitude, longitude, zone.latitude, zone.longitude)

      if (distance < nearest.distance) {
        return { id: zone.id, distance }
      }

      return nearest
    },
    { id: zonesWithCoordinates[0].id, distance: Number.POSITIVE_INFINITY },
  )

  return nearestZone.id
}

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
  const { worker, currentPlan, workerZones, updateZone, setCurrentWeekStart, currentWeekStart } = useAuth()
  const { pushToast } = useToast()
  const [selectedWeekStart, setSelectedWeekStart] = useState(currentWeekStart)
  const [quote, setQuote] = useState(null)
  const [loadingPayment, setLoadingPayment] = useState(false)
  const [locating, setLocating] = useState(false)
  const [locationMessage, setLocationMessage] = useState('Detecting your live location for zone pricing.')
  const fromPlans = Boolean(location.state?.fromPlans)
  const selectedPlan = findPlanByName(location.state?.selectedPlanName || currentPlan)

  const selectedZone = workerZones.find((zone) => zone.id === worker.zoneId) || workerZones[0]
  const selectedWeek = weekOptions.find((week) => week.weekStart === selectedWeekStart) || weekOptions[0]

  if (!fromPlans) {
    return <Navigate to="/plans" replace />
  }

  const syncZoneFromLiveLocation = useCallback(() => {
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      setLocationMessage('Live location is not available in this browser. Using your last known zone.')
      return
    }

    setLocating(true)

    navigator.geolocation.getCurrentPosition(
      (position) => {
        const nearestZoneId = getNearestZoneId(workerZones, position.coords.latitude, position.coords.longitude)

        if (!nearestZoneId) {
          setLocationMessage('Unable to map your location to a zone. Using your last known zone.')
          setLocating(false)
          return
        }

        const nearestZone = workerZones.find((zone) => zone.id === nearestZoneId)

        if (!nearestZone) {
          setLocationMessage('Unable to map your location to a zone. Using your last known zone.')
          setLocating(false)
          return
        }

        if (nearestZone.id !== worker.zoneId) {
          updateZone(nearestZone.id)
        }

        setLocationMessage(`Live location mapped to ${nearestZone.name}.`)
        setLocating(false)
      },
      () => {
        setLocationMessage('Location permission denied or unavailable. Using your last known zone.')
        setLocating(false)
      },
      {
        enableHighAccuracy: true,
        timeout: 10000,
        maximumAge: 120000,
      },
    )
  }, [updateZone, worker.zoneId, workerZones])

  useEffect(() => {
    syncZoneFromLiveLocation()
  }, [syncZoneFromLiveLocation])

  const openCheckout = async (order) => {
    try {
      const scriptReady = await loadRazorpayScript()

      if (!scriptReady || typeof window.Razorpay !== 'function') {
        return new Promise((resolve) => {
          window.setTimeout(() => {
            resolve({ paymentId: `pay_mock_${Date.now()}` })
          }, 800)
        })
      }

      return new Promise((resolve, reject) => {
        const checkout = new window.Razorpay({
          key: order.key,
          amount: order.amount,
          currency: order.currency,
          name: order.name,
          description: order.description,
          order_id: order.orderId,
          prefill: order.prefill,
          theme: {
            color: '#1D9E75',
          },
          modal: {
            ondismiss: () => reject(new Error('Checkout dismissed')),
          },
          handler: (response) => resolve(response),
        })

        checkout.open()
      })
    } catch (error) {
      throw error
    }
  }

  const handlePay = async () => {
    if (!quote) {
      pushToast('warning', 'Premium not ready', 'Wait for the live quote to load before paying.')
      return
    }

    setLoadingPayment(true)

    try {
      const order = await buyPolicy(quote.policyId, {
        zoneId: worker.zoneId,
        zoneName: selectedZone.name,
        weekStart: selectedWeekStart,
        planName: selectedPlan.name,
        premium: selectedPlan.weeklyPrice,
        coverageAmount: selectedPlan.coverage,
      })

      const paymentResult = await openCheckout(order)

      pushToast(
        'success',
        'Payment successful',
        `${selectedPlan.name} cover for ${selectedZone.name} is ready. Reference ${paymentResult.paymentId || paymentResult.razorpay_payment_id || order.orderId}.`,
      )
      setCurrentWeekStart(selectedWeekStart)
      navigate('/', { replace: true })
    } catch (error) {
      const dismissed = error.message === 'Checkout dismissed'

      pushToast(
        dismissed ? 'warning' : 'error',
        dismissed ? 'Checkout closed' : 'Payment failed',
        dismissed ? 'No payment was captured. You can try again.' : 'The order could not be completed. Please retry.',
      )
    } finally {
      setLoadingPayment(false)
    }
  }

  return (
    <div className="page">
      <header className="page__header">
        <p className="page__eyebrow">Buy policy</p>
        <h1 className="page__title">Choose your week, see the live premium, and pay once</h1>
        <p className="page__subtitle">
          GigShield uses live location to detect your active zone, then prices weekly cover from live risk and forecast pressure. No auto-renewal.
        </p>
      </header>

      <div className="grid grid--two">
        <div className="stack">
          <section className="surface-card stack">
            <div className="section-header">
              <div>
                <p className="section__eyebrow">Week selector</p>
                <h2 className="section__title">Toggle current week or next week</h2>
              </div>
              <span className="pill pill--teal">{selectedPlan.name} plan</span>
            </div>

            <div className="tab-card" role="tablist" aria-label="Week selector">
              {weekOptions.map((week) => (
                <button
                  key={week.weekStart}
                  type="button"
                  className={`tab-button ${selectedWeekStart === week.weekStart ? 'tab-button--active' : ''}`}
                  onClick={() => {
                    setSelectedWeekStart(week.weekStart)
                    setCurrentWeekStart(week.weekStart)
                  }}
                >
                  {week.label}
                </button>
              ))}
            </div>

            <div className="policy-card__detail">
              <span>Live detected zone</span>
              <strong>{selectedZone.name}</strong>
            </div>

            <button className="button button--secondary" type="button" onClick={syncZoneFromLiveLocation} disabled={locating}>
              {locating ? 'Detecting live location…' : 'Refresh live location'}
            </button>

            <p className="muted">{locationMessage}</p>

            <p className="muted">
              Registered worker: <strong>{worker.name}</strong> · Zone: <strong>{selectedZone.name}</strong> · Week: <strong>{selectedWeek.label}</strong>
            </p>
          </section>

          <PremiumCalc
            zoneId={worker.zoneId}
            weekStart={selectedWeekStart}
            weeklyIncome={worker.weeklyIncome}
            weekLabel={selectedWeek.label}
            fixedPremium={selectedPlan.weeklyPrice}
            onQuoteChange={setQuote}
          />
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
              <strong>{selectedPlan.name}</strong>
            </div>
            <div className="policy-card__detail">
              <span>Zone</span>
              <strong>{selectedZone.name}</strong>
            </div>
            <div className="policy-card__detail">
              <span>Premium</span>
              <strong>{currencyFormatter.format(selectedPlan.weeklyPrice)}</strong>
            </div>
            <div className="policy-card__detail">
              <span>Coverage</span>
              <strong>{currencyFormatter.format(selectedPlan.coverage)}</strong>
            </div>
          </div>

          <div className="policy-card__detail">
            <span>Selected week</span>
            <strong>{selectedWeek.label}</strong>
          </div>

          <button className="button button--primary" type="button" onClick={handlePay} disabled={loadingPayment}>
            {loadingPayment ? 'Opening checkout…' : quote ? `Pay ${currencyFormatter.format(selectedPlan.weeklyPrice)} with Razorpay` : 'Waiting for premium…'}
          </button>

          <p className="muted">
            You earn ~{currencyFormatter.format(worker.weeklyIncome)} / week and stay covered for {currencyFormatter.format(selectedPlan.coverage)}.
          </p>

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