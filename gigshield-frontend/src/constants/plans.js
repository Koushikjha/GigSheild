// Display-only metadata for the backend's three real PolicyTier values
// (STANDARD | GOLD | PREMIUM — see com.gigshield.policy.enums.PolicyTier).
// All numbers (premium, payout, description) come live from
// GET /api/v1/policies/tiers — nothing here is pricing data, just how each
// tier is labelled and which events it covers on the marketing copy.
export const TIER_DISPLAY = {
  STANDARD: {
    label: 'Standard',
    badge: 'Balanced coverage',
    features: ['Rain and AQI events covered', 'Fast payout matching', 'Weekly premium control'],
  },
  GOLD: {
    label: 'Gold',
    badge: 'Enhanced coverage',
    features: ['Rain, AQI, and traffic-blockage covered', 'Higher payout ratio than Standard', 'Live payout state tracking'],
  },
  PREMIUM: {
    label: 'Premium',
    badge: 'Full coverage',
    features: ['Highest payout ratio of any tier', 'Best for heavy monsoon weeks', 'Covers curfew and war-risk events too'],
  },
}

export function getTierDisplay(tier) {
  return TIER_DISPLAY[tier] || { label: tier, badge: '', features: [] }
}
