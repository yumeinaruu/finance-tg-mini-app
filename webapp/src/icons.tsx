import {
  Baby, Book, Briefcase, Bus, Car, Coffee, Coins, Dumbbell, Fuel, Gamepad2, Gift, GraduationCap, HeartPulse, House, Landmark,
  Laptop, Package, PawPrint, PiggyBank, Plane, Scissors, ShoppingBag, Smartphone, Tag, Ticket, Train, UtensilsCrossed, Wallet,
  Wrench, Zap, type LucideIcon,
} from 'lucide-react'
import type { CSSProperties } from 'react'
import type { Categories } from './categories'

/** Icon by category name; user-made categories get a matching icon when the name says what it is. Order matters. */
const RULES: [RegExp, LucideIcon][] = [
  [/кофе|кофейн/, Coffee],
  [/еда|продукт|кафе|ресторан|обед|питани/, UtensilsCrossed],
  [/бензин|топлив|заправк/, Fuel],
  [/метро|автобус|проезд|троллейбус/, Bus],
  [/поезд|жд|электричк/, Train],
  [/такси|транспорт|авто|машин|каршер/, Car],
  [/коммун|свет|электр|жкх/, Zap],
  [/ремонт/, Wrench],
  [/жиль|квартир|аренд|дом/, House],
  [/подписк|связь|телефон|интернет|мобил/, Smartphone],
  [/здоров|аптек|врач|медиц|лекарств/, HeartPulse],
  [/одежд|покупк|шопинг|магазин/, ShoppingBag],
  [/техник|электрон|гаджет/, Laptop],
  [/игр/, Gamepad2],
  [/развлеч|кино|отдых|концерт/, Ticket],
  [/подар/, Gift],
  [/спорт|фитнес|зал|бассейн/, Dumbbell],
  [/путешеств|отпуск|авиа|поездк/, Plane],
  [/образован|курс|учеб/, GraduationCap],
  [/книг/, Book],
  [/живот|питом|кот|собак/, PawPrint],
  [/дет|ребен/, Baby],
  [/красот|салон|стрижк|парикмах/, Scissors],
  [/кредит|долг|банк|ипотек/, Landmark],
  [/инвест|сбереж|копил|накоп/, PiggyBank],
  [/зарплат|зп|оклад|аванс|преми/, Wallet],
  [/фриланс|работ|бизнес|подработ/, Briefcase],
  [/доход|кэшбэк|кешбэк|возврат|процент/, Coins],
  [/прочее|друго|разное/, Package],
]

export function iconForName(name: string): LucideIcon {
  const key = name.toLowerCase().replace(/ё/g, 'е')
  return RULES.find(([pattern]) => pattern.test(key))?.[1] ?? Tag
}

/** Category icon on a tile tinted with the category color. */
export function CategoryIcon({ categories, id, size = 'md' }: { categories: Categories; id: number; size?: 'sm' | 'md' | 'lg' }) {
  const Icon = iconForName(categories.name(id))
  const px = size === 'sm' ? 15 : size === 'lg' ? 22 : 18
  return (
    <span className={`cat-icon cat-icon--${size}`} style={{ '--c': categories.color(id) } as CSSProperties} aria-hidden="true">
      <Icon size={px} strokeWidth={2.1} />
    </span>
  )
}
