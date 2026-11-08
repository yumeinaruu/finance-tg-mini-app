import { useEffect, useState, type ReactNode } from 'react'
import { money } from '../format'

export interface Slice {
  id: number
  value: number
  color: string
}

const RING_RADIUS = 80
const RING_WIDTH = 20
const RING_GAP = 3

/**
 * Category ring. A single category closes the full circle; several get small gaps between them.
 * Segments draw in once on mount (skipped with reduced motion via CSS).
 */
export function Ring({ slices, total, onSelect, children }: { slices: Slice[]; total: number; onSelect?: (id: number) => void; children?: ReactNode }) {
  const [drawn, setDrawn] = useState(false)
  useEffect(() => {
    const frame = requestAnimationFrame(() => setDrawn(true))
    return () => cancelAnimationFrame(frame)
  }, [])

  const circumference = 2 * Math.PI * RING_RADIUS
  const visible = slices.filter((s) => s.value > 0)
  const gap = visible.length > 1 ? RING_GAP : 0
  let offset = 0

  return (
    <div className="ring">
      <svg viewBox="0 0 200 200" role="img" aria-label="Расходы по категориям">
        <circle cx="100" cy="100" r={RING_RADIUS} className="ring__track" strokeWidth={RING_WIDTH} />
        {total > 0 &&
          visible.map((slice) => {
            const share = (slice.value / total) * circumference
            const length = Math.max(share - gap, 0.8)
            const element = (
              <circle
                key={slice.id}
                cx="100"
                cy="100"
                r={RING_RADIUS}
                fill="none"
                stroke={slice.color}
                strokeWidth={RING_WIDTH}
                strokeDasharray={drawn ? `${length} ${circumference}` : `0 ${circumference}`}
                strokeDashoffset={-(offset + gap / 2)}
                transform="rotate(-90 100 100)"
                className="ring__segment"
                onClick={() => onSelect?.(slice.id)}
              />
            )
            offset += share
            return element
          })}
      </svg>
      <div className="ring__center">{children}</div>
    </div>
  )
}

/** Daily expense bars for one month with a dashed daily-average line. */
export function DayBars({ values, today }: { values: number[]; today?: number }) {
  const width = 320
  const height = 96
  const gap = 3
  const max = Math.max(...values, 1)
  const daysCounted = today ?? values.length
  const average = values.slice(0, daysCounted).reduce((s, v) => s + v, 0) / Math.max(daysCounted, 1)
  const barWidth = (width - gap * (values.length - 1)) / values.length
  const y = (v: number) => height - (v / max) * height
  return (
    <div className="day-bars">
      <svg viewBox={`0 0 ${width} ${height + 16}`} preserveAspectRatio="none" role="img" aria-label="Расходы по дням">
        {values.map((value, i) => {
          const h = value > 0 ? Math.max(height - y(value), 3) : 0
          const day = i + 1
          const state = day === today ? 'today' : today !== undefined && day > today ? 'future' : 'past'
          return (
            <rect key={i} x={i * (barWidth + gap)} y={height - h} width={barWidth} height={h} rx={Math.min(barWidth / 2, 3)} className={`day-bars__bar day-bars__bar--${state}`}>
              <title>{`${day}: ${money(value)}`}</title>
            </rect>
          )
        })}
        {average > 0 && <line x1="0" x2={width} y1={y(average)} y2={y(average)} className="day-bars__avg" />}
        {[1, 10, 20, values.length].map((day) => (
          <text
            key={day}
            x={day === 1 ? 0 : day === values.length ? width : (day - 1) * (barWidth + gap) + barWidth / 2}
            y={height + 13}
            className="chart-axis"
            textAnchor={day === 1 ? 'start' : day === values.length ? 'end' : 'middle'}
          >
            {day}
          </text>
        ))}
      </svg>
      {average > 0 && <div className="day-bars__caption">В среднем {money(Math.round(average))} в день</div>}
    </div>
  )
}

export interface Series {
  name: string
  color: string
  values: number[]
}

/** Income and expense lines by month, with a soft area under each line. */
export function LineChart({ series, labels }: { series: Series[]; labels: string[] }) {
  const width = 320
  const height = 150
  const pad = { top: 12, right: 10, bottom: 22, left: 10 }
  const max = Math.max(...series.flatMap((s) => s.values), 1)
  const step = labels.length > 1 ? (width - pad.left - pad.right) / (labels.length - 1) : 0
  const x = (i: number) => pad.left + i * step
  const y = (v: number) => pad.top + (1 - v / max) * (height - pad.top - pad.bottom)
  const bottom = height - pad.bottom
  return (
    <svg className="line-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Доходы и расходы по месяцам">
      <defs>
        {series.map((s, i) => (
          <linearGradient key={s.name} id={`area-${i}`} x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor={s.color} stopOpacity="0.22" />
            <stop offset="100%" stopColor={s.color} stopOpacity="0" />
          </linearGradient>
        ))}
      </defs>
      {[0.5, 1].map((f) => (
        <line key={f} x1={pad.left} x2={width - pad.right} y1={y(max * f)} y2={y(max * f)} className="chart-grid" />
      ))}
      <line x1={pad.left} x2={width - pad.right} y1={bottom} y2={bottom} className="chart-grid" />
      {series.map((s, i) => {
        const points = s.values.map((v, j) => `${x(j)},${y(v)}`).join(' ')
        return (
          <g key={s.name}>
            <polygon points={`${x(0)},${bottom} ${points} ${x(s.values.length - 1)},${bottom}`} fill={`url(#area-${i})`} />
            <polyline fill="none" stroke={s.color} strokeWidth="2.5" strokeLinejoin="round" strokeLinecap="round" points={points} />
            <circle cx={x(s.values.length - 1)} cy={y(s.values[s.values.length - 1])} r="3.5" fill={s.color} />
          </g>
        )
      })}
      {labels.map((label, i) => (
        <text key={label + i} x={x(i)} y={height - 5} className="chart-axis" textAnchor="middle">
          {label}
        </text>
      ))}
    </svg>
  )
}

/** Budget progress: calm below 80%, amber up to 100%, red above. */
export function Progress({ value, limit }: { value: number; limit: number }) {
  const ratio = limit > 0 ? value / limit : 0
  const state = ratio >= 1 ? 'over' : ratio >= 0.8 ? 'warn' : 'ok'
  return (
    <div className="progress" role="progressbar" aria-valuenow={Math.round(ratio * 100)} aria-valuemin={0} aria-valuemax={100}>
      <div className={`progress__fill progress__fill--${state}`} style={{ width: `${Math.min(ratio, 1) * 100}%` }} />
    </div>
  )
}

/** Thin share bar under a category row. */
export function ShareBar({ share, color }: { share: number; color: string }) {
  return (
    <div className="share-bar">
      <div style={{ width: `${Math.max(share * 100, 1.5)}%`, background: color }} />
    </div>
  )
}
