import { compact, money } from '../format'

export interface Slice {
  id: number
  value: number
  color: string
}

/** Donut chart; tapping a segment calls [onSelect]. */
export function Donut({ slices, total, onSelect }: { slices: Slice[]; total: number; onSelect?: (id: number) => void }) {
  const radius = 42
  const circumference = 2 * Math.PI * radius
  let offset = 0
  return (
    <svg className="donut" viewBox="0 0 120 120" role="img" aria-label="Расходы по категориям">
      <circle cx="60" cy="60" r={radius} className="donut__track" />
      {slices.map((slice) => {
        const length = total > 0 ? (slice.value / total) * circumference : 0
        const dash = `${Math.max(length - 1.5, 0.5)} ${circumference}`
        const element = (
          <circle
            key={slice.id}
            cx="60"
            cy="60"
            r={radius}
            fill="none"
            stroke={slice.color}
            strokeWidth="16"
            strokeDasharray={dash}
            strokeDashoffset={-offset}
            transform="rotate(-90 60 60)"
            onClick={() => onSelect?.(slice.id)}
            style={{ cursor: onSelect ? 'pointer' : undefined }}
          />
        )
        offset += length
        return element
      })}
      <text x="60" y="57" className="donut__label">
        расходы
      </text>
      <text x="60" y="73" className="donut__value">
        {compact(total)}
      </text>
    </svg>
  )
}

/** Daily expense bars for one month; [values] has one entry per day. */
export function DayBars({ values, highlight }: { values: number[]; highlight?: number }) {
  const max = Math.max(...values, 1)
  const width = 300
  const height = 90
  const gap = 2
  const barWidth = (width - gap * (values.length - 1)) / values.length
  return (
    <svg className="bars" viewBox={`0 0 ${width} ${height + 14}`} preserveAspectRatio="none" role="img" aria-label="Расходы по дням">
      {values.map((value, i) => {
        const h = value > 0 ? Math.max((value / max) * height, 2) : 0
        return (
          <rect
            key={i}
            x={i * (barWidth + gap)}
            y={height - h}
            width={barWidth}
            height={h}
            rx="1.5"
            className={i + 1 === highlight ? 'bars__bar bars__bar--today' : 'bars__bar'}
          >
            <title>{`${i + 1}: ${money(value)}`}</title>
          </rect>
        )
      })}
      {[1, 10, 20, values.length].map((day) => (
        <text
          key={day}
          x={day === 1 ? 0 : day === values.length ? width : (day - 1) * (barWidth + gap) + barWidth / 2}
          y={height + 12}
          className="chart-axis"
          textAnchor={day === 1 ? 'start' : day === values.length ? 'end' : 'middle'}
        >
          {day}
        </text>
      ))}
    </svg>
  )
}

export interface Series {
  name: string
  color: string
  values: number[]
}

/** Line chart with month labels under the points. */
export function LineChart({ series, labels }: { series: Series[]; labels: string[] }) {
  const width = 320
  const height = 140
  const pad = { top: 10, right: 8, bottom: 20, left: 8 }
  const max = Math.max(...series.flatMap((s) => s.values), 1)
  const step = labels.length > 1 ? (width - pad.left - pad.right) / (labels.length - 1) : 0
  const x = (i: number) => pad.left + i * step
  const y = (v: number) => pad.top + (1 - v / max) * (height - pad.top - pad.bottom)
  return (
    <svg className="line-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Доходы и расходы по месяцам">
      {[0.25, 0.5, 0.75, 1].map((f) => (
        <line key={f} x1={pad.left} x2={width - pad.right} y1={y(max * f)} y2={y(max * f)} className="chart-grid" />
      ))}
      {series.map((s) => (
        <g key={s.name}>
          <polyline
            fill="none"
            stroke={s.color}
            strokeWidth="2.5"
            strokeLinejoin="round"
            strokeLinecap="round"
            points={s.values.map((v, i) => `${x(i)},${y(v)}`).join(' ')}
          />
          {s.values.map((v, i) => (
            <circle key={i} cx={x(i)} cy={y(v)} r="2.5" fill={s.color}>
              <title>{`${labels[i]}: ${money(v)}`}</title>
            </circle>
          ))}
        </g>
      ))}
      {labels.map((label, i) => (
        <text key={label + i} x={x(i)} y={height - 4} className="chart-axis">
          {label}
        </text>
      ))}
    </svg>
  )
}

/** Budget progress: green below 80%, amber up to 100%, red above. */
export function Progress({ value, limit }: { value: number; limit: number }) {
  const ratio = limit > 0 ? value / limit : 0
  const state = ratio >= 1 ? 'over' : ratio >= 0.8 ? 'warn' : 'ok'
  return (
    <div className="progress">
      <div className={`progress__fill progress__fill--${state}`} style={{ width: `${Math.min(ratio, 1) * 100}%` }} />
    </div>
  )
}
