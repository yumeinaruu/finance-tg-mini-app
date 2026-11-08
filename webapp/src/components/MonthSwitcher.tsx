import { ChevronLeft, ChevronRight } from 'lucide-react'
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
        <ChevronLeft size={22} />
      </button>
      <h1 className="month-switcher__title">{monthTitle(month)}</h1>
      <button className="icon-button" aria-label="Следующий месяц" disabled={max !== undefined && next > max} onClick={() => onChange(next)}>
        <ChevronRight size={22} />
      </button>
    </div>
  )
}
