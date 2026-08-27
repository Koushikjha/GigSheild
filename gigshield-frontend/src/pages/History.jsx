import { useEffect, useState } from 'react'
import { getClaims } from '../api/claims'
import { getPolicyHistory } from '../api/policies'
import PayoutRow from '../components/PayoutRow'
import PolicyCard from '../components/PolicyCard'
import { useToast } from '../context/ToastContext'

const currencyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 0,
})

const PAID_STATUSES = new Set(['PAID', 'AUTO_APPROVED', 'ADMIN_APPROVED'])

export default function History() {
  const { pushToast } = useToast()
  const [claims, setClaims] = useState([])
  const [policies, setPolicies] = useState([])
  const [loading, setLoading] = useState(true)
  const [tab, setTab] = useState('payouts')
  const [sortDirection, setSortDirection] = useState('desc')
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true

    const loadHistory = async () => {
      try {
        const [claimsPage, policiesPage] = await Promise.all([getClaims(0), getPolicyHistory(0)])

        if (!active) {
          return
        }

        setClaims(claimsPage.content ?? [])
        setPolicies(policiesPage.content ?? [])
        setError('')
      } catch {
        if (!active) {
          return
        }

        setError('Unable to load history right now.')
        pushToast('error', 'History unavailable', 'Could not reach the GigShield backend.')
      } finally {
        if (active) {
          setLoading(false)
        }
      }
    }

    loadHistory()

    return () => {
      active = false
    }
  }, [pushToast])

  const payouts = claims
    .filter((claim) => PAID_STATUSES.has(claim.status))
    .sort((left, right) => {
      const leftDate = new Date(left.createdAt).getTime()
      const rightDate = new Date(right.createdAt).getTime()
      return sortDirection === 'desc' ? rightDate - leftDate : leftDate - rightDate
    })

  const totalReceived = payouts.reduce((sum, claim) => sum + Number(claim.payoutAmount || 0), 0)
  const totalPaidIn = policies.reduce((sum, policy) => sum + Number(policy.premiumPaid || 0), 0)

  return (
    <div className="page">
      <header className="page__header">
        <p className="page__eyebrow">History</p>
        <h1 className="page__title">Your payouts and policy history</h1>
        <p className="page__subtitle">Check what you received, what you paid, and every weekly policy record.</p>
      </header>

      <section className="grid grid--two">
        <article className="summary-card summary-card--success">
          <div>
            <p className="page__eyebrow">Total received</p>
            <p className="summary-card__value">{currencyFormatter.format(totalReceived)}</p>
          </div>
          <span className="pill pill--paid">Green cashflow</span>
        </article>

        <article className="summary-card summary-card--neutral">
          <div>
            <p className="page__eyebrow">Total paid in</p>
            <p className="summary-card__value">{currencyFormatter.format(totalPaidIn)}</p>
          </div>
          <span className="pill pill--neutral">Weekly premiums</span>
        </article>
      </section>

      <div className="tabs-row">
        <div className="tab-card" role="tablist" aria-label="History tabs">
          <button type="button" className={`tab-button ${tab === 'payouts' ? 'tab-button--active' : ''}`} onClick={() => setTab('payouts')}>
            Payouts
          </button>
          <button type="button" className={`tab-button ${tab === 'policies' ? 'tab-button--active' : ''}`} onClick={() => setTab('policies')}>
            Policies
          </button>
        </div>

        {tab === 'payouts' ? (
          <button className="button button--secondary" type="button" onClick={() => setSortDirection(sortDirection === 'desc' ? 'asc' : 'desc')}>
            {sortDirection === 'desc' ? 'Newest first' : 'Oldest first'}
          </button>
        ) : null}
      </div>

      {error ? <div className="surface-card muted">{error}</div> : null}

      {tab === 'payouts' ? (
        <section className="list">
          {payouts.map((payout) => (
            <PayoutRow key={payout.id} payout={payout} />
          ))}
          {loading ? <div className="surface-card muted">Loading payout history…</div> : null}
          {!loading && payouts.length === 0 && !error ? <div className="surface-card muted">No payouts yet.</div> : null}
        </section>
      ) : (
        <section className="list">
          {policies.map((policy) => (
            <PolicyCard key={policy.id} policy={policy} dense />
          ))}
          {loading ? <div className="surface-card muted">Loading policy history…</div> : null}
          {!loading && policies.length === 0 && !error ? <div className="surface-card muted">No policies yet.</div> : null}
        </section>
      )}
    </div>
  )
}
