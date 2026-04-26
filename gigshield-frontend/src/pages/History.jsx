import { useEffect, useState } from 'react'
import { getHistory } from '../api/policies'
import PayoutRow from '../components/PayoutRow'
import PolicyCard from '../components/PolicyCard'
import { useToast } from '../context/ToastContext'

const currencyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 0,
})

export default function History() {
  const { pushToast } = useToast()
  const [historyData, setHistoryData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [tab, setTab] = useState('payouts')
  const [sortDirection, setSortDirection] = useState('desc')
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true

    const loadHistory = async () => {
      try {
        const response = await getHistory()

        if (!active) {
          return
        }

        setHistoryData(response)
        setError('')
      } catch {
        if (!active) {
          return
        }

        setError('Unable to load history right now.')
        pushToast('error', 'History unavailable', 'The mock API could not return payout history.')
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

  const payouts = [...(historyData?.payouts ?? [])].sort((left, right) => {
    const leftDate = new Date(`${left.date}T00:00:00`).getTime()
    const rightDate = new Date(`${right.date}T00:00:00`).getTime()
    return sortDirection === 'desc' ? rightDate - leftDate : leftDate - rightDate
  })

  const policies = historyData?.policies ?? []

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
            <p className="summary-card__value">{currencyFormatter.format(Number(historyData?.summary?.totalReceived || 0))}</p>
          </div>
          <span className="pill pill--paid">Green cashflow</span>
        </article>

        <article className="summary-card summary-card--neutral">
          <div>
            <p className="page__eyebrow">Total paid in</p>
            <p className="summary-card__value">{currencyFormatter.format(Number(historyData?.summary?.totalPaidIn || 0))}</p>
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
        </section>
      ) : (
        <section className="list">
          {policies.map((policy) => (
            <PolicyCard key={policy.id} policy={policy} dense />
          ))}
          {loading ? <div className="surface-card muted">Loading policy history…</div> : null}
        </section>
      )}
    </div>
  )
}