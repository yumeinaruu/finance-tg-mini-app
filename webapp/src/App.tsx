import { useCallback, useEffect, useMemo, useState } from 'react'
import { api } from './api'
import { Categories } from './categories'
import { ErrorBox, Loader } from './components/Common'
import { Sheet } from './components/Sheet'
import { BudgetsScreen } from './screens/Budgets'
import { OperationsScreen } from './screens/Operations'
import { OverviewScreen } from './screens/Overview'
import { RecurringScreen } from './screens/Recurring'
import { ReportsScreen } from './screens/Reports'
import { tg } from './telegram'
import type { Meta } from './types'
import { useAsync } from './useAsync'

type Tab = 'overview' | 'operations' | 'budgets' | 'recurring' | 'reports'

const TABS: { id: Tab; icon: string; label: string }[] = [
  { id: 'overview', icon: '📊', label: 'Обзор' },
  { id: 'operations', icon: '🧾', label: 'Операции' },
  { id: 'budgets', icon: '🎯', label: 'Бюджеты' },
  { id: 'recurring', icon: '🔁', label: 'Регулярные' },
  { id: 'reports', icon: '📈', label: 'Отчёты' },
]

export function App() {
  const { data: meta, error, loading, reload } = useAsync(() => api.meta(), [])

  useEffect(() => {
    tg?.ready()
    tg?.expand()
  }, [])

  if (loading && !meta) return <Loader />
  if (error || !meta) {
    return (
      <ErrorBox
        message={error === 'Нет доступа' ? 'Открой приложение через кнопку в чате с ботом' : (error ?? 'Ошибка')}
        onRetry={error === 'Нет доступа' ? undefined : reload}
      />
    )
  }
  return <Main meta={meta} />
}

function Main({ meta }: { meta: Meta }) {
  const categories = useMemo(() => Categories.from(meta), [meta])
  const [tab, setTab] = useState<Tab>('overview')
  const [month, setMonth] = useState(meta.today.slice(0, 7))
  const [categoryFilter, setCategoryFilter] = useState<number | undefined>(undefined)
  const [settings, setSettings] = useState(false)
  const closeSettings = useCallback(() => setSettings(false), [])

  const switchTab = (next: Tab) => {
    if (next !== 'operations') setCategoryFilter(undefined)
    setTab(next)
    window.scrollTo(0, 0)
  }

  return (
    <div className="app">
      <header className="topbar">
        <span className="topbar__title">{TABS.find((t) => t.id === tab)?.label}</span>
        <button className="icon-button" aria-label="Настройки" onClick={() => setSettings(true)}>
          ⚙︎
        </button>
      </header>

      <main>
        {tab === 'overview' && (
          <OverviewScreen
            categories={categories}
            base={meta.baseCurrency}
            today={meta.today}
            month={month}
            onMonthChange={setMonth}
            onOpenCategory={(id) => {
              setCategoryFilter(id)
              setTab('operations')
            }}
            onOpenBudgets={() => switchTab('budgets')}
          />
        )}
        {tab === 'operations' && (
          <OperationsScreen
            meta={meta}
            categories={categories}
            month={month}
            onMonthChange={setMonth}
            categoryFilter={categoryFilter}
            onCategoryFilterChange={setCategoryFilter}
          />
        )}
        {tab === 'budgets' && <BudgetsScreen categories={categories} base={meta.baseCurrency} today={meta.today} month={month} onMonthChange={setMonth} />}
        {tab === 'recurring' && <RecurringScreen meta={meta} categories={categories} />}
        {tab === 'reports' && <ReportsScreen categories={categories} base={meta.baseCurrency} today={meta.today} />}
      </main>

      <nav className="tabbar">
        {TABS.map((t) => (
          <button key={t.id} className={t.id === tab ? 'tabbar__item active' : 'tabbar__item'} onClick={() => switchTab(t.id)}>
            <span className="tabbar__icon">{t.icon}</span>
            <span>{t.label}</span>
          </button>
        ))}
      </nav>

      {settings && (
        <Sheet title="Настройки" onClose={closeSettings}>
          <div className="row">
            <span className="row__main">Базовая валюта</span>
            <span className="row__amount">{meta.baseCurrency}</span>
          </div>
          <div className="row">
            <span className="row__main">Валюты</span>
            <span className="row__hint">{meta.currencies.join(', ')}</span>
          </div>
          <div className="settings-categories">
            <div className="field__label">Категории</div>
            <div className="chips">
              {categories.active().map((c) => (
                <span key={c.id} className="chip chip--static">
                  {c.emoji} {c.name}
                </span>
              ))}
            </div>
          </div>
          <div className="hint">
            Добавлять, переименовывать и удалять категории и слова для них — в боте: /categories, /cat, /alias. Время уведомлений задаётся на сервере (NOTIFY_TIME).
          </div>
        </Sheet>
      )}
    </div>
  )
}
