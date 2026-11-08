import { api } from '../api'
import type { Categories } from '../categories'
import { DayBars, Donut, Progress } from '../components/Charts'
import { Empty, ErrorBox, Loader, Section } from '../components/Common'
import { MonthSwitcher } from '../components/MonthSwitcher'
import { addMonths, daysInMonth, monthGenitive, money, percent } from '../format'
import { useAsync } from '../useAsync'

interface Props {
  categories: Categories
  base: string
  today: string
  month: string
  onMonthChange: (month: string) => void
  onOpenCategory: (categoryId: number) => void
  onOpenBudgets: () => void
}

export function OverviewScreen({ categories, base, today, month, onMonthChange, onOpenCategory, onOpenBudgets }: Props) {
  const { data, error, loading, reload } = useAsync(() => api.overview(month), [month])
  const currentMonth = today.slice(0, 7)

  return (
    <div className="screen">
      <MonthSwitcher month={month} onChange={onMonthChange} max={currentMonth} />
      {loading && !data && <Loader />}
      {error && <ErrorBox message={error} onRetry={reload} />}
      {data && (
        <>
          <div className="cards">
            <div className="card">
              <div className="card__label">Расходы</div>
              <div className="card__value">−{money(data.expense)}</div>
              {data.previousExpense > 0 && <Trend current={data.expense} previous={data.previousExpense} month={addMonths(month, -1)} />}
            </div>
            <div className="card">
              <div className="card__label">Доходы</div>
              <div className="card__value card__value--income">+{money(data.income)}</div>
            </div>
            <div className="card card--wide">
              <div className="card__label">Баланс месяца, {base}</div>
              <div className={data.balance >= 0 ? 'card__value card__value--income' : 'card__value card__value--expense'}>
                {data.balance >= 0 ? '+' : '−'}
                {money(data.balance)}
              </div>
            </div>
          </div>

          <Section title="Расходы по категориям">
            {data.expenseByCategory.length === 0 ? (
              <Empty icon="🫙">В этом месяце расходов нет</Empty>
            ) : (
              <>
                <Donut
                  total={data.expense}
                  slices={data.expenseByCategory.map((t) => ({ id: t.categoryId, value: t.total, color: categories.color(t.categoryId) }))}
                  onSelect={onOpenCategory}
                />
                <ul className="list">
                  {data.expenseByCategory.map((t) => (
                    <li key={t.categoryId} className="row row--tappable" onClick={() => onOpenCategory(t.categoryId)}>
                      <span className="dot" style={{ background: categories.color(t.categoryId) }} />
                      <span className="row__main">{categories.label(t.categoryId)}</span>
                      <span className="row__hint">{percent(t.total, data.expense)}%</span>
                      <span className="row__amount">{money(t.total)}</span>
                    </li>
                  ))}
                </ul>
              </>
            )}
          </Section>

          {data.expense > 0 && (
            <Section title="По дням">
              <DayBars values={dailyExpenses(month, data.days)} highlight={month === currentMonth ? Number(today.slice(8)) : undefined} />
            </Section>
          )}

          {data.budgets.length > 0 && (
            <Section
              title="Бюджеты"
              action={
                <button className="link-button" onClick={onOpenBudgets}>
                  Все →
                </button>
              }
            >
              <ul className="list">
                {[...data.budgets]
                  .sort((a, b) => b.spent / b.limit - a.spent / a.limit)
                  .slice(0, 3)
                  .map((b) => (
                    <li key={b.categoryId} className="budget-row">
                      <div className="budget-row__top">
                        <span>{categories.label(b.categoryId)}</span>
                        <span className="row__hint">
                          {money(b.spent)} / {money(b.limit)}
                        </span>
                      </div>
                      <Progress value={b.spent} limit={b.limit} />
                    </li>
                  ))}
              </ul>
            </Section>
          )}
        </>
      )}
    </div>
  )
}

function Trend({ current, previous, month }: { current: number; previous: number; month: string }) {
  const change = Math.round(((current - previous) * 100) / previous)
  const up = change > 0
  return (
    <div className={up ? 'trend trend--up' : 'trend trend--down'}>
      {up ? '▲' : '▼'} {Math.abs(change)}% к {monthGenitive(month).replace(/я$/, 'ю').replace(/а$/, 'у')}
    </div>
  )
}

function dailyExpenses(month: string, days: { date: string; expense: number }[]): number[] {
  const values = new Array(daysInMonth(month)).fill(0)
  days.forEach((d) => (values[Number(d.date.slice(8)) - 1] = d.expense))
  return values
}
