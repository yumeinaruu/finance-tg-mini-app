import { api } from '../api'
import { ErrorBox, Loader, PageHeader, Section } from '../components/Common'
import { shortDate } from '../format'
import { useAsync } from '../useAsync'

const NAMES: Record<string, string> = { USD: 'Доллар США', EUR: 'Евро', RUB: 'Российский рубль' }

export function RatesScreen({ base, today, onBack }: { base: string; today: string; onBack: () => void }) {
  const { data, error, loading, reload } = useAsync(() => api.rates(), [])
  return (
    <div className="screen">
      <PageHeader title="Курсы валют" onBack={onBack} />
      {loading && !data && <Loader />}
      {error && <ErrorBox message={error} onRetry={reload} />}
      {data && (
        <>
          <Section title={`На ${shortDate(data.date, today)}, в ${base}`} flush>
            <ul className="list">
              {data.rates.map((r) => (
                <li key={r.currency} className="row">
                  <span className="currency-badge">{r.currency}</span>
                  <span className="row__main">
                    <span className="row__line">
                      <span className="row__title">{NAMES[r.currency] ?? r.currency}</span>
                      <span className="row__amount">{(Number(r.rate) * r.unit).toFixed(4)}</span>
                    </span>
                    {r.unit > 1 && <span className="row__meta">за {r.unit} {r.currency}</span>}
                  </span>
                </li>
              ))}
            </ul>
          </Section>
          <p className="hint">Рыночный курс, обновляется каждые 6 часов. Для операций в валюте берётся курс на дату операции.</p>
        </>
      )}
    </div>
  )
}
