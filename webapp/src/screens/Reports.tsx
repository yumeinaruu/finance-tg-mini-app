import { useMemo, useState } from 'react'
import { api } from '../api'
import type { Categories } from '../categories'
import { LineChart } from '../components/Charts'
import { ErrorBox, Loader, PageHeader, Section } from '../components/Common'
import { money, monthShort, monthTitle } from '../format'
import { CategoryIcon } from '../icons'
import type { Meta, MonthReport } from '../types'
import { useAsync } from '../useAsync'

export function ReportsScreen({ categories, meta, onBack }: { categories: Categories; meta: Meta; onBack: () => void }) {
  const { data, error, loading, reload } = useAsync(() => api.monthly(12), [])
  const base = meta.baseCurrency

  return (
    <div className="screen">
      <PageHeader title="Отчёты" onBack={onBack} />
      {loading && !data && <Loader />}
      {error && <ErrorBox message={error} onRetry={reload} />}
      {data && (
        <>
          <Section title={`Доходы и расходы за год, ${base}`}>
            <LineChart
              labels={data.map((m) => monthShort(m.month))}
              series={[
                { name: 'Доходы', color: 'var(--income)', values: data.map((m) => m.income) },
                { name: 'Расходы', color: 'var(--expense)', values: data.map((m) => m.expense) },
              ]}
            />
            <div className="legend">
              <span>
                <span className="dot" style={{ background: 'var(--income)' }} /> Доходы
              </span>
              <span>
                <span className="dot" style={{ background: 'var(--expense)' }} /> Расходы
              </span>
            </div>
          </Section>
          <Compare categories={categories} months={data} />
          <Averages categories={categories} months={data} />
        </>
      )}
    </div>
  )
}

function Compare({ categories, months }: { categories: Categories; months: MonthReport[] }) {
  const [a, setA] = useState(months[months.length - 2]?.month ?? months[0].month)
  const [b, setB] = useState(months[months.length - 1].month)
  const first = months.find((m) => m.month === a)
  const second = months.find((m) => m.month === b)

  const rows = useMemo(() => {
    const ids = new Set([...(first?.expenseByCategory ?? []), ...(second?.expenseByCategory ?? [])].map((t) => t.categoryId))
    const value = (m: MonthReport | undefined, id: number) => m?.expenseByCategory.find((t) => t.categoryId === id)?.total ?? 0
    return [...ids]
      .map((id) => ({ id, a: value(first, id), b: value(second, id) }))
      .sort((x, y) => Math.max(y.a, y.b) - Math.max(x.a, x.b))
  }, [first, second])

  const select = (value: string, onChange: (v: string) => void) => (
    <select className="input" value={value} onChange={(e) => onChange(e.target.value)}>
      {months.map((m) => (
        <option key={m.month} value={m.month}>
          {monthTitle(m.month)}
        </option>
      ))}
    </select>
  )

  return (
    <Section title="Сравнение месяцев">
      <div className="form-row">
        {select(a, setA)}
        {select(b, setB)}
      </div>
      {rows.length === 0 ? (
        <div className="hint hint--center">Расходов нет</div>
      ) : (
        <table className="compare">
          <thead>
            <tr>
              <th />
              <th>{monthShort(a)}</th>
              <th>{monthShort(b)}</th>
              <th>Δ</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id}>
                <td>{categories.name(r.id)}</td>
                <td>{money(r.a)}</td>
                <td>{money(r.b)}</td>
                <Diff value={r.b - r.a} />
              </tr>
            ))}
            <tr className="compare__total">
              <td>Итого</td>
              <td>{money(first?.expense ?? 0)}</td>
              <td>{money(second?.expense ?? 0)}</td>
              <Diff value={(second?.expense ?? 0) - (first?.expense ?? 0)} />
            </tr>
          </tbody>
        </table>
      )}
    </Section>
  )
}

/** Expense difference: growth is red, savings green. */
function Diff({ value }: { value: number }) {
  return (
    <td className={value > 0 ? 'amount--expense' : value < 0 ? 'amount--income' : ''}>
      {value === 0 ? '—' : `${value > 0 ? '+' : '−'}${money(value)}`}
    </td>
  )
}

function Averages({ categories, months }: { categories: Categories; months: MonthReport[] }) {
  const active = months.filter((m) => m.expense > 0)
  if (active.length === 0) return null
  const totals = new Map<number, number>()
  active.forEach((m) => m.expenseByCategory.forEach((t) => totals.set(t.categoryId, (totals.get(t.categoryId) ?? 0) + t.total)))
  const rows = [...totals.entries()].map(([id, total]) => ({ id, avg: Math.round(total / active.length) })).sort((x, y) => y.avg - x.avg)
  return (
    <Section title={`В среднем за месяц, по ${active.length} мес.`} flush>
      <ul className="list list--compact">
        {rows.map((r) => (
          <li key={r.id} className="row">
            <CategoryIcon categories={categories} id={r.id} size="sm" />
            <span className="row__main">
              <span className="row__line">
                <span className="row__title">{categories.name(r.id)}</span>
                <span className="row__amount">{money(r.avg)}</span>
              </span>
            </span>
          </li>
        ))}
      </ul>
    </Section>
  )
}
