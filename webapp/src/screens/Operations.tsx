import { Plus, ReceiptText, Repeat, Search, SearchX } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { api } from '../api'
import type { Categories } from '../categories'
import { Empty, ErrorBox, Loader, Segmented } from '../components/Common'
import { MonthSwitcher } from '../components/MonthSwitcher'
import { dayTitle, money, monthRange } from '../format'
import { CategoryIcon } from '../icons'
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
        <div className="search">
          <Search size={17} className="search__icon" aria-hidden="true" />
          <input className="input search__input" type="search" placeholder="Поиск по заметке" value={search} onChange={(e) => setSearch(e.target.value)} />
        </div>
        <div className="form-row">
          <select
            className="input input--compact"
            aria-label="Категория"
            value={categoryFilter ?? ''}
            onChange={(e) => onCategoryFilterChange(e.target.value ? Number(e.target.value) : undefined)}
          >
            <option value="">Все категории</option>
            {categories.all.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
                {c.archived ? ' (скрыта)' : ''}
              </option>
            ))}
          </select>
          <select className="input input--compact input--narrow" aria-label="Валюта" value={currency} onChange={(e) => setCurrency(e.target.value)}>
            <option value="">Все валюты</option>
            {meta.currencies.map((c) => (
              <option key={c}>{c}</option>
            ))}
          </select>
        </div>
      </div>

      {loading && !data && <Loader />}
      {error && <ErrorBox message={error} onRetry={reload} />}
      {data && data.length === 0 &&
        (filtersActive ? (
          <Empty icon={SearchX} title="Ничего не нашлось">
            <p className="empty__text">Попробуй убрать фильтры или поменять месяц.</p>
          </Empty>
        ) : (
          <Empty icon={ReceiptText} title="В этом месяце операций нет">
            <button className="button button--inline" onClick={() => setEditing('new')}>
              Добавить операцию
            </button>
          </Empty>
        ))}

      {groups.map(([date, ops]) => {
        const expense = ops.filter((o) => o.type === 'EXPENSE').reduce((s, o) => s + o.amountBase, 0)
        return (
          <section key={date} className="section">
            <div className="section__header">
              <h2>{dayTitle(date, meta.today)}</h2>
              {expense > 0 && <span className="row__meta">−{money(expense)}</span>}
            </div>
            <ul className="section__body section__body--flush list">
              {ops.map((op) => (
                <li key={op.id}>
                  <button className="row row--button" onClick={() => setEditing(op)}>
                    <CategoryIcon categories={categories} id={op.categoryId} />
                    <span className="row__main">
                      <span className="row__line">
                        <span className="row__title">{op.note ?? categories.name(op.categoryId)}</span>
                        <span className={op.type === 'INCOME' ? 'row__amount amount--income' : 'row__amount'}>
                          {op.type === 'INCOME' ? '+' : '−'}
                          {money(op.amount)}
                          {op.currency !== meta.baseCurrency && <span className="row__currency"> {op.currency}</span>}
                        </span>
                      </span>
                      <span className="row__line">
                        <span className="row__meta row__meta--icon">
                          {op.recurringId && <Repeat size={12} aria-label="Регулярный платёж" />}
                          {categories.name(op.categoryId)}
                        </span>
                        {op.currency !== meta.baseCurrency && (
                          <span className="row__meta">
                            {money(op.amountBase)} {meta.baseCurrency}
                          </span>
                        )}
                      </span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </section>
        )
      })}

      <button className="fab" aria-label="Добавить операцию" onClick={() => setEditing('new')}>
        <Plus size={26} strokeWidth={2.4} />
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
