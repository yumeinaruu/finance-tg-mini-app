export const MONTHS = ['Январь', 'Февраль', 'Март', 'Апрель', 'Май', 'Июнь', 'Июль', 'Август', 'Сентябрь', 'Октябрь', 'Ноябрь', 'Декабрь']
const MONTHS_GENITIVE = ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня', 'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря']
const MONTHS_SHORT = ['янв', 'фев', 'мар', 'апр', 'май', 'июн', 'июл', 'авг', 'сен', 'окт', 'ноя', 'дек']
const WEEKDAYS_SHORT = ['вс', 'пн', 'вт', 'ср', 'чт', 'пт', 'сб']
export const WEEKDAYS = ['Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота', 'Воскресенье']

/** 173520 -> "1 735.20" (no sign). */
export function money(minor: number): string {
  const abs = Math.abs(Math.round(minor))
  const whole = Math.floor(abs / 100)
  const cents = String(abs % 100).padStart(2, '0')
  return `${String(whole).replace(/\B(?=(\d{3})+(?!\d))/g, ' ')}.${cents}`
}

/** Short form for chart labels: 1 735 / 12.4k. */
export function compact(minor: number): string {
  const value = Math.abs(minor) / 100
  if (value >= 10000) return `${(value / 1000).toFixed(value >= 100000 ? 0 : 1)}k`
  return String(Math.round(value)).replace(/\B(?=(\d{3})+(?!\d))/g, ' ')
}

/** "12,5" -> 1250; null when not a positive amount with at most 2 decimals. */
export function parseAmount(text: string): number | null {
  const value = text.trim().replace(/\s/g, '').replace(',', '.')
  if (!/^\d{1,9}(\.\d{0,2})?$/.test(value)) return null
  const [whole, fraction = ''] = value.split('.')
  const minor = Number(whole) * 100 + Number(fraction.padEnd(2, '0'))
  return minor > 0 ? minor : null
}

/** 1250 -> "12.50" for inputs. */
export function amountInput(minor: number): string {
  return (minor / 100).toFixed(2)
}

export function monthTitle(month: string): string {
  const [year, m] = month.split('-').map(Number)
  return `${MONTHS[m - 1]} ${year}`
}

export function monthShort(month: string): string {
  return MONTHS_SHORT[Number(month.split('-')[1]) - 1]
}

export function monthGenitive(month: string): string {
  return MONTHS_GENITIVE[Number(month.split('-')[1]) - 1]
}

export function addMonths(month: string, delta: number): string {
  const [year, m] = month.split('-').map(Number)
  const date = new Date(Date.UTC(year, m - 1 + delta, 1))
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}`
}

export function monthOf(date: string): string {
  return date.slice(0, 7)
}

export function daysInMonth(month: string): number {
  const [year, m] = month.split('-').map(Number)
  return new Date(Date.UTC(year, m, 0)).getUTCDate()
}

export function monthRange(month: string): { from: string; to: string } {
  return { from: `${month}-01`, to: `${month}-${String(daysInMonth(month)).padStart(2, '0')}` }
}

function shiftDay(date: string, days: number): string {
  const d = new Date(`${date}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}

/** "Сегодня" / "Вчера" / "5 ноября, ср" / "5 ноября 2025". */
export function dayTitle(date: string, today: string): string {
  if (date === today) return 'Сегодня'
  if (date === shiftDay(today, -1)) return 'Вчера'
  const d = new Date(`${date}T00:00:00Z`)
  const base = `${d.getUTCDate()} ${MONTHS_GENITIVE[d.getUTCMonth()]}`
  return date.slice(0, 4) === today.slice(0, 4) ? `${base}, ${WEEKDAYS_SHORT[d.getUTCDay()]}` : `${base} ${d.getUTCFullYear()}`
}

/** "15.11" or "15.11.2027" when the year differs. */
export function shortDate(date: string, today: string): string {
  const [year, month, day] = date.split('-')
  return year === today.slice(0, 4) ? `${day}.${month}` : `${day}.${month}.${year}`
}

export function percent(part: number, whole: number): number {
  return whole > 0 ? Math.round((part * 100) / whole) : 0
}

export function plural(n: number, one: string, few: string, many: string): string {
  const mod100 = n % 100
  const mod10 = n % 10
  if (mod100 >= 11 && mod100 <= 14) return many
  if (mod10 === 1) return one
  if (mod10 >= 2 && mod10 <= 4) return few
  return many
}
