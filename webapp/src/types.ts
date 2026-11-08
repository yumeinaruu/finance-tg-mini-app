export type CategoryType = 'EXPENSE' | 'INCOME'

export interface Category {
  id: number
  name: string
  emoji: string
  type: CategoryType
  archived: boolean
}

export interface Meta {
  baseCurrency: string
  currencies: string[]
  today: string
  categories: Category[]
}

/** Amounts are minor units (kopecks / cents). */
export interface Operation {
  id: number
  categoryId: number
  type: CategoryType
  amount: number
  currency: string
  amountBase: number
  rate: string
  date: string
  note: string | null
  recurringId: number | null
}

export interface OperationInput {
  categoryId: number
  amount: number
  currency: string
  date: string
  note: string | null
}

export interface CategoryTotal {
  categoryId: number
  total: number
}

export interface DayTotal {
  date: string
  expense: number
  income: number
}

export interface Budget {
  categoryId: number
  limit: number
  spent: number
  forecast: number | null
}

export interface Overview {
  month: string
  income: number
  expense: number
  balance: number
  previousExpense: number
  expenseByCategory: CategoryTotal[]
  days: DayTotal[]
  budgets: Budget[]
}

export type ScheduleKind = 'monthly' | 'weekly' | 'yearly'

export interface Schedule {
  kind: ScheduleKind
  day?: number | null
  weekday?: number | null
  month?: number | null
}

export interface Recurring {
  id: number
  name: string
  categoryId: number
  amount: number
  currency: string
  schedule: Schedule
  description: string
  nextRun: string
  paused: boolean
}

export interface RecurringInput {
  name: string
  categoryId: number
  amount: number
  currency: string
  schedule: Schedule
  paused: boolean
}

export interface MonthReport {
  month: string
  income: number
  expense: number
  expenseByCategory: CategoryTotal[]
}

export interface OperationFilter {
  categoryId?: number
  type?: CategoryType
  currency?: string
  q?: string
}

export interface CategoryDetails {
  id: number
  name: string
  emoji: string
  type: CategoryType
  aliases: string[]
  operations: number
  total: number
  recurring: number
}

export interface Rate {
  currency: string
  /** BYN per 1 unit. */
  rate: string
  unit: number
}

export interface Rates {
  date: string
  rates: Rate[]
}
