import { Repeat } from 'lucide-react'
import { useState } from 'react'
import { api } from '../api'
import type { Categories } from '../categories'
import { CategoryPicker, Field, Segmented } from '../components/Common'
import { Sheet } from '../components/Sheet'
import { useToast } from '../components/Toast'
import { amountInput, money, parseAmount } from '../format'
import { confirmAction, haptic } from '../telegram'
import type { CategoryType, Meta, Operation } from '../types'

interface Props {
  meta: Meta
  categories: Categories
  operation: Operation | null
  defaultDate: string
  onClose: () => void
  onSaved: () => void
}

/** Create or edit an operation: amount first, then category, then the details. */
export function OperationForm({ meta, categories, operation, defaultDate, onClose, onSaved }: Props) {
  const toast = useToast()
  const [type, setType] = useState<CategoryType>(operation?.type ?? 'EXPENSE')
  const [amount, setAmount] = useState(operation ? amountInput(operation.amount) : '')
  const [currency, setCurrency] = useState(operation?.currency ?? meta.baseCurrency)
  const [categoryId, setCategoryId] = useState<number | null>(operation?.categoryId ?? null)
  const [date, setDate] = useState(operation?.date ?? defaultDate)
  const [note, setNote] = useState(operation?.note ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const changeType = (next: CategoryType) => {
    setType(next)
    if (categoryId !== null && categories.get(categoryId).type !== next) setCategoryId(null)
  }

  const save = async () => {
    const minor = parseAmount(amount)
    if (minor === null) return setError('Введи сумму, например 12.50')
    if (categoryId === null) return setError('Выбери категорию')
    if (!date) return setError('Выбери дату')
    setBusy(true)
    setError(null)
    try {
      const input = { categoryId, amount: minor, currency, date, note: note.trim() || null }
      if (operation) await api.updateOperation(operation.id, input)
      else await api.createOperation(input)
      haptic('success')
      toast(operation ? 'Изменения сохранены' : 'Операция добавлена')
      onSaved()
    } catch (e) {
      haptic('error')
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const remove = async () => {
    if (!operation || !(await confirmAction('Удалить операцию?'))) return
    setBusy(true)
    try {
      await api.deleteOperation(operation.id)
      haptic('success')
      toast('Операция удалена')
      onSaved()
    } catch (e) {
      setError((e as Error).message)
      setBusy(false)
    }
  }

  return (
    <Sheet title={operation ? 'Операция' : 'Новая операция'} onClose={onClose}>
      <Segmented
        value={type}
        onChange={changeType}
        options={[
          { value: 'EXPENSE', label: 'Расход' },
          { value: 'INCOME', label: 'Доход' },
        ]}
      />
      <div className={type === 'INCOME' ? 'amount-field amount-field--income' : 'amount-field'}>
        <input
          className="amount-field__input"
          inputMode="decimal"
          placeholder="0"
          aria-label="Сумма"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          autoFocus={!operation}
        />
        <select className="amount-field__currency" aria-label="Валюта" value={currency} onChange={(e) => setCurrency(e.target.value)}>
          {meta.currencies.map((c) => (
            <option key={c}>{c}</option>
          ))}
        </select>
      </div>
      {operation && operation.currency !== meta.baseCurrency && operation.currency === currency && (
        <p className="hint hint--center">
          {money(operation.amountBase)} {meta.baseCurrency} по курсу {Number(operation.rate).toFixed(4)}
        </p>
      )}
      <CategoryPicker categories={categories} type={type} value={categoryId} onChange={setCategoryId} />
      <div className="form-row">
        <Field label="Дата">
          <input className="input" type="date" value={date} max={meta.today} onChange={(e) => setDate(e.target.value)} />
        </Field>
      </div>
      <Field label="Заметка">
        <input className="input" placeholder="Например, кофе с собой" maxLength={200} value={note} onChange={(e) => setNote(e.target.value)} />
      </Field>
      {operation?.recurringId && (
        <p className="hint hint--icon">
          <Repeat size={14} /> Добавлена регулярным платежом
        </p>
      )}
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      <div className="sheet__actions">
        <button className="button" disabled={busy} onClick={save}>
          {operation ? 'Сохранить' : 'Добавить'}
        </button>
        {operation && (
          <button className="button button--destructive" disabled={busy} onClick={remove}>
            Удалить операцию
          </button>
        )}
      </div>
    </Sheet>
  )
}
