import { useCallback, useState } from 'react'
import ToastContext from './toastContext'

let toastSequence = 0

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])

  const dismissToast = useCallback((toastId) => {
    setToasts((currentToasts) =>
      currentToasts.filter((toast) => toast.id !== toastId),
    )
  }, [])

  const pushToast = useCallback(
    (tone, title, message = '') => {
      const id = `${Date.now()}-${toastSequence + 1}`

      toastSequence += 1

      setToasts((currentToasts) => [
        ...currentToasts,
        { id, tone, title, message },
      ])

      window.setTimeout(() => {
        dismissToast(id)
      }, 3800)

      return id
    },
    [dismissToast],
  )

  return (
    <ToastContext.Provider value={{ toasts, pushToast, dismissToast }}>
      {children}
    </ToastContext.Provider>
  )
}
