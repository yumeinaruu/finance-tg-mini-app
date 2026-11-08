import { EyeOff, Plus, X } from 'lucide-react'
import { useState } from 'react'
import { api } from '../api'
import type { Categories } from '../categories'
import { Empty, ErrorBox, Field, Loader, PageHeader, Section, Segmented } from '../components/Common'
import { Sheet } from '../components/Sheet'
import { useToast } from '../components/Toast'
import { money, plural } from '../format'
import { CategoryIcon } from '../icons'
import { confirmAction, haptic } from '../telegram'
import type { CategoryDetails, CategoryType } from '../types'
import { useAsync } from '../useAsync'

interface Props {
  categories: Categories
  base: string
  onBack: () => void
  /** Called after any change so the rest of the app sees new names. */
  onChanged: () => void
}

export function CategoriesScreen({ categories, base, onBack, onChanged }: Props) {
  const [type, setType] = useState<CategoryType>('EXPENSE')
  const { data, error, loading, reload } = useAsync(() => api.categories(), [])
  const [editing, setEditing] = useState<CategoryDetails | 'new' | null>(null)

  // colors come from the app-wide map, which reloads after every change
  const palette = categories
  const list = data?.filter((c) => c.type === type) ?? []

  const changed = () => {
    setEditing(null)
    reload()
    onChanged()
  }

  return (
    <div className="screen">
      <PageHeader title="Категории" onBack={onBack} />
      <Segmented
        value={type}
        onChange={setType}
        options={[
          { value: 'EXPENSE', label: 'Расходы' },
          { value: 'INCOME', label: 'Доходы' },
        ]}
      />
      {loading && !data && <Loader />}
      {error && <ErrorBox message={error} onRetry={reload} />}
      {data && list.length === 0 && <Empty icon={Plus} title="Категорий пока нет" />}
      {list.length > 0 && (
        <Section flush>
          <ul className="list">
            {list.map((c) => (
              <li key={c.id}>
                <button className="row row--button" onClick={() => setEditing(c)}>
                  <CategoryIcon categories={palette} id={c.id} />
                  <span className="row__main">
                    <span className="row__line">
                      <span className="row__title">{c.name}</span>
                      {c.operations > 0 && (
                        <span className="row__meta">
                          {c.operations} {plural(c.operations, 'операция', 'операции', 'операций')}
                        </span>
                      )}
                    </span>
                    <span className="row__line">
                      <span className="row__meta">{c.aliases.length > 0 ? c.aliases.join(', ') : 'Нет слов для бота'}</span>
                    </span>
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </Section>
      )}
      {data && (
        <button className="button button--secondary" onClick={() => setEditing('new')}>
          <Plus size={18} /> Новая категория
        </button>
      )}
      <p className="hint">Слова помогают боту понять категорию: напишешь «бассейн 15» — трата попадёт в нужную категорию.</p>

      {editing === 'new' && <NewCategorySheet type={type} onClose={() => setEditing(null)} onSaved={changed} />}
      {editing && editing !== 'new' && (
        <CategorySheet
          category={editing}
          palette={palette}
          others={(data ?? []).filter((c) => c.type === editing.type && c.id !== editing.id)}
          base={base}
          onClose={() => setEditing(null)}
          onWordsChanged={reload}
          onSaved={changed}
        />
      )}
    </div>
  )
}

function NewCategorySheet({ type, onClose, onSaved }: { type: CategoryType; onClose: () => void; onSaved: () => void }) {
  const toast = useToast()
  const [name, setName] = useState('')
  const [kind, setKind] = useState<CategoryType>(type)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const save = async () => {
    if (!name.trim()) return setError('Укажи название')
    setBusy(true)
    try {
      await api.createCategory(name, kind)
      haptic('success')
      toast('Категория добавлена')
      onSaved()
    } catch (e) {
      setError((e as Error).message)
      setBusy(false)
    }
  }

  return (
    <Sheet title="Новая категория" onClose={onClose}>
      <Segmented
        value={kind}
        onChange={setKind}
        options={[
          { value: 'EXPENSE', label: 'Расход' },
          { value: 'INCOME', label: 'Доход' },
        ]}
      />
      <Field label="Название">
        <input className="input" placeholder="Например, спорт" maxLength={32} value={name} onChange={(e) => setName(e.target.value)} autoFocus />
      </Field>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      <div className="sheet__actions">
        <button className="button" disabled={busy} onClick={save}>
          Добавить
        </button>
      </div>
    </Sheet>
  )
}

function CategorySheet({
  category,
  palette,
  others,
  base,
  onClose,
  onWordsChanged,
  onSaved,
}: {
  category: CategoryDetails
  palette: Categories
  others: CategoryDetails[]
  base: string
  onClose: () => void
  onWordsChanged: () => void
  onSaved: () => void
}) {
  const toast = useToast()
  const [name, setName] = useState(category.name)
  const [aliases, setAliases] = useState(category.aliases)
  const [word, setWord] = useState('')
  const [removing, setRemoving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const used = category.operations > 0 || category.recurring > 0

  const run = async (action: () => Promise<void>) => {
    setBusy(true)
    setError(null)
    try {
      await action()
    } catch (e) {
      haptic('error')
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const rename = () =>
    run(async () => {
      if (name.trim() === category.name) return onClose()
      await api.renameCategory(category.id, name)
      haptic('success')
      toast('Категория переименована')
      onSaved()
    })

  const addWord = () =>
    run(async () => {
      if (!word.trim()) return
      const { alias } = await api.addAlias(category.id, word)
      setAliases((current) => (current.includes(alias) ? current : [...current, alias]))
      setWord('')
      onWordsChanged()
    })

  const removeWord = (alias: string) =>
    run(async () => {
      await api.removeAlias(category.id, alias)
      setAliases((current) => current.filter((a) => a !== alias))
      onWordsChanged()
    })

  const remove = (options: { moveTo?: number; hide?: boolean } = {}) =>
    run(async () => {
      const target = others.find((c) => c.id === options.moveTo)
      const question = target
        ? `Перенести операции в «${target.name}» и удалить «${category.name}»?`
        : options.hide
          ? null
          : `Удалить категорию «${category.name}»?`
      if (question && !(await confirmAction(question))) return
      await api.deleteCategory(category.id, options)
      haptic('success')
      toast(options.hide ? 'Категория скрыта' : 'Категория удалена')
      onSaved()
    })

  return (
    <Sheet title={category.name} onClose={onClose}>
      <Field label="Название">
        <div className="input-action">
          <input className="input" maxLength={32} value={name} onChange={(e) => setName(e.target.value)} />
          <button className="button button--inline" disabled={busy || !name.trim() || name.trim() === category.name} onClick={rename}>
            Сохранить
          </button>
        </div>
      </Field>

      <Field label="Слова для бота">
        <div className="words">
          {aliases.map((alias) => (
            <span key={alias} className="word">
              {alias}
              <button aria-label={`Удалить слово ${alias}`} disabled={busy} onClick={() => removeWord(alias)}>
                <X size={14} />
              </button>
            </span>
          ))}
          {aliases.length === 0 && <span className="hint">Пока нет — бот узнаёт категорию только по названию</span>}
        </div>
        <div className="input-action">
          <input
            className="input"
            placeholder="Например, бассейн"
            value={word}
            onChange={(e) => setWord(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && addWord()}
          />
          <button className="button button--secondary button--inline" disabled={busy || !word.trim()} onClick={addWord}>
            Добавить
          </button>
        </div>
      </Field>

      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}

      {!removing ? (
        <button className="button button--destructive" disabled={busy} onClick={() => (used ? setRemoving(true) : remove())}>
          Удалить категорию
        </button>
      ) : (
        <div className="danger-zone">
          <p>
            {category.operations > 0 &&
              `В категории ${category.operations} ${plural(category.operations, 'операция', 'операции', 'операций')} на ${money(category.total)} ${base}. `}
            {category.recurring > 0 && `К ней привязано регулярных платежей: ${category.recurring}. `}
            Куда их перенести?
          </p>
          {others.length > 0 && (
            <div className="tiles">
              {others.map((c) => (
                <button key={c.id} type="button" className="tile" disabled={busy} onClick={() => remove({ moveTo: c.id })}>
                  <CategoryIcon categories={palette} id={c.id} />
                  <span className="tile__label">{c.name}</span>
                </button>
              ))}
            </div>
          )}
          <button className="button button--plain" disabled={busy} onClick={() => remove({ hide: true })}>
            <EyeOff size={17} /> Не переносить, а скрыть категорию
          </button>
          <p className="hint">Скрытая категория пропадёт из выбора, но её операции останутся в отчётах.</p>
        </div>
      )}
    </Sheet>
  )
}
