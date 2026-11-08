import { ChartColumn, ChevronRight, DatabaseBackup, FileSpreadsheet, Shapes, ArrowLeftRight, type LucideIcon } from 'lucide-react'
import { useState } from 'react'
import { api } from '../api'
import type { Categories } from '../categories'
import { ScreenTitle, Section } from '../components/Common'
import { useToast } from '../components/Toast'
import { haptic } from '../telegram'
import type { Meta } from '../types'
import { CategoriesScreen } from './CategoriesScreen'
import { ExportScreen } from './Export'
import { RatesScreen } from './Rates'
import { ReportsScreen } from './Reports'

type Page = 'reports' | 'categories' | 'rates' | 'export'

const ITEMS: { page: Page; icon: LucideIcon; color: string; title: string; hint: string }[] = [
  { page: 'reports', icon: ChartColumn, color: '#4C7DF0', title: 'Отчёты', hint: 'Год, сравнение месяцев, средние траты' },
  { page: 'categories', icon: Shapes, color: '#8A63D2', title: 'Категории', hint: 'Названия и слова для бота' },
  { page: 'rates', icon: ArrowLeftRight, color: '#2BA88C', title: 'Курсы валют', hint: 'USD, EUR, RUB к рублю' },
  { page: 'export', icon: FileSpreadsheet, color: '#E8833A', title: 'Выгрузка в CSV', hint: 'Операции за период файлом' },
]

export function MoreScreen({ meta, categories, onCategoriesChanged }: { meta: Meta; categories: Categories; onCategoriesChanged: () => void }) {
  const toast = useToast()
  const [page, setPage] = useState<Page | null>(null)
  const [busy, setBusy] = useState(false)
  const back = () => {
    setPage(null)
    window.scrollTo(0, 0)
  }

  if (page === 'reports') return <ReportsScreen categories={categories} meta={meta} onBack={back} />
  if (page === 'categories') return <CategoriesScreen categories={categories} base={meta.baseCurrency} onBack={back} onChanged={onCategoriesChanged} />
  if (page === 'rates') return <RatesScreen base={meta.baseCurrency} today={meta.today} onBack={back} />
  if (page === 'export') return <ExportScreen today={meta.today} onBack={back} />

  const backup = async () => {
    setBusy(true)
    try {
      await api.backup()
      haptic('success')
      toast('Копия базы отправлена в чат')
    } catch (e) {
      toast((e as Error).message, 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="screen">
      <ScreenTitle>Ещё</ScreenTitle>
      <Section flush>
        <ul className="list">
          {ITEMS.map(({ page: target, icon: Icon, color, title, hint }) => (
            <li key={target}>
              <button
                className="row row--button"
                onClick={() => {
                  setPage(target)
                  window.scrollTo(0, 0)
                }}
              >
                <span className="menu-icon" style={{ background: color }}>
                  <Icon size={18} strokeWidth={2.2} />
                </span>
                <span className="row__main">
                  <span className="row__title">{title}</span>
                  <span className="row__meta">{hint}</span>
                </span>
                <ChevronRight size={18} className="row__chevron" />
              </button>
            </li>
          ))}
        </ul>
      </Section>
      <Section flush>
        <button className="row row--button" disabled={busy} onClick={backup}>
          <span className="menu-icon" style={{ background: '#64748B' }}>
            <DatabaseBackup size={18} strokeWidth={2.2} />
          </span>
          <span className="row__main">
            <span className="row__title">Резервная копия</span>
            <span className="row__meta">Прислать файл базы в чат. Бот делает это и сам раз в неделю</span>
          </span>
        </button>
      </Section>
      <p className="hint hint--center">Основная валюта — {meta.baseCurrency}. Траты в валюте пересчитываются по курсу на дату операции.</p>
    </div>
  )
}
