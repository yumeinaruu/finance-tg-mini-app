import { ChevronLeft, CircleAlert, type LucideIcon } from 'lucide-react'
import type { CSSProperties, ReactNode } from 'react'
import type { Categories } from '../categories'
import { CategoryIcon } from '../icons'
import type { CategoryType } from '../types'
import { useBackButton } from './Sheet'

export function Loader() {
  return <div className="loader" aria-label="Загрузка" />
}

export function ErrorBox({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="empty">
      <span className="empty__icon empty__icon--error">
        <CircleAlert size={26} />
      </span>
      <p>{message}</p>
      {onRetry && (
        <button className="button button--secondary button--inline" onClick={onRetry}>
          Повторить
        </button>
      )}
    </div>
  )
}

export function Empty({ icon: Icon, title, children }: { icon: LucideIcon; title: string; children?: ReactNode }) {
  return (
    <div className="empty">
      <span className="empty__icon">
        <Icon size={26} />
      </span>
      <p className="empty__title">{title}</p>
      {children}
    </div>
  )
}

export function Section({ title, action, children, flush }: { title?: string; action?: ReactNode; children: ReactNode; flush?: boolean }) {
  return (
    <section className="section">
      {(title || action) && (
        <div className="section__header">
          <h2>{title}</h2>
          {action}
        </div>
      )}
      <div className={flush ? 'section__body section__body--flush' : 'section__body'}>{children}</div>
    </section>
  )
}

export function ScreenTitle({ children }: { children: ReactNode }) {
  return <h1 className="screen-title">{children}</h1>
}

interface SegmentedProps<T extends string> {
  value: T
  options: { value: T; label: string }[]
  onChange: (value: T) => void
}

export function Segmented<T extends string>({ value, options, onChange }: SegmentedProps<T>) {
  const index = Math.max(options.findIndex((o) => o.value === value), 0)
  return (
    <div className="segmented" role="tablist" style={{ '--count': options.length, '--index': index } as CSSProperties}>
      <span className="segmented__thumb" aria-hidden="true" />
      {options.map((o) => (
        <button key={o.value} role="tab" aria-selected={o.value === value} className={o.value === value ? 'active' : ''} onClick={() => onChange(o.value)}>
          {o.label}
        </button>
      ))}
    </div>
  )
}

/** Grid of category tiles of one type; a hidden category stays visible when it is the current value. */
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
    <div className="tiles" role="radiogroup">
      {[...options, ...current].map((c) => (
        <button key={c.id} type="button" role="radio" aria-checked={c.id === value} className={c.id === value ? 'tile tile--active' : 'tile'} onClick={() => onChange(c.id)}>
          <CategoryIcon categories={categories} id={c.id} />
          <span className="tile__label">{c.name}</span>
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

/** Title of a nested page with an in-page back button; Telegram's native back button does the same. */
export function PageHeader({ title, onBack }: { title: string; onBack: () => void }) {
  useBackButton(true, onBack)
  return (
    <div className="page-header">
      <button className="icon-button" aria-label="Назад" onClick={onBack}>
        <ChevronLeft size={24} />
      </button>
      <h1 className="page-header__title">{title}</h1>
    </div>
  )
}
