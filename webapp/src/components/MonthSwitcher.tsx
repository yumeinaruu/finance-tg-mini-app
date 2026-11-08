import { addMonths, monthTitle } from '../format'

interface Props {
  month: string
  onChange: (month: string) => void
  /** Latest selectable month (yyyy-MM). */
  max?: string
}

export function MonthSwitcher({ month, onChange, max }: Props) {
  const next = addMonths(month, 1)
  return (
    <div className="month-switcher">
      <button className="icon-button" aria-label="Предыдущий месяц" onClick={() => onChange(addMonths(month, -1))}>
        ‹
      </button>
      <span className="month-switcher__title">{monthTitle(month)}</span>
      <button
        className="icon-button"
        aria-label="Следующий месяц"
        disabled={max !== undefined && next > max}
        onClick={() => onChange(next)}
      >
        ›
      </button>
    </div>
  )
}
