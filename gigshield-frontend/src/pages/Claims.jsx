import { useEffect, useState } from 'react'
import { getClaims, reportCancelledOrder } from '../api/claims'
import ClaimItem from '../components/ClaimItem'
import StatusBanner from '../components/StatusBanner'
import { useToast } from '../context/ToastContext'

// datetime-local inputs work in the browser's local time with no timezone
// suffix (e.g. "2026-08-27T14:30") — the backend takes that as a plain
// LocalDateTime, matching how the rest of this app already treats time.
function toDatetimeLocalValue(date) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export default function Claims() {
  const { pushToast } = useToast()
  const [claimsData, setClaimsData] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [showReportForm, setShowReportForm] = useState(false)
  const [cancelledAt, setCancelledAt] = useState(() => toDatetimeLocalValue(new Date()))
  const [note, setNote] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const loadClaims = async () => {
    try {
      const page = await getClaims(0)
      setClaimsData(page.content ?? [])
      setError('')
    } catch {
      setError('Unable to load claims right now.')
      pushToast('error', 'Claims unavailable', 'Could not reach the GigShield backend.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let active = true

    const load = async () => {
      await loadClaims()
      if (!active) {
        return
      }
    }

    load()

    return () => {
      active = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const processingClaims = claimsData.filter(
    (claim) => claim.status === 'PENDING_FRAUD_CHECK' || claim.status === 'AUTO_APPROVED' || claim.status === 'ADMIN_APPROVED',
  )

  const handleReportCancelledOrder = async (event) => {
    event.preventDefault()
    setSubmitting(true)
    try {
      // <input type="datetime-local"> gives "YYYY-MM-DDTHH:mm" with no
      // seconds — pad it so it parses the same way everywhere.
      const isoLocal = cancelledAt.length === 16 ? `${cancelledAt}:00` : cancelledAt
      await reportCancelledOrder(isoLocal, note.trim() || undefined)
      pushToast('success', 'Cancelled order reported', 'We checked it against live conditions at that time and place.')
      setNote('')
      setCancelledAt(toDatetimeLocalValue(new Date()))
      setShowReportForm(false)
      await loadClaims()
    } catch (submitError) {
      const backendMessage = submitError.response?.data?.message
      pushToast('error', 'Could not file this claim', backendMessage || 'Please check the time and try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="page">
      <header className="page__header">
        <p className="page__eyebrow">My claims</p>
        <h1 className="page__title">Track your payout progress</h1>
        <p className="page__subtitle">
          Weather and city-wide events create payout requests automatically. A single cancelled order is different —
          report it yourself, with when it happened, and we'll check it against real conditions at that time and place.
        </p>
      </header>

      {processingClaims.length > 0 ? (
        <StatusBanner
          tone="warning"
          title="A payout is currently processing"
          description={`There ${processingClaims.length === 1 ? 'is 1 claim' : `are ${processingClaims.length} claims`} moving through verification and payout.`}
          meta="No action needed"
        />
      ) : null}

      <section className="surface-card stack">
        <div className="section-header">
          <div>
            <p className="section__eyebrow">Single cancelled order</p>
            <h2 className="section__title">Report a cancelled order</h2>
          </div>
          <button
            type="button"
            className="button button--secondary"
            onClick={() => setShowReportForm((current) => !current)}
          >
            {showReportForm ? 'Cancel' : 'Report a cancellation'}
          </button>
        </div>

        {showReportForm ? (
          <form className="auth-form" onSubmit={handleReportCancelledOrder}>
            <label className="field-group">
              <span>When was the order cancelled?</span>
              <input
                className="input"
                type="datetime-local"
                value={cancelledAt}
                max={toDatetimeLocalValue(new Date())}
                onChange={(event) => setCancelledAt(event.target.value)}
                required
              />
            </label>

            <label className="field-group">
              <span>Note (optional)</span>
              <input
                className="input"
                value={note}
                onChange={(event) => setNote(event.target.value.slice(0, 280))}
                placeholder="e.g. restaurant cancelled during heavy rain"
              />
            </label>

            <button className="button button--primary" type="submit" disabled={submitting}>
              {submitting ? 'Checking…' : 'Submit claim'}
            </button>

            <p className="auth-card__note">
              Must be within the last 72 hours. We verify it against live weather/AQI conditions for your registered
              location at that exact time — not "right now" — so please give the actual time it happened.
            </p>
          </form>
        ) : (
          <p className="muted">Only for a single order — not for city-wide weather events, which pay out on their own.</p>
        )}
      </section>

      {error ? <div className="surface-card muted">{error}</div> : null}

      <section className="list">
        {claimsData.map((claim) => (
          <ClaimItem key={claim.id} claim={claim} />
        ))}
        {loading ? <div className="surface-card muted">Loading claims…</div> : null}
        {!loading && claimsData.length === 0 && !error ? (
          <div className="surface-card muted">No claims yet — covered events will create one automatically.</div>
        ) : null}
      </section>
    </div>
  )
}
