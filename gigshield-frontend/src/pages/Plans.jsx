import PlanCard from '../components/PlanCard'
import { useNavigate } from 'react-router-dom'
import { plans } from '../constants/plans'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../context/ToastContext'

export default function Plans() {
  const navigate = useNavigate()
  const { currentPlan, setCurrentPlan } = useAuth()
  const { pushToast } = useToast()

  const handleSelect = (plan) => {
    if (plan.name === currentPlan) {
      pushToast('info', `${plan.name} is already active`, 'Continuing to weekly checkout.')
      navigate('/buy-policy', {
        state: {
          fromPlans: true,
          selectedPlanName: plan.name,
        },
      })
      return
    }

    setCurrentPlan(plan.name)
    pushToast('success', `${plan.name} selected`, 'Plan updated. Continue with week selection and payment.')
    navigate('/buy-policy', {
      state: {
        fromPlans: true,
        selectedPlanName: plan.name,
      },
    })
  }

  return (
    <div className="page">
      <header className="page__header">
        <p className="page__eyebrow">Plans</p>
        <h1 className="page__title">Choose a weekly plan that matches your zone</h1>
        <p className="page__subtitle">Selecting a plan takes you to week selection and Razorpay checkout. No auto-renewal.</p>
      </header>

      <section className="grid grid--three">
        {plans.map((plan) => (
          <PlanCard key={plan.name} plan={plan} isCurrent={plan.name === currentPlan} onSelect={handleSelect} />
        ))}
      </section>

      <footer className="surface-card">
        <strong>No auto-renewal. You choose each week. Skip anytime.</strong>
        <p className="muted" style={{ marginTop: 8 }}>
          Switch plans when the zone changes, when weather risk rises, or when you want a tighter premium band.
        </p>
      </footer>
    </div>
  )
}