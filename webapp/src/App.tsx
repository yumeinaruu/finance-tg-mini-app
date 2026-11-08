import { ChartPie, LayoutGrid, ReceiptText, Repeat, Target, type LucideIcon } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { api } from './api'
import { Categories } from './categories'
import { ErrorBox, Loader } from './components/Common'
import { BudgetsScreen } from './screens/Budgets'
import { OperationsScreen } from './screens/Operations'
import { OverviewScreen } from './screens/Overview'
import { RecurringScreen } from './screens/Recurring'
import { MoreScreen } from './screens/More'
import { tg } from './telegram'
import type { Meta } from './types'
import { useAsync } from './useAsync'

type Tab = 'overview' | 'operations' | 'budgets' | 'recurring' | 'more'

const TABS: { id: Tab; icon: LucideIcon; label: string }[] = [
  { id: 'overview', icon: ChartPie, label: 'Обзор' },
  { id: 'operations', icon: ReceiptText, label: 'Операции' },
  { id: 'budgets', icon: Target, label: 'Бюджеты' },
  { id: 'recurring', icon: Repeat, label: 'Регулярные' },
  { id: 'more', icon: LayoutGrid, label: 'Ещё' },
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
        message={error === 'Нет доступа' ? 'Открой приложение кнопкой «Финансы» в чате с ботом' : (error ?? 'Не удалось загрузить данные')}
        onRetry={error === 'Нет доступа' ? undefined : reload}
      />
    )
  }
  return <Main meta={meta} onMetaChanged={reload} />
}

function Main({ meta, onMetaChanged }: { meta: Meta; onMetaChanged: () => void }) {
  const categories = useMemo(() => Categories.from(meta), [meta])
  const [tab, setTab] = useState<Tab>('overview')
  const [month, setMonth] = useState(meta.today.slice(0, 7))
  const [categoryFilter, setCategoryFilter] = useState<number | undefined>(undefined)

  const switchTab = (next: Tab) => {
    if (next !== 'operations') setCategoryFilter(undefined)
    setTab(next)
    window.scrollTo(0, 0)
  }

  return (
    <div className="app">
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
              window.scrollTo(0, 0)
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
        {tab === 'more' && <MoreScreen meta={meta} categories={categories} onCategoriesChanged={onMetaChanged} />}
      </main>

      <nav className="tabbar" aria-label="Разделы">
        {TABS.map(({ id, icon: Icon, label }) => (
          <button key={id} className={id === tab ? 'tabbar__item active' : 'tabbar__item'} aria-current={id === tab ? 'page' : undefined} onClick={() => switchTab(id)}>
            <Icon size={22} strokeWidth={id === tab ? 2.3 : 1.9} />
            <span>{label}</span>
          </button>
        ))}
      </nav>
    </div>
  )
}
