import { createContext, useContext, useState } from 'react'

const ToastContext = createContext(null)

let toastSequence = 0

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])

  const dismissToast = (toastId) => {
    setToasts((currentToasts) => currentToasts.filter((toast) => toast.id !== toastId))
  }

  const pushToast = (tone, title, message = '') => {
    const id = `${Date.now()}-${toastSequence + 1}`

    toastSequence += 1
    setToasts((currentToasts) => [...currentToasts, { id, tone, title, message }])

    window.setTimeout(() => {
      dismissToast(id)
    }, 3800)

    return id
  }

  return (
    <ToastContext.Provider value={{ toasts, pushToast, dismissToast }}>
      {children}
    </ToastContext.Provider>
  )
}

export function useToast() {
  const context = useContext(ToastContext)

  if (!context) {
    throw new Error('useToast must be used within ToastProvider')
  }

  return context
}