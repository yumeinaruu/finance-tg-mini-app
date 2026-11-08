import { useState } from 'react'
import { api } from '../api'
import type { Categories } from '../categories'
import { Progress } from '../components/Charts'
import { Empty, ErrorBox, Field, Loader, Section } from '../components/Common'
import { MonthSwitcher } from '../components/MonthSwitcher'
import { Sheet } from '../components/Sheet'
import { useToast } from '../components/Toast'
import { amountInput, money, parseAmount, percent, plural } from '../format'
import { confirmAction, haptic } from '../telegram'
import type { Budget } from '../types'
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
        <Empty icon="🎯">
          На этот месяц бюджетов нет
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
          <div className="card card--wide">
            <div className="card__label">Потрачено из бюджетов, {base}</div>
            <div className="card__value">
              {money(totalSpent)} <span className="card__of">/ {money(totalLimit)}</span>
            </div>
            <Progress value={totalSpent} limit={totalLimit} />
          </div>
          <Section>
            <ul className="list">
              {data.map((b) => (
                <li key={b.categoryId} className="budget-row row--tappable" onClick={() => setEditing(b)}>
                  <div className="budget-row__top">
                    <span>{categories.label(b.categoryId)}</span>
                    <span className="row__hint">{percent(b.spent, b.limit)}%</span>
                  </div>
                  <Progress value={b.spent} limit={b.limit} />
                  <div className="budget-row__bottom">
                    <span>
                      {money(b.spent)} из {money(b.limit)}
                    </span>
                    <span className={b.spent > b.limit ? 'amount--expense' : ''}>
                      {b.spent > b.limit ? `перерасход ${money(b.spent - b.limit)}` : `осталось ${money(b.limit - b.spent)}`}
                    </span>
                  </div>
                  {b.forecast !== null && b.forecast > b.spent && (
                    <div className={b.forecast > b.limit ? 'hint hint--warn' : 'hint'}>
                      Прогноз к концу месяца ≈ {money(b.forecast)}
                    </div>
                  )}
                </li>
              ))}
            </ul>
          </Section>
          <div className="actions">
            {free.length > 0 && (
              <button className="button" onClick={() => setEditing('new')}>
                Добавить бюджет
              </button>
            )}
            <button className="button button--secondary" onClick={copy}>
              Скопировать с прошлого месяца
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
      {today.slice(0, 7) !== month && <div className="hint hint--center">Бот предупреждает о лимитах только для текущего месяца</div>}
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
    <Sheet title={budget ? categories.label(budget.categoryId) : 'Новый бюджет'} onClose={onClose}>
      {!budget && (
        <Field label="Категория">
          <div className="chips">
            {free.map((id) => (
              <button key={id} type="button" className={id === categoryId ? 'chip chip--active' : 'chip'} onClick={() => setCategoryId(id)}>
                {categories.label(id)}
              </button>
            ))}
          </div>
        </Field>
      )}
      <Field label="Лимит на месяц">
        <input className="input input--amount" inputMode="decimal" placeholder="600" value={limit} onChange={(e) => setLimit(e.target.value)} autoFocus />
      </Field>
      {error && <div className="form-error">{error}</div>}
      <button className="button" disabled={busy} onClick={save}>
        Сохранить
      </button>
      {budget && (
        <button className="button button--destructive" disabled={busy} onClick={remove}>
          Убрать бюджет
        </button>
      )}
    </Sheet>
  )
}
