import { useToast } from '../context/ToastContext'

export default function ToastHost() {
  const { toasts, dismissToast } = useToast()

  return (
    <div className="toast-host" aria-live="polite" aria-atomic="true">
      {toasts.map((toast) => (
        <article className={`toast toast--${toast.tone}`} key={toast.id}>
          <div className="toast__header">
            <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
              <span className="toast__tone" aria-hidden="true" />
              <div>
                <p className="toast__title">{toast.title}</p>
                {toast.message ? <p className="toast__message">{toast.message}</p> : null}
              </div>
            </div>
            <button className="toast__dismiss" type="button" onClick={() => dismissToast(toast.id)} aria-label="Dismiss toast">
              ×
            </button>
          </div>
        </article>
      ))}
    </div>
  )
}