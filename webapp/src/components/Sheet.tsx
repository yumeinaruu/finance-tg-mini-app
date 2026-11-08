import { useEffect, type ReactNode } from 'react'
import { tg } from '../telegram'

/** Shows the native Telegram back button while [active]; falls back to nothing outside Telegram. */
export function useBackButton(active: boolean, onBack: () => void) {
  useEffect(() => {
    if (!active || !tg) return
    tg.BackButton.show()
    tg.BackButton.onClick(onBack)
    return () => {
      tg?.BackButton.offClick(onBack)
      tg?.BackButton.hide()
    }
  }, [active, onBack])
}

interface Props {
  title: string
  onClose: () => void
  children: ReactNode
}

/** Bottom sheet for forms. */
export function Sheet({ title, onClose, children }: Props) {
  useBackButton(true, onClose)
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    document.body.classList.add('no-scroll')
    return () => {
      window.removeEventListener('keydown', onKey)
      document.body.classList.remove('no-scroll')
    }
  }, [onClose])
  return (
    <div className="sheet-backdrop" onClick={onClose}>
      <div className="sheet" role="dialog" aria-label={title} onClick={(e) => e.stopPropagation()}>
        <div className="sheet__header">
          <span className="sheet__title">{title}</span>
          <button className="icon-button" aria-label="Закрыть" onClick={onClose}>
            ✕
          </button>
        </div>
        <div className="sheet__body">{children}</div>
      </div>
    </div>
  )
}
