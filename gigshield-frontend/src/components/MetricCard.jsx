export default function MetricCard({ label, value, subtext, barValue, barTone = 'safe' }) {
  const barToneClass =
    typeof barValue === 'number'
      ? barValue > 70
        ? 'metric-card__bar-fill metric-card__bar-fill--danger'
        : barValue >= 40
          ? 'metric-card__bar-fill metric-card__bar-fill--warning'
          : 'metric-card__bar-fill'
      : barTone === 'warning'
        ? 'metric-card__bar-fill metric-card__bar-fill--warning'
        : barTone === 'danger'
          ? 'metric-card__bar-fill metric-card__bar-fill--danger'
          : 'metric-card__bar-fill'

  return (
    <article className="metric-card">
      <p className="metric-card__label">{label}</p>
      <p className="metric-card__value">{value}</p>
      {subtext ? <p className="metric-card__subtext">{subtext}</p> : null}
      {typeof barValue === 'number' ? (
        <div className="metric-card__bar" aria-hidden="true">
          <div className={barToneClass} style={{ width: `${Math.max(0, Math.min(100, barValue))}%` }} />
        </div>
      ) : null}
    </article>
  )
}