import { useCallback, useEffect, useRef, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/useAuth'

const PHONE_PATTERN = /^[6-9]\d{9}$/
const OTP_PATTERN = /^\d{6}$/
const RESEND_COOLDOWN_SECONDS = 30

const PLATFORMS = [
  { value: 'ZOMATO', label: 'Zomato' },
  { value: 'SWIGGY', label: 'Swiggy' },
  { value: 'OTHER', label: 'Other' },
]

function extractErrorMessage(error, fallback) {
  return error?.response?.data?.message || fallback
}

// Phone-number + OTP login, matching the backend exactly:
// POST /api/v1/auth/send-otp { phone } -> POST /api/v1/auth/verify-otp
// { phone, otp, fullName?, city?, latitude?, longitude?, platform? }.
// There is no email/password anywhere in this system — OTP is the only
// credential (see AuthService / OtpService / User.java on the backend).
export default function LoginPage({ initialMode = 'login' }) {
  const navigate = useNavigate()
  const location = useLocation()
  const { isAuthenticated, sendOtp, verifyOtp } = useAuth()

  const [step, setStep] = useState('phone') // 'phone' | 'otp'
  const [phone, setPhone] = useState('')
  const [otp, setOtp] = useState('')
  const [showRegistration, setShowRegistration] = useState(initialMode === 'register')
  const [fullName, setFullName] = useState('')
  const [city, setCity] = useState('')
  const [platform, setPlatform] = useState('ZOMATO')
  const [coords, setCoords] = useState(null)
  const [locationStatus, setLocationStatus] = useState('idle')
  const [locationMessage, setLocationMessage] = useState('Needed once, for new accounts only.')

  const [sendingOtp, setSendingOtp] = useState(false)
  const [verifying, setVerifying] = useState(false)
  const [error, setError] = useState('')
  const [resendCooldown, setResendCooldown] = useState(0)
  const cooldownRef = useRef(null)

  useEffect(() => {
    setShowRegistration(initialMode === 'register')
  }, [initialMode])

  useEffect(() => {
    return () => {
      if (cooldownRef.current) {
        window.clearInterval(cooldownRef.current)
      }
    }
  }, [])

  const startResendCooldown = useCallback(() => {
    setResendCooldown(RESEND_COOLDOWN_SECONDS)
    if (cooldownRef.current) {
      window.clearInterval(cooldownRef.current)
    }
    cooldownRef.current = window.setInterval(() => {
      setResendCooldown((current) => {
        if (current <= 1) {
          window.clearInterval(cooldownRef.current)
          return 0
        }
        return current - 1
      })
    }, 1000)
  }, [])

  const requestLiveLocation = useCallback(() => {
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      setLocationStatus('error')
      setLocationMessage('Geolocation is not available in this browser — enter your city manually.')
      return
    }

    setLocationStatus('requesting')
    setLocationMessage('Requesting location permission...')

    navigator.geolocation.getCurrentPosition(
      (position) => {
        setCoords({ latitude: position.coords.latitude, longitude: position.coords.longitude })
        setLocationStatus('granted')
        setLocationMessage('Location captured.')
      },
      () => {
        setLocationStatus('denied')
        setLocationMessage('Location permission denied — required to complete registration.')
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 120000 },
    )
  }, [])

  if (isAuthenticated) {
    const destination = location.state?.from?.pathname || '/'
    return <Navigate to={destination} replace />
  }

  const handleSendOtp = async (event) => {
    event.preventDefault()
    setError('')

    if (!PHONE_PATTERN.test(phone)) {
      setError('Enter a valid 10-digit Indian mobile number.')
      return
    }

    setSendingOtp(true)
    try {
      await sendOtp(phone)
      setStep('otp')
      startResendCooldown()
    } catch (sendError) {
      setError(extractErrorMessage(sendError, 'Could not send OTP. Please try again.'))
    } finally {
      setSendingOtp(false)
    }
  }

  const handleResendOtp = async () => {
    if (resendCooldown > 0) {
      return
    }
    setError('')
    setSendingOtp(true)
    try {
      await sendOtp(phone)
      startResendCooldown()
    } catch (sendError) {
      setError(extractErrorMessage(sendError, 'Could not resend OTP. Please try again.'))
    } finally {
      setSendingOtp(false)
    }
  }

  const handleVerifyOtp = async (event) => {
    event.preventDefault()
    setError('')

    if (!OTP_PATTERN.test(otp)) {
      setError('Enter the 6-digit OTP sent to your phone.')
      return
    }

    const registration = showRegistration
      ? {
          fullName: fullName.trim(),
          city: city.trim(),
          platform,
          latitude: coords?.latitude,
          longitude: coords?.longitude,
        }
      : {}

    if (showRegistration) {
      if (!registration.fullName || !registration.city) {
        setError('Full name and city are required to create your account.')
        return
      }
      if (coords === null) {
        setError('Capture your live location to finish registration.')
        return
      }
    }

    setVerifying(true)
    try {
      await verifyOtp(phone, otp, registration)
      navigate('/', { replace: true })
    } catch (verifyError) {
      setError(extractErrorMessage(verifyError, 'Could not verify OTP. Please try again.'))
    } finally {
      setVerifying(false)
    }
  }

  return (
    <main className="auth-shell auth-shell--light">
      <div className="auth-shell__glow auth-shell__glow--left" aria-hidden="true" />
      <div className="auth-shell__glow auth-shell__glow--right" aria-hidden="true" />

      <section className="auth-panel auth-panel--story">
        <div className="auth-brand-row">
          <div className="auth-brand-mark">G</div>
          <div>
            <p className="auth-brand-kicker">GigShield</p>
            <strong className="auth-brand-name">Auto payout cover for gig workers</strong>
          </div>
        </div>

        <div className="auth-story">
          <p className="auth-story__eyebrow">Welcome to GigShield</p>
          <h1 className="auth-story__title">Insurance support designed for everyday delivery partners.</h1>
          <p className="auth-story__subtitle">
            Weekly protection for riders and drivers, priced from your live zone risk with auto payout when eligible.
          </p>
        </div>

        <div className="auth-metrics">
          <article className="auth-metric">
            <strong>Weekly policy pricing</strong>
            <span>Transparent quote before checkout</span>
          </article>
          <article className="auth-metric">
            <strong>Income coverage</strong>
            <span>Protect your expected weekly earnings</span>
          </article>
          <article className="auth-metric">
            <strong>Auto settlement</strong>
            <span>No manual claim forms for eligible events</span>
          </article>
        </div>

        <div className="auth-flow-card">
          <div className="auth-flow-card__top">
            <span className="auth-flow-dot" />
            <span>Log in with your phone number — no password, ever.</span>
          </div>
        </div>
      </section>

      <section className="auth-panel auth-panel--form">
        <div className="auth-card__header">
          <div>
            <h2 className="section__title">
              {step === 'phone' ? 'Log in or create your account' : `Enter the code sent to +91 ${phone}`}
            </h2>
          </div>
        </div>

        {step === 'phone' ? (
          <form className="auth-form" onSubmit={handleSendOtp}>
            <label className="field-group">
              <span>Phone number</span>
              <input
                className="input"
                type="tel"
                inputMode="numeric"
                maxLength={10}
                value={phone}
                onChange={(event) => setPhone(event.target.value.replace(/\D/g, '').slice(0, 10))}
                placeholder="98765 43210"
                autoFocus
              />
            </label>

            {error ? <div className="surface-card muted">{error}</div> : null}

            <button className="button button--primary auth-form__submit" type="submit" disabled={sendingOtp}>
              {sendingOtp ? 'Sending OTP…' : 'Send OTP'}
            </button>

            <p className="auth-card__note">
              New here? Just enter your number — we'll ask a few quick details after the OTP step.
            </p>
          </form>
        ) : (
          <form className="auth-form" onSubmit={handleVerifyOtp}>
            <label className="field-group">
              <span>6-digit OTP</span>
              <input
                className="input"
                type="tel"
                inputMode="numeric"
                maxLength={6}
                value={otp}
                onChange={(event) => setOtp(event.target.value.replace(/\D/g, '').slice(0, 6))}
                placeholder="••••••"
                autoFocus
              />
            </label>

            <div className="field-group">
              <button type="button" className="auth-card__link" onClick={handleResendOtp} disabled={resendCooldown > 0 || sendingOtp}>
                {resendCooldown > 0 ? `Resend OTP in ${resendCooldown}s` : 'Resend OTP'}
              </button>
            </div>

            <div className="field-group">
              <button
                type="button"
                className="auth-card__link"
                onClick={() => setShowRegistration((current) => !current)}
              >
                {showRegistration ? 'Hide new-account details' : "New here? Add your details"}
              </button>
            </div>

            {showRegistration ? (
              <>
                <label className="field-group">
                  <span>Full name</span>
                  <input className="input" value={fullName} onChange={(event) => setFullName(event.target.value)} placeholder="Your name" />
                </label>

                <label className="field-group">
                  <span>City</span>
                  <input className="input" value={city} onChange={(event) => setCity(event.target.value)} placeholder="Delhi" />
                </label>

                <label className="field-group">
                  <span>Delivery platform</span>
                  <select className="input" value={platform} onChange={(event) => setPlatform(event.target.value)}>
                    {PLATFORMS.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </label>

                <div className="field-group">
                  <span>Live location</span>
                  <div className="auth-location-row">
                    <button
                      type="button"
                      className="button button--secondary"
                      onClick={requestLiveLocation}
                      disabled={locationStatus === 'requesting'}
                    >
                      {locationStatus === 'requesting' ? 'Requesting location...' : 'Capture live location'}
                    </button>
                    <span className={`pill ${locationStatus === 'granted' ? 'pill--active' : 'pill--neutral'}`}>
                      {locationStatus === 'granted' ? 'Captured' : locationStatus === 'denied' ? 'Denied' : 'Pending'}
                    </span>
                  </div>
                  <p className="auth-card__note">{locationMessage}</p>
                </div>
              </>
            ) : null}

            {error ? <div className="surface-card muted">{error}</div> : null}

            <button className="button button--primary auth-form__submit" type="submit" disabled={verifying}>
              {verifying ? 'Verifying…' : 'Verify & continue'}
            </button>

            <p className="auth-card__note">
              Wrong number?{' '}
              <button
                type="button"
                className="auth-card__link"
                onClick={() => {
                  setStep('phone')
                  setOtp('')
                  setError('')
                }}
              >
                Change phone number
              </button>
            </p>
          </form>
        )}
      </section>
    </main>
  )
}
