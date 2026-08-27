const toneDetails = {
  safe: {
    className: 'status-banner--safe',
    accentLabel: 'Stable',
  },
  warning: {
    className: 'status-banner--warning',
    accentLabel: 'Watching',
  },
  danger: {
    className: 'status-banner--danger',
    accentLabel: 'Active',
  },
}

export default function StatusBanner({ tone = 'safe', title, description, meta }) {
  const currentTone = toneDetails[tone] ?? toneDetails.safe

  return (
    <section className={`status-banner ${currentTone.className}`}>
      <span className="status-banner__accent" aria-hidden="true" />
      <div className="status-banner__content">
        <p className="page__eyebrow">Live zone status</p>
        <h2 className="status-banner__title">{title}</h2>
        <p className="status-banner__description">{description}</p>
      </div>
      {meta ? <span className="pill pill--neutral">{meta}</span> : <span className="pill pill--neutral">{currentTone.accentLabel}</span>}
    </section>
  )
}