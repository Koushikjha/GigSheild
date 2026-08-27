import { useEffect, useState } from 'react'
import MetricCard from '../components/MetricCard'
import PolicyCard from '../components/PolicyCard'
import StatusBanner from '../components/StatusBanner'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'

const currencyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 0,
})

const dateTimeFormatter = new Intl.DateTimeFormat('en-IN', {
  dateStyle: 'medium',
  timeStyle: 'short',
})

const RISK_TONE = { LOW: 'safe', MEDIUM: 'warning', HIGH: 'danger' }

export default function Dashboard() {
  const { worker, workerLoading, refreshWorker } = useAuth()
  const { pushToast } = useToast()
  const [lastSynced, setLastSynced] = useState('')

  useEffect(() => {
    let active = true

    const tick = async () => {
      try {
        await refreshWorker()
        if (active) {
          setLastSynced(dateTimeFormatter.format(new Date()))
        }
      } catch {
        if (active) {
          pushToast('error', 'Dashboard refresh failed', 'Could not reach the GigShield backend.')
        }
      }
    }

    tick()
    const intervalId = window.setInterval(tick, 30000)

    return () => {
      active = false
      window.clearInterval(intervalId)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const riskBand = worker?.riskBand || 'LOW'
  const tone = RISK_TONE[riskBand] || 'safe'
  const activePolicy = worker?.activePolicy || null
  const firstName = worker?.fullName?.split(' ')[0] || 'Worker'

  return (
    <div className="page page--dashboard">
      <header className="page__header">
        <p className="page__eyebrow">Control center</p>
        <h1 className="page__title page__title--dashboard">Your current payout status and weekly coverage</h1>
        <p className="page__subtitle">See your live risk, claim activity, and policy details in one place.</p>
      </header>

      <section className={`dashboard-spotlight dashboard-spotlight--${tone}`}>
        <div className="dashboard-spotlight__copy">
          <p className="dashboard-spotlight__eyebrow">Live overview</p>
          <h2 className="dashboard-spotlight__title">{firstName}, your zone risk is {riskBand.toLowerCase()}.</h2>
          <p className="dashboard-spotlight__subtitle">
            If a covered event happens while your policy is active, payout starts automatically — no forms to file.
          </p>

          <div className="dashboard-spotlight__chips">
            <span className={`pill pill--${tone}`}>Risk {riskBand}</span>
            <span className="pill pill--neutral">Recommended premium ₹{worker?.recommendedPremium ?? '—'}/week</span>
            <span className="pill pill--neutral">{worker?.accountStatus || 'ACTIVE'}</span>
          </div>
        </div>

        <div className="dashboard-spotlight__panel">
          <div className="dashboard-spotlight__metric">
            <span>City</span>
            <strong>{worker?.city || '—'}</strong>
          </div>
          <div className="dashboard-spotlight__metric">
            <span>Max payout on active policy</span>
            <strong>{currencyFormatter.format(Number(activePolicy?.maxPayoutAmount || 0))}</strong>
          </div>
          <div className="dashboard-spotlight__metric">
            <span>Policy state</span>
            <strong>{activePolicy ? activePolicy.status : 'No active policy'}</strong>
          </div>
        </div>
      </section>

      <StatusBanner
        tone={tone}
        title={activePolicy ? 'Zone monitored — you are covered' : 'No active policy'}
        description={
          activePolicy
            ? 'GigShield is watching weather and AQI triggers for your city automatically.'
            : 'Buy a weekly policy from Plans to start automatic coverage.'
        }
        meta={lastSynced ? `Updated ${lastSynced}` : 'Refreshing live'}
      />

      <section className="grid grid--three">
        <MetricCard
          label="Risk level"
          value={riskBand.charAt(0) + riskBand.slice(1).toLowerCase()}
          subtext="Live weather + AQI risk for your city"
          barValue={riskBand === 'HIGH' ? 85 : riskBand === 'MEDIUM' ? 55 : 20}
          barTone={tone}
        />
        <MetricCard
          label="Total payouts received"
          value={currencyFormatter.format(Number(worker?.totalPayoutReceived || 0))}
          subtext="Automatic payouts across recent claims"
        />
        <MetricCard
          label="Claims pending review"
          value={String(worker?.pendingClaimsCount || 0)}
          subtext={`${worker?.recentClaimsCount || 0} claims in recent history`}
        />
      </section>

      <section className="page-section">
        <div className="section-header">
          <div>
            <p className="section__eyebrow">Policy</p>
            <h2 className="section__title">Active weekly cover</h2>
          </div>
        </div>
        {activePolicy ? (
          <PolicyCard policy={activePolicy} />
        ) : (
          <div className="surface-card muted">No active policy. Visit Plans to buy weekly cover.</div>
        )}
        {workerLoading && !worker ? <div className="surface-card muted">Loading dashboard…</div> : null}
      </section>
    </div>
  )
}
