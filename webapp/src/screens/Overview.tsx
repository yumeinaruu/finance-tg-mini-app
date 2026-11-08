import { ArrowDownRight, ArrowUpRight, ChartPie } from 'lucide-react'
import { api } from '../api'
import type { Categories } from '../categories'
import { DayBars, Progress, Ring, ShareBar } from '../components/Charts'
import { Empty, ErrorBox, Loader, Section } from '../components/Common'
import { MonthSwitcher } from '../components/MonthSwitcher'
import { addMonths, daysInMonth, monthGenitive, money, percent } from '../format'
import { CategoryIcon } from '../icons'
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
          <section className="hero">
            <Ring
              total={data.expense}
              slices={data.expenseByCategory.map((t) => ({ id: t.categoryId, value: t.total, color: categories.color(t.categoryId) }))}
              onSelect={onOpenCategory}
            >
              <span className="hero__amount">{money(data.expense)}</span>
              <span className="hero__caption">{base} потрачено</span>
              {data.previousExpense > 0 && <Trend current={data.expense} previous={data.previousExpense} month={addMonths(month, -1)} />}
            </Ring>
            <div className="hero__stats">
              <div>
                <span className="stat__label">Доходы</span>
                <span className={data.income > 0 ? 'stat__value amount--income' : 'stat__value'}>
                  {data.income > 0 ? '+' : ''}
                  {money(data.income)}
                </span>
              </div>
              <div>
                <span className="stat__label">Остаток</span>
                <span className={data.balance >= 0 ? 'stat__value' : 'stat__value amount--expense'}>
                  {data.balance >= 0 ? '' : '−'}
                  {money(data.balance)}
                </span>
              </div>
            </div>
          </section>

          {data.expenseByCategory.length === 0 ? (
            <Empty icon={ChartPie} title="В этом месяце расходов нет">
              <p className="empty__text">Напиши боту, например «кофе 5», или добавь операцию на вкладке «Операции».</p>
            </Empty>
          ) : (
            <Section title="Куда ушли деньги" flush>
              <ul className="list">
                {data.expenseByCategory.map((t) => (
                  <li key={t.categoryId}>
                    <button className="row row--button" onClick={() => onOpenCategory(t.categoryId)}>
                      <CategoryIcon categories={categories} id={t.categoryId} />
                      <span className="row__main">
                        <span className="row__line">
                          <span className="row__title">{categories.name(t.categoryId)}</span>
                          <span className="row__amount">{money(t.total)}</span>
                        </span>
                        <span className="row__line">
                          <ShareBar share={t.total / data.expense} color={categories.color(t.categoryId)} />
                          <span className="row__meta">{percent(t.total, data.expense)}%</span>
                        </span>
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            </Section>
          )}

          {data.expense > 0 && (
            <Section title="По дням">
              <DayBars values={dailyExpenses(month, data.days)} today={month === currentMonth ? Number(today.slice(8)) : undefined} />
            </Section>
          )}

          {data.budgets.length > 0 && (
            <Section
              title="Бюджеты"
              action={
                <button className="link-button" onClick={onOpenBudgets}>
                  Все бюджеты
                </button>
              }
            >
              <ul className="list list--spaced">
                {[...data.budgets]
                  .sort((a, b) => b.spent / b.limit - a.spent / a.limit)
                  .slice(0, 3)
                  .map((b) => (
                    <li key={b.categoryId} className="budget-mini">
                      <div className="budget-mini__top">
                        <span>{categories.name(b.categoryId)}</span>
                        <span className="row__meta">
                          {money(b.spent)} из {money(b.limit)}
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
  if (change === 0) return <span className="trend">как в {monthGenitive(month).replace(/я$/, 'е').replace(/а$/, 'е')}</span>
  const more = change > 0
  const Icon = more ? ArrowUpRight : ArrowDownRight
  // dative month name: октября -> октябрю, марта -> марту
  const monthName = monthGenitive(month).replace(/я$/, 'ю').replace(/а$/, 'у')
  return (
    <span className={more ? 'trend trend--up' : 'trend trend--down'}>
      <Icon size={14} strokeWidth={2.4} />
      {Math.abs(change)}% к {monthName}
    </span>
  )
}

function dailyExpenses(month: string, days: { date: string; expense: number }[]): number[] {
  const values = new Array(daysInMonth(month)).fill(0)
  days.forEach((d) => (values[Number(d.date.slice(8)) - 1] = d.expense))
  return values
}
