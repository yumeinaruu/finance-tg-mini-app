import { useEffect, useMemo, useState } from 'react'
import { api } from '../api'
import type { Categories } from '../categories'
import { Empty, ErrorBox, Loader, Segmented } from '../components/Common'
import { MonthSwitcher } from '../components/MonthSwitcher'
import { dayTitle, money, monthRange } from '../format'
import type { CategoryType, Meta, Operation } from '../types'
import { useAsync } from '../useAsync'
import { OperationForm } from './OperationForm'

interface Props {
  meta: Meta
  categories: Categories
  month: string
  onMonthChange: (month: string) => void
  categoryFilter: number | undefined
  onCategoryFilterChange: (id: number | undefined) => void
}

type TypeFilter = 'ALL' | CategoryType

export function OperationsScreen({ meta, categories, month, onMonthChange, categoryFilter, onCategoryFilterChange }: Props) {
  const [type, setType] = useState<TypeFilter>('ALL')
  const [currency, setCurrency] = useState('')
  const [search, setSearch] = useState('')
  const [query, setQuery] = useState('')
  const [editing, setEditing] = useState<Operation | 'new' | null>(null)

  useEffect(() => {
    const timer = window.setTimeout(() => setQuery(search.trim()), 300)
    return () => window.clearTimeout(timer)
  }, [search])

  const { from, to } = monthRange(month)
  const { data, error, loading, reload } = useAsync(
    () => api.operations(from, to, { categoryId: categoryFilter, type: type === 'ALL' ? undefined : type, currency: currency || undefined, q: query || undefined }),
    [from, to, categoryFilter, type, currency, query],
  )

  const groups = useMemo(() => {
    const byDate = new Map<string, Operation[]>()
    ;[...(data ?? [])].reverse().forEach((op) => byDate.set(op.date, [...(byDate.get(op.date) ?? []), op]))
    return [...byDate.entries()]
  }, [data])

  const filtersActive = categoryFilter !== undefined || type !== 'ALL' || currency !== '' || query !== ''

  return (
    <div className="screen">
      <MonthSwitcher month={month} onChange={onMonthChange} max={meta.today.slice(0, 7)} />
      <div className="filters">
        <Segmented
          value={type}
          onChange={setType}
          options={[
            { value: 'ALL', label: 'Все' },
            { value: 'EXPENSE', label: 'Расходы' },
            { value: 'INCOME', label: 'Доходы' },
          ]}
        />
        <div className="form-row">
          <select className="input" value={categoryFilter ?? ''} onChange={(e) => onCategoryFilterChange(e.target.value ? Number(e.target.value) : undefined)}>
            <option value="">Все категории</option>
            {categories.all.map((c) => (
              <option key={c.id} value={c.id}>
                {c.emoji} {c.name}
                {c.archived ? ' (скрыта)' : ''}
              </option>
            ))}
          </select>
          <select className="input input--narrow" value={currency} onChange={(e) => setCurrency(e.target.value)}>
            <option value="">Валюта</option>
            {meta.currencies.map((c) => (
              <option key={c}>{c}</option>
            ))}
          </select>
        </div>
        <input className="input" type="search" placeholder="Поиск по заметке" value={search} onChange={(e) => setSearch(e.target.value)} />
      </div>

      {loading && !data && <Loader />}
      {error && <ErrorBox message={error} onRetry={reload} />}
      {data && data.length === 0 && <Empty icon="🧾">{filtersActive ? 'Ничего не нашлось' : 'В этом месяце операций нет'}</Empty>}

      {groups.map(([date, ops]) => {
        const expense = ops.filter((o) => o.type === 'EXPENSE').reduce((s, o) => s + o.amountBase, 0)
        return (
          <section key={date} className="section">
            <div className="section__header">
              <span>{dayTitle(date, meta.today)}</span>
              {expense > 0 && <span className="row__hint">−{money(expense)}</span>}
            </div>
            <ul className="section__body list">
              {ops.map((op) => (
                <li key={op.id} className="row row--tappable" onClick={() => setEditing(op)}>
                  <span className="row__emoji">{categories.get(op.categoryId).emoji}</span>
                  <span className="row__main">
                    <span>{op.note ?? categories.get(op.categoryId).name}</span>
                    <span className="row__sub">
                      {categories.get(op.categoryId).name}
                      {op.recurringId ? ' · 🔁' : ''}
                    </span>
                  </span>
                  <span className="row__right">
                    <span className={op.type === 'INCOME' ? 'row__amount amount--income' : 'row__amount'}>
                      {op.type === 'INCOME' ? '+' : '−'}
                      {money(op.amount)} {op.currency !== meta.baseCurrency ? op.currency : ''}
                    </span>
                    {op.currency !== meta.baseCurrency && (
                      <span className="row__sub">
                        ≈ {money(op.amountBase)} {meta.baseCurrency}
                      </span>
                    )}
                  </span>
                </li>
              ))}
            </ul>
          </section>
        )
      })}

      <button className="fab" aria-label="Добавить операцию" onClick={() => setEditing('new')}>
        +
      </button>

      {editing && (
        <OperationForm
          meta={meta}
          categories={categories}
          operation={editing === 'new' ? null : editing}
          defaultDate={month === meta.today.slice(0, 7) ? meta.today : to}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            reload()
          }}
        />
      )}
    </div>
  )
}
