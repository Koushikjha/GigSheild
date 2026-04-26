import { useEffect, useState } from 'react'
import { getClaims } from '../api/claims'
import ClaimItem from '../components/ClaimItem'
import StatusBanner from '../components/StatusBanner'
import { useToast } from '../context/ToastContext'

export default function Claims() {
  const { pushToast } = useToast()
  const [claimsData, setClaimsData] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true

    const loadClaims = async () => {
      try {
        const response = await getClaims()

        if (!active) {
          return
        }

        setClaimsData(response.claims ?? [])
        setError('')
      } catch {
        if (!active) {
          return
        }

        setError('Unable to load claims right now.')
        pushToast('error', 'Claims unavailable', 'The mock API did not respond in time.')
      } finally {
        if (active) {
          setLoading(false)
        }
      }
    }

    loadClaims()

    return () => {
      active = false
    }
  }, [pushToast])

  const processingClaims = claimsData.filter((claim) => claim.status.toLowerCase() === 'processing')

  return (
    <div className="page">
      <header className="page__header">
        <p className="page__eyebrow">My claims</p>
        <h1 className="page__title">Track your payout progress</h1>
        <p className="page__subtitle">
          You do not need to file anything manually. Covered events create payout requests automatically.
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

      {error ? <div className="surface-card muted">{error}</div> : null}

      <section className="list">
        {claimsData.map((claim) => (
          <ClaimItem key={claim.id} claim={claim} />
        ))}
        {loading ? <div className="surface-card muted">Loading claims…</div> : null}
      </section>
    </div>
  )
}