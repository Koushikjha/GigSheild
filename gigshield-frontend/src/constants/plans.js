export const plans = [
  {
    name: 'Basic',
    weeklyPrice: 99,
    priceRange: '₹99/week',
    coverage: 1260,
    bestFor: 'Low-volatility zones',
    description: 'Lean protection for riders with predictable routes.',
    features: ['Rain and AQI events covered', 'Fast payout matching', 'Weekly premium control'],
    ctaLabel: 'Select Basic',
  },
  {
    name: 'Standard',
    weeklyPrice: 149,
    priceRange: '₹149/week',
    coverage: 1470,
    bestFor: 'Balanced coverage',
    description: 'The default choice for most active delivery zones.',
    features: ['Rain, AQI, blockage, and curfew covered', 'Dynamic pricing from zone risk', 'Live payout state tracking'],
    ctaLabel: 'Select Standard',
  },
  {
    name: 'Full Shield',
    weeklyPrice: 249,
    priceRange: '₹249/week',
    coverage: 1680,
    bestFor: 'Higher-risk corridors',
    description: 'Broader protection for workers spending more time in disruption-heavy areas.',
    features: ['Highest event sensitivity', 'Best for heavy monsoon weeks', 'Premium reacts to forecast pressure'],
    ctaLabel: 'Select Full Shield',
  },
]

export const DEFAULT_PLAN_NAME = 'Standard'

export function findPlanByName(planName) {
  return plans.find((plan) => plan.name === planName) || plans.find((plan) => plan.name === DEFAULT_PLAN_NAME) || plans[0]
}
