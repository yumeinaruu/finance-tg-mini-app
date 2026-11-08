import { useState } from 'react'
import { api } from '../api'
import type { Categories } from '../categories'
import { CategoryPicker, Empty, ErrorBox, Field, Loader, Section, Segmented } from '../components/Common'
import { Sheet } from '../components/Sheet'
import { useToast } from '../components/Toast'
import { MONTHS, WEEKDAYS, amountInput, money, parseAmount, shortDate } from '../format'
import { confirmAction, haptic } from '../telegram'
import type { CategoryType, Meta, Recurring, ScheduleKind } from '../types'
import { useAsync } from '../useAsync'

export function RecurringScreen({ meta, categories }: { meta: Meta; categories: Categories }) {
  const { data, error, loading, reload } = useAsync(() => api.recurring(), [])
  const [editing, setEditing] = useState<Recurring | 'new' | null>(null)

  const monthlyTotal = (data ?? [])
    .filter((r) => !r.paused && r.schedule.kind === 'monthly' && categories.get(r.categoryId).type === 'EXPENSE' && r.currency === meta.baseCurrency)
    .reduce((s, r) => s + r.amount, 0)

  return (
    <div className="screen">
      {loading && !data && <Loader />}
      {error && <ErrorBox message={error} onRetry={reload} />}
      {data && data.length === 0 && (
        <Empty icon="🔁">
          Регулярных платежей нет.
          <br />
          Бот сам добавит их в нужный день и пришлёт уведомление.
        </Empty>
      )}
      {data && data.length > 0 && (
        <>
          {monthlyTotal > 0 && (
            <div className="hint hint--center">
              Ежемесячные расходы в {meta.baseCurrency}: {money(monthlyTotal)}
            </div>
          )}
          <Section>
            <ul className="list">
              {data.map((r) => (
                <li key={r.id} className={r.paused ? 'row row--tappable row--muted' : 'row row--tappable'} onClick={() => setEditing(r)}>
                  <span className="row__emoji">{categories.get(r.categoryId).emoji}</span>
                  <span className="row__main">
                    <span>{r.name}</span>
                    <span className="row__sub">{r.description}</span>
                  </span>
                  <span className="row__right">
                    <span className={categories.get(r.categoryId).type === 'INCOME' ? 'row__amount amount--income' : 'row__amount'}>
                      {money(r.amount)} {r.currency}
                    </span>
                    <span className="row__sub">{r.paused ? '⏸ на паузе' : shortDate(r.nextRun, meta.today)}</span>
                  </span>
                </li>
              ))}
            </ul>
          </Section>
        </>
      )}
      <button className="fab" aria-label="Добавить регулярный платёж" onClick={() => setEditing('new')}>
        +
      </button>
      {editing && (
        <RecurringForm
          meta={meta}
          categories={categories}
          payment={editing === 'new' ? null : editing}
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

function RecurringForm({
  meta,
  categories,
  payment,
  onClose,
  onSaved,
}: {
  meta: Meta
  categories: Categories
  payment: Recurring | null
  onClose: () => void
  onSaved: () => void
}) {
  const toast = useToast()
  const [type, setType] = useState<CategoryType>(payment ? categories.get(payment.categoryId).type : 'EXPENSE')
  const [name, setName] = useState(payment?.name ?? '')
  const [amount, setAmount] = useState(payment ? amountInput(payment.amount) : '')
  const [currency, setCurrency] = useState(payment?.currency ?? meta.baseCurrency)
  const [categoryId, setCategoryId] = useState<number | null>(payment?.categoryId ?? null)
  const [kind, setKind] = useState<ScheduleKind>(payment?.schedule.kind ?? 'monthly')
  const [day, setDay] = useState(payment?.schedule.day ?? 1)
  const [weekday, setWeekday] = useState(payment?.schedule.weekday ?? 1)
  const [month, setMonth] = useState(payment?.schedule.month ?? 1)
  const [paused, setPaused] = useState(payment?.paused ?? false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const save = async () => {
    const minor = parseAmount(amount)
    if (!name.trim()) return setError('Укажи название')
    if (minor === null) return setError('Введи сумму')
    if (categoryId === null) return setError('Выбери категорию')
    const schedule =
      kind === 'monthly' ? { kind, day } : kind === 'weekly' ? { kind, weekday } : { kind, day, month }
    setBusy(true)
    try {
      const input = { name: name.trim(), categoryId, amount: minor, currency, schedule, paused }
      if (payment) await api.updateRecurring(payment.id, input)
      else await api.createRecurring(input)
      haptic('success')
      toast('Сохранено')
      onSaved()
    } catch (e) {
      setError((e as Error).message)
      setBusy(false)
    }
  }

  const remove = async () => {
    if (!payment || !(await confirmAction(`Удалить «${payment.name}»? Уже добавленные операции останутся.`))) return
    setBusy(true)
    try {
      await api.deleteRecurring(payment.id)
      toast('Удалено')
      onSaved()
    } catch (e) {
      setError((e as Error).message)
      setBusy(false)
    }
  }

  return (
    <Sheet title={payment ? payment.name : 'Регулярный платёж'} onClose={onClose}>
      <Segmented
        value={type}
        onChange={(next) => {
          setType(next)
          setCategoryId(null)
        }}
        options={[
          { value: 'EXPENSE', label: 'Расход' },
          { value: 'INCOME', label: 'Доход' },
        ]}
      />
      <Field label="Название">
        <input className="input" placeholder="Netflix, аренда…" maxLength={200} value={name} onChange={(e) => setName(e.target.value)} />
      </Field>
      <div className="form-row">
        <Field label="Сумма">
          <input className="input input--amount" inputMode="decimal" placeholder="0.00" value={amount} onChange={(e) => setAmount(e.target.value)} />
        </Field>
        <Field label="Валюта">
          <select className="input" value={currency} onChange={(e) => setCurrency(e.target.value)}>
            {meta.currencies.map((c) => (
              <option key={c}>{c}</option>
            ))}
          </select>
        </Field>
      </div>
      <Field label="Категория">
        <CategoryPicker categories={categories} type={type} value={categoryId} onChange={setCategoryId} />
      </Field>
      <Field label="Когда">
        <Segmented
          value={kind}
          onChange={setKind}
          options={[
            { value: 'monthly', label: 'Каждый месяц' },
            { value: 'weekly', label: 'Неделя' },
            { value: 'yearly', label: 'Год' },
          ]}
        />
      </Field>
      <div className="form-row">
        {kind === 'weekly' ? (
          <select className="input" value={weekday} onChange={(e) => setWeekday(Number(e.target.value))}>
            {WEEKDAYS.map((w, i) => (
              <option key={w} value={i + 1}>
                {w}
              </option>
            ))}
          </select>
        ) : (
          <select className="input" value={day} onChange={(e) => setDay(Number(e.target.value))}>
            {Array.from({ length: 31 }, (_, i) => i + 1).map((d) => (
              <option key={d} value={d}>
                {d}-го
              </option>
            ))}
          </select>
        )}
        {kind === 'yearly' && (
          <select className="input" value={month} onChange={(e) => setMonth(Number(e.target.value))}>
            {MONTHS.map((m, i) => (
              <option key={m} value={i + 1}>
                {m}
              </option>
            ))}
          </select>
        )}
      </div>
      {kind === 'monthly' && day > 28 && <div className="hint">В коротких месяцах — в последний день</div>}
      {payment && (
        <label className="toggle">
          <input type="checkbox" checked={paused} onChange={(e) => setPaused(e.target.checked)} />
          <span>На паузе (пропущенные даты не добавятся)</span>
        </label>
      )}
      {error && <div className="form-error">{error}</div>}
      <button className="button" disabled={busy} onClick={save}>
        Сохранить
      </button>
      {payment && (
        <button className="button button--destructive" disabled={busy} onClick={remove}>
          Удалить
        </button>
      )}
    </Sheet>
  )
}
