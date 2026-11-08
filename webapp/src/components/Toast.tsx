import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react'

type Kind = 'info' | 'error'
const ToastContext = createContext<(text: string, kind?: Kind) => void>(() => {})

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<{ text: string; kind: Kind } | null>(null)
  const timer = useRef<number | undefined>(undefined)
  const show = useCallback((text: string, kind: Kind = 'info') => {
    window.clearTimeout(timer.current)
    setToast({ text, kind })
    timer.current = window.setTimeout(() => setToast(null), 2600)
  }, [])
  return (
    <ToastContext.Provider value={show}>
      {children}
      {toast && (
        <div className={`toast toast--${toast.kind}`} role="status">
          {toast.text}
        </div>
      )}
    </ToastContext.Provider>
  )
}

export const useToast = () => useContext(ToastContext)
