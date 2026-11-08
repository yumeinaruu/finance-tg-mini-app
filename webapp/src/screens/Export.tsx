import { Send } from 'lucide-react'
import { useState } from 'react'
import { api } from '../api'
import { PageHeader, Section, Segmented } from '../components/Common'
import { useToast } from '../components/Toast'
import { addMonths, monthRange, shortDate } from '../format'
import { haptic } from '../telegram'

type Preset = 'month' | 'previous' | 'year' | 'custom'

function range(preset: Preset, today: string): { from: string; to: string } {
  const month = today.slice(0, 7)
  if (preset === 'previous') return monthRange(addMonths(month, -1))
  if (preset === 'year') return { from: `${today.slice(0, 4)}-01-01`, to: today }
  return { from: monthRange(month).from, to: today }
}

/** CSV export: pick a period, the bot sends the file to the chat (downloads are unreliable inside Telegram on iOS). */
export function ExportScreen({ today, onBack }: { today: string; onBack: () => void }) {
  const toast = useToast()
  const [preset, setPreset] = useState<Preset>('month')
  const [custom, setCustom] = useState(range('month', today))
  const [busy, setBusy] = useState(false)
  const period = preset === 'custom' ? custom : range(preset, today)

  const send = async () => {
    setBusy(true)
    try {
      await api.exportCsv(period.from, period.to)
      haptic('success')
      toast('Файл отправлен в чат с ботом')
    } catch (e) {
      toast((e as Error).message, 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="screen">
      <PageHeader title="Выгрузка в CSV" onBack={onBack} />
      <p className="hint">Файл с операциями за период придёт в чат с ботом. Его можно открыть в Numbers, Excel или Google Таблицах.</p>
      <Segmented
        value={preset}
        onChange={setPreset}
        options={[
          { value: 'month', label: 'Месяц' },
          { value: 'previous', label: 'Прошлый' },
          { value: 'year', label: 'Год' },
          { value: 'custom', label: 'Период' },
        ]}
      />
      <Section flush>
        {preset === 'custom' ? (
          <div className="list">
            <label className="row row--field">
              <span className="row__title">С</span>
              <input className="row__date" type="date" value={custom.from} max={custom.to} onChange={(e) => setCustom({ ...custom, from: e.target.value })} />
            </label>
            <label className="row row--field">
              <span className="row__title">По</span>
              <input className="row__date" type="date" value={custom.to} min={custom.from} max={today} onChange={(e) => setCustom({ ...custom, to: e.target.value })} />
            </label>
          </div>
        ) : (
          <div className="row row--field">
            <span className="row__title">Период</span>
            <span className="row__meta">
              {shortDate(period.from, today)} — {shortDate(period.to, today)}
            </span>
          </div>
        )}
      </Section>
      <button className="button" disabled={busy || !period.from || !period.to} onClick={send}>
        <Send size={18} /> Отправить в чат
      </button>
    </div>
  )
}
