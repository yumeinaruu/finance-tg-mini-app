import { Copy, Plus, Target, TrendingUp } from 'lucide-react'
import { useState } from 'react'
import { api } from '../api'
import type { Categories } from '../categories'
import { Progress } from '../components/Charts'
import { Empty, ErrorBox, Loader, Section } from '../components/Common'
import { MonthSwitcher } from '../components/MonthSwitcher'
import { Sheet } from '../components/Sheet'
import { useToast } from '../components/Toast'
import { amountInput, money, parseAmount, percent, plural } from '../format'
import { confirmAction, haptic } from '../telegram'
import type { Budget } from '../types'
import { CategoryIcon } from '../icons'
import { useAsync } from '../useAsync'

interface Props {
  categories: Categories
  base: string
  today: string
  month: string
  onMonthChange: (month: string) => void
}

export function BudgetsScreen({ categories, base, today, month, onMonthChange }: Props) {
  const toast = useToast()
  const { data, error, loading, reload } = useAsync(() => api.budgets(month), [month])
  const [editing, setEditing] = useState<Budget | 'new' | null>(null)

  const copy = async () => {
    try {
      const { count } = await api.copyBudgets(month)
      toast(count > 0 ? `Скопировано: ${count} ${plural(count, 'бюджет', 'бюджета', 'бюджетов')}` : 'Нечего копировать')
      reload()
    } catch (e) {
      toast((e as Error).message, 'error')
    }
  }

  const totalLimit = data?.reduce((s, b) => s + b.limit, 0) ?? 0
  const totalSpent = data?.reduce((s, b) => s + b.spent, 0) ?? 0
  const free = categories.active('EXPENSE').filter((c) => !data?.some((b) => b.categoryId === c.id))

  return (
    <div className="screen">
      <MonthSwitcher month={month} onChange={onMonthChange} />
      {loading && !data && <Loader />}
      {error && <ErrorBox message={error} onRetry={reload} />}
      {data && data.length === 0 && (
        <Empty icon={Target} title="На этот месяц бюджетов нет">
          <p className="empty__text">Задай лимит на категорию, и бот предупредит, когда потратишь 80% и 100%.</p>
          <div className="empty__actions">
            <button className="button" onClick={() => setEditing('new')}>
              Добавить бюджет
            </button>
            <button className="button button--secondary" onClick={copy}>
              Скопировать с прошлого месяца
            </button>
          </div>
        </Empty>
      )}
      {data && data.length > 0 && (
        <>
          <section className="summary">
            <div className="summary__line">
              <span className="summary__amount">{money(Math.max(totalLimit - totalSpent, 0))}</span>
              <span className="summary__caption">{base} осталось из {money(totalLimit)}</span>
            </div>
            <Progress value={totalSpent} limit={totalLimit} />
          </section>
          <Section flush>
            <ul className="list">
              {data.map((b) => (
                <li key={b.categoryId}>
                  <button className="row row--button row--top" onClick={() => setEditing(b)}>
                    <CategoryIcon categories={categories} id={b.categoryId} />
                    <span className="row__main row__main--stack">
                      <span className="row__line">
                        <span className="row__title">{categories.name(b.categoryId)}</span>
                        <span className="row__meta">{percent(b.spent, b.limit)}%</span>
                      </span>
                      <Progress value={b.spent} limit={b.limit} />
                      <span className="row__line">
                        <span className="row__meta">
                          {money(b.spent)} из {money(b.limit)}
                        </span>
                        <span className={b.spent > b.limit ? 'row__meta amount--expense' : 'row__meta'}>
                          {b.spent > b.limit ? `перерасход ${money(b.spent - b.limit)}` : `осталось ${money(b.limit - b.spent)}`}
                        </span>
                      </span>
                      {b.forecast !== null && b.forecast > b.spent && (
                        <span className={b.forecast > b.limit ? 'row__meta row__meta--icon amount--warn' : 'row__meta row__meta--icon'}>
                          <TrendingUp size={13} /> к концу месяца ≈ {money(b.forecast)}
                        </span>
                      )}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </Section>
          <div className="actions">
            {free.length > 0 && (
              <button className="button button--secondary button--icon" onClick={() => setEditing('new')}>
                <Plus size={18} /> Добавить бюджет
              </button>
            )}
            <button className="button button--plain button--icon" onClick={copy}>
              <Copy size={16} /> Скопировать с прошлого месяца
            </button>
          </div>
        </>
      )}
      {editing && (
        <BudgetForm
          categories={categories}
          month={month}
          budget={editing === 'new' ? null : editing}
          free={free.map((c) => c.id)}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null)
            reload()
          }}
        />
      )}
      {today.slice(0, 7) !== month && data && data.length > 0 && <p className="hint hint--center">Прогноз считается только для текущего месяца</p>}
    </div>
  )
}

function BudgetForm({
  categories,
  month,
  budget,
  free,
  onClose,
  onSaved,
}: {
  categories: Categories
  month: string
  budget: Budget | null
  free: number[]
  onClose: () => void
  onSaved: () => void
}) {
  const toast = useToast()
  const [categoryId, setCategoryId] = useState<number | null>(budget?.categoryId ?? free[0] ?? null)
  const [limit, setLimit] = useState(budget ? amountInput(budget.limit) : '')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const save = async () => {
    const minor = parseAmount(limit)
    if (categoryId === null) return setError('Выбери категорию')
    if (minor === null) return setError('Введи лимит, например 600')
    setBusy(true)
    try {
      await api.setBudget(month, categoryId, minor)
      haptic('success')
      toast('Бюджет сохранён')
      onSaved()
    } catch (e) {
      setError((e as Error).message)
      setBusy(false)
    }
  }

  const remove = async () => {
    if (!budget || !(await confirmAction('Убрать бюджет?'))) return
    setBusy(true)
    try {
      await api.deleteBudget(month, budget.categoryId)
      toast('Бюджет убран')
      onSaved()
    } catch (e) {
      setError((e as Error).message)
      setBusy(false)
    }
  }

  return (
    <Sheet title={budget ? `Бюджет: ${categories.name(budget.categoryId)}` : 'Новый бюджет'} onClose={onClose}>
      <div className="amount-field">
        <input className="amount-field__input" inputMode="decimal" placeholder="0" aria-label="Лимит на месяц" value={limit} onChange={(e) => setLimit(e.target.value)} autoFocus />
        <span className="amount-field__suffix">в месяц</span>
      </div>
      {!budget && (
        <div className="tiles" role="radiogroup" aria-label="Категория">
          {free.map((id) => (
            <button key={id} type="button" role="radio" aria-checked={id === categoryId} className={id === categoryId ? 'tile tile--active' : 'tile'} onClick={() => setCategoryId(id)}>
              <CategoryIcon categories={categories} id={id} />
              <span className="tile__label">{categories.name(id)}</span>
            </button>
          ))}
        </div>
      )}
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      <div className="sheet__actions">
        <button className="button" disabled={busy} onClick={save}>
          Сохранить
        </button>
        {budget && (
          <button className="button button--destructive" disabled={busy} onClick={remove}>
            Убрать бюджет
          </button>
        )}
      </div>
    </Sheet>
  )
}
