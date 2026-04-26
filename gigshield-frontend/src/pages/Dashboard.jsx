import { useEffect, useState } from 'react'
import { getDashboardData, getZoneStatus } from '../api/dashboard'
import MetricCard from '../components/MetricCard'
import PolicyCard from '../components/PolicyCard'
import StatusBanner from '../components/StatusBanner'
import { findPlanByName } from '../constants/plans'
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

export default function Dashboard() {
  const { worker, currentPlan } = useAuth()
  const { pushToast } = useToast()
  const [dashboardData, setDashboardData] = useState(null)
  const [zoneStatus, setZoneStatus] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [lastSynced, setLastSynced] = useState('')

  useEffect(() => {
    let active = true

    const refreshDashboard = async () => {
      try {
        const [nextDashboardData, nextZoneStatus] = await Promise.all([getDashboardData(), getZoneStatus()])

        if (!active) {
          return
        }

        setDashboardData(nextDashboardData)
        setZoneStatus(nextZoneStatus)
        setLastSynced(dateTimeFormatter.format(new Date()))
        setError('')
      } catch {
        if (!active) {
          return
        }

        setError('Unable to refresh dashboard data right now.')
        pushToast('error', 'Dashboard refresh failed', 'The mock API is temporarily unavailable.')
      } finally {
        if (active) {
          setLoading(false)
        }
      }
    }

    refreshDashboard()
    const intervalId = window.setInterval(refreshDashboard, 30000)

    return () => {
      active = false
      window.clearInterval(intervalId)
    }
  }, [pushToast])

  const activeZoneStatus = zoneStatus ?? dashboardData?.zoneStatus ?? {}
  const activeWorker = {
    ...(dashboardData?.worker ?? {}),
    ...worker,
  }
  const metrics = dashboardData?.metrics ?? {}
  const activePolicy = dashboardData?.activePolicy ?? null
  const policyPlan = findPlanByName(currentPlan || activePolicy?.planName)
  const resolvedActivePolicy = activePolicy
    ? {
        ...activePolicy,
        planName: policyPlan.name,
        premiumPaid: policyPlan.weeklyPrice,
        coverageAmount: policyPlan.coverage,
      }
    : null
  const riskLabel = activeZoneStatus.riskLevel ? activeZoneStatus.riskLevel.charAt(0).toUpperCase() + activeZoneStatus.riskLevel.slice(1) : 'Low'
  const tone = activeZoneStatus.tone || 'safe'
  const riskScore = activeZoneStatus.riskScore || metrics.riskScore || 0
  const firstName = activeWorker.name?.split(' ')[0] || 'Worker'

  return (
    <div className="page page--dashboard">
      <header className="page__header">
        <p className="page__eyebrow">Control center</p>
        <h1 className="page__title page__title--dashboard">Your current payout status and weekly coverage</h1>
        <p className="page__subtitle">
          See your live risk, delivery eligibility, and policy details in one place.
        </p>
      </header>

      <section className={`dashboard-spotlight dashboard-spotlight--${tone}`}>
        <div className="dashboard-spotlight__copy">
          <p className="dashboard-spotlight__eyebrow">Live overview</p>
          <h2 className="dashboard-spotlight__title">{firstName}, your zone is {activeZoneStatus.title?.toLowerCase() || 'being monitored'}.</h2>
          <p className="dashboard-spotlight__subtitle">
            {activeZoneStatus.heroDetail || 'If a covered event happens during an eligible delivery, payout starts automatically.'}
          </p>

          <div className="dashboard-spotlight__chips">
            <span className={`pill pill--${tone}`}>Risk {riskLabel}</span>
            <span className="pill pill--neutral">Score {riskScore}/100</span>
            <span className="pill pill--neutral">{activeZoneStatus.onDelivery ? 'On delivery' : 'Not on delivery'}</span>
          </div>
        </div>

        <div className="dashboard-spotlight__panel">
          <div className="dashboard-spotlight__metric">
            <span>Zone</span>
            <strong>{activeWorker.zone}</strong>
          </div>
          <div className="dashboard-spotlight__metric">
            <span>Weekly coverage</span>
            <strong>{currencyFormatter.format(Number(resolvedActivePolicy?.coverageAmount || 0))}</strong>
          </div>
          <div className="dashboard-spotlight__metric">
            <span>Payout state</span>
            <strong>{activeZoneStatus.eligible ? 'Eligible' : 'Not eligible'}</strong>
          </div>
        </div>
      </section>

      <StatusBanner
        tone={tone}
        title={activeZoneStatus.title || 'Zone safe'}
        description={activeZoneStatus.description || 'Live zone data is loading.'}
        meta={lastSynced ? `Updated ${lastSynced}` : 'Refreshing live'}
      />

      <article className={`hero-card hero-card--${tone}`}>
        <div className="hero-card__header">
          <div>
            <p className="page__eyebrow">Delivery eligibility</p>
            <h2 className="hero-card__title">{activeWorker.name}</h2>
            <p className="hero-card__label">Registered zone: {activeWorker.zone}</p>
          </div>
          <span className={`pill pill--${tone}`}>{activeZoneStatus.statusText || 'live'}</span>
        </div>

        <div className="hero-card__grid">
          <div className="hero-card__status">
            <strong>{activeZoneStatus.heroLabel || 'Ready for auto payout checks'}</strong>
            <span>{activeZoneStatus.heroDetail || 'If a covered event happens during an eligible delivery, payout starts automatically.'}</span>
          </div>
          <div className="hero-card__status">
            <strong>Current state</strong>
            <span>
              {activeZoneStatus.onDelivery ? 'On delivery' : 'Not on delivery'} · {activeZoneStatus.eligible ? 'Eligible' : 'Not eligible'}
              {activeZoneStatus.processingAmount ? ` · ₹${activeZoneStatus.processingAmount} processing` : ''}
            </span>
          </div>
        </div>
      </article>

      <section className="grid grid--three">
        <MetricCard
          label="Risk level"
          value={riskLabel}
          subtext={`Risk score ${riskScore}/100`}
          barValue={riskScore}
          barTone={activeZoneStatus.riskLevel || metrics.riskLevel || 'safe'}
        />
        <MetricCard
          label="Payouts this month"
          value={currencyFormatter.format(Number(metrics.payoutsThisMonth || 0))}
          subtext="Automatic payouts received in the current month"
        />
        <MetricCard
          label="Weeks covered"
          value={String(metrics.weeksCovered || 0)}
          subtext="Weekly policies completed without filing a claim"
        />
      </section>

      <section className="page-section">
        <div className="section-header">
          <div>
            <p className="section__eyebrow">Policy</p>
            <h2 className="section__title">Active weekly cover</h2>
          </div>
        </div>
        {resolvedActivePolicy ? <PolicyCard policy={resolvedActivePolicy} /> : <div className="surface-card muted">No active policy loaded.</div>}
        {error ? <div className="surface-card muted">{error}</div> : null}
      </section>
    </div>
  )
}