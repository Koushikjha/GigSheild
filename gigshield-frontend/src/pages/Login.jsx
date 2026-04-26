import { useCallback, useEffect, useRef, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

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
  const zonesWithCoordinates = zones.filter((zone) => Number.isFinite(zone.latitude) && Number.isFinite(zone.longitude))

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

export default function LoginPage({ initialMode = 'login' }) {
  const navigate = useNavigate()
  const location = useLocation()
  const {
    isAuthenticated,
    login,
    register,
    workerZones,
    updateZone,
    setWorker,
  } = useAuth()
  const [mode, setMode] = useState(initialMode)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [name, setName] = useState('')
  const [weeklyIncome, setWeeklyIncome] = useState('')
  const [locationStatus, setLocationStatus] = useState('idle')
  const [locationMessage, setLocationMessage] = useState('Location permission is required for live zone detection.')
  const locationRequestedRef = useRef(false)

  const requestLiveLocation = useCallback(() => {
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      setLocationStatus('error')
      setLocationMessage('Geolocation is not available in this browser.')
      return
    }

    setLocationStatus('requesting')
    setLocationMessage('Requesting location permission...')

    navigator.geolocation.getCurrentPosition(
      (position) => {
        const nearestZoneId = getNearestZoneId(workerZones, position.coords.latitude, position.coords.longitude)

        if (nearestZoneId) {
          updateZone(nearestZoneId)
        }

        setWorker((currentWorker) => ({
          ...currentWorker,
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          locationCapturedAt: new Date().toISOString(),
        }))

        const nearestZone = workerZones.find((zone) => zone.id === nearestZoneId)
        setLocationStatus('granted')
        setLocationMessage(
          nearestZone
            ? `Live location captured and mapped to ${nearestZone.name}.`
            : 'Live location captured successfully.',
        )
      },
      () => {
        setLocationStatus('denied')
        setLocationMessage('Location permission denied. Enable it to use live zone detection.')
      },
      {
        enableHighAccuracy: true,
        timeout: 10000,
        maximumAge: 120000,
      },
    )
  }, [setWorker, updateZone, workerZones])

  useEffect(() => {
    setMode(initialMode)
  }, [initialMode])

  useEffect(() => {
    if (locationRequestedRef.current) {
      return
    }

    locationRequestedRef.current = true
    requestLiveLocation()
  }, [requestLiveLocation])

  useEffect(() => {
    if (isAuthenticated) {
      const destination = location.state?.from?.pathname || '/'
      navigate(destination, { replace: true })
    }
  }, [isAuthenticated, location.state, navigate])

  const handleSubmit = (event) => {
    event.preventDefault()

    if (mode === 'register') {
      register({
        name,
        weeklyIncome,
      })
      navigate('/', { replace: true })
      return
    }

    login({ name, email, password })
    navigate('/', { replace: true })
  }

  if (isAuthenticated) {
    return <Navigate to="/" replace />
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
            <span>Live risk, no claims filing, instant payout when eligible.</span>
          </div>
        </div>
      </section>

      <section className="auth-panel auth-panel--form">
        <div className="auth-card__tabs" role="tablist" aria-label="Authentication mode">
          <button type="button" className={`tab-button ${mode === 'login' ? 'tab-button--active' : ''}`} onClick={() => setMode('login')}>
            Login
          </button>
          <button type="button" className={`tab-button ${mode === 'register' ? 'tab-button--active' : ''}`} onClick={() => setMode('register')}>
            Register
          </button>
        </div>

        <div className="auth-card__header">
          <div>
            <h2 className="section__title">{mode === 'register' ? 'Set up your GigShield profile' : 'Access your dashboard'}</h2>
          </div>
        </div>

        <form className="auth-form" onSubmit={handleSubmit}>
          {mode === 'register' ? (
            <>
              <label className="field-group">
                <span>Full name</span>
                <input className="input" value={name} onChange={(event) => setName(event.target.value)} placeholder="Your name" />
              </label>

              <label className="field-group">
                <span>Weekly income</span>
                <input className="input" inputMode="numeric" value={weeklyIncome} onChange={(event) => setWeeklyIncome(event.target.value)} placeholder="4200" />
              </label>
            </>
          ) : null}

          {mode === 'login' ? (
            <label className="field-group">
              <span>Name</span>
              <input className="input" value={name} onChange={(event) => setName(event.target.value)} placeholder="Your name" />
            </label>
          ) : null}

          <label className="field-group">
            <span>Email</span>
            <input className="input" type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="name@example.com" />
          </label>

          <label className="field-group">
            <span>Password</span>
            <input className="input" type="password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="••••••••" />
          </label>

          <div className="field-group">
            <span>Live location permission</span>
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

          <button className="button button--primary auth-form__submit" type="submit">
            {mode === 'register' ? 'Create account' : 'Login to dashboard'}
          </button>

          <p className="auth-card__note">
            {mode === 'register' ? 'Already have an account? ' : 'Create a new account? '}
            <button
              type="button"
              className="auth-card__link"
              onClick={() => setMode(mode === 'login' ? 'register' : 'login')}
            >
              {mode === 'register' ? 'Switch to login' : 'Register here'}
            </button>
          </p>
        </form>
      </section>
    </main>
  )
}