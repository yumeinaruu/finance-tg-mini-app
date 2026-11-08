import type { ReactNode } from 'react'
import type { Categories } from '../categories'
import type { CategoryType } from '../types'

export function Loader() {
  return <div className="loader" aria-label="Загрузка" />
}

export function ErrorBox({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="empty">
      <div className="empty__icon">⚠️</div>
      <div>{message}</div>
      {onRetry && (
        <button className="button button--secondary" onClick={onRetry}>
          Повторить
        </button>
      )}
    </div>
  )
}

export function Empty({ icon, children }: { icon: string; children: ReactNode }) {
  return (
    <div className="empty">
      <div className="empty__icon">{icon}</div>
      <div>{children}</div>
    </div>
  )
}

export function Section({ title, action, children }: { title?: string; action?: ReactNode; children: ReactNode }) {
  return (
    <section className="section">
      {(title || action) && (
        <div className="section__header">
          <span>{title}</span>
          {action}
        </div>
      )}
      <div className="section__body">{children}</div>
    </section>
  )
}

interface SegmentedProps<T extends string> {
  value: T
  options: { value: T; label: string }[]
  onChange: (value: T) => void
}

export function Segmented<T extends string>({ value, options, onChange }: SegmentedProps<T>) {
  return (
    <div className="segmented" role="tablist">
      {options.map((o) => (
        <button key={o.value} role="tab" aria-selected={o.value === value} className={o.value === value ? 'active' : ''} onClick={() => onChange(o.value)}>
          {o.label}
        </button>
      ))}
    </div>
  )
}

/** Grid of category chips of one type. */
export function CategoryPicker({
  categories,
  type,
  value,
  onChange,
}: {
  categories: Categories
  type: CategoryType
  value: number | null
  onChange: (id: number) => void
}) {
  const options = categories.active(type)
  const current = value !== null && !options.some((c) => c.id === value) ? [categories.get(value)] : []
  return (
    <div className="chips">
      {[...options, ...current].map((c) => (
        <button key={c.id} type="button" className={c.id === value ? 'chip chip--active' : 'chip'} onClick={() => onChange(c.id)}>
          {c.emoji} {c.name}
        </button>
      ))}
    </div>
  )
}

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="field">
      <span className="field__label">{label}</span>
      {children}
    </label>
  )
}
