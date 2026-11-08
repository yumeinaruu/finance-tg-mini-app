import { Send } from 'lucide-react'
import { useMemo, useState } from 'react'
import { api } from '../api'
import type { Categories } from '../categories'
import { LineChart } from '../components/Charts'
import { ErrorBox, Field, Loader, ScreenTitle, Section } from '../components/Common'
import { useToast } from '../components/Toast'
import { money, monthRange, monthShort, monthTitle } from '../format'
import { haptic } from '../telegram'
import { CategoryIcon } from '../icons'
import type { Meta, MonthReport } from '../types'
import { useAsync } from '../useAsync'

export function ReportsScreen({ categories, meta }: { categories: Categories; meta: Meta }) {
  const { data, error, loading, reload } = useAsync(() => api.monthly(12), [])
  const base = meta.baseCurrency
  const today = meta.today

  return (
    <div className="screen">
      <ScreenTitle>Отчёты</ScreenTitle>
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
      <Export today={today} />
      <Section title="Настройки">
        <dl className="settings">
          <div>
            <dt>Основная валюта</dt>
            <dd>{base}</dd>
          </div>
          <div>
            <dt>Валюты</dt>
            <dd>{meta.currencies.join(', ')}</dd>
          </div>
        </dl>
        <p className="hint">Категории и слова для них настраиваются в чате с ботом: /categories, /cat, /alias.</p>
      </Section>
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

function Export({ today }: { today: string }) {
  const toast = useToast()
  const { from: monthStart } = monthRange(today.slice(0, 7))
  const [from, setFrom] = useState(monthStart)
  const [to, setTo] = useState(today)
  const [busy, setBusy] = useState(false)

  const send = async () => {
    setBusy(true)
    try {
      await api.exportCsv(from, to)
      haptic('success')
      toast('Файл отправлен в чат с ботом')
    } catch (e) {
      toast((e as Error).message, 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Section title="Выгрузка в CSV">
      <div className="form-row">
        <Field label="С">
          <input className="input" type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} />
        </Field>
        <Field label="По">
          <input className="input" type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} />
        </Field>
      </div>
      <button className="button button--secondary button--icon" disabled={busy || !from || !to} onClick={send}>
        <Send size={17} /> Отправить файл в чат
      </button>
    </Section>
  )
}
