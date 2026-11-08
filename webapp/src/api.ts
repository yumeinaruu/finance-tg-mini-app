import { initData } from './telegram'
import type {
  Budget,
  Category,
  CategoryDetails,
  CategoryType,
  Rates,
  Meta,
  MonthReport,
  Operation,
  OperationFilter,
  OperationInput,
  Overview,
  Recurring,
  RecurringInput,
} from './types'

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message)
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  let response: Response
  try {
    response = await fetch(`/api${path}`, {
      method,
      headers: {
        Authorization: `tma ${initData()}`,
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, 'Нет связи с сервером')
  }
  if (!response.ok) {
    let message = `Ошибка ${response.status}`
    try {
      message = (await response.json()).error ?? message
    } catch {
      // keep the generic message
    }
    throw new ApiError(response.status, message)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

function query(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') search.set(key, String(value))
  })
  return search.toString()
}

export const api = {
  meta: () => request<Meta>('GET', '/meta'),
  overview: (month: string) => request<Overview>('GET', `/overview/${month}`),

  operations: (from: string, to: string, filter: OperationFilter = {}) =>
    request<Operation[]>('GET', `/operations?${query({ from, to, ...filter })}`),
  createOperation: (input: OperationInput) => request<Operation>('POST', '/operations', input),
  updateOperation: (id: number, input: OperationInput) => request<Operation>('PUT', `/operations/${id}`, input),
  deleteOperation: (id: number) => request<void>('DELETE', `/operations/${id}`),

  budgets: (month: string) => request<Budget[]>('GET', `/budgets/${month}`),
  setBudget: (month: string, categoryId: number, limit: number) =>
    request<void>('PUT', `/budgets/${month}/${categoryId}`, { limit }),
  deleteBudget: (month: string, categoryId: number) => request<void>('DELETE', `/budgets/${month}/${categoryId}`),
  copyBudgets: (month: string) => request<{ count: number }>('POST', `/budgets/${month}/copy`),

  recurring: () => request<Recurring[]>('GET', '/recurring'),
  createRecurring: (input: RecurringInput) => request<Recurring>('POST', '/recurring', input),
  updateRecurring: (id: number, input: RecurringInput) => request<Recurring>('PUT', `/recurring/${id}`, input),
  deleteRecurring: (id: number) => request<void>('DELETE', `/recurring/${id}`),

  monthly: (months = 12) => request<MonthReport[]>('GET', `/reports/monthly?months=${months}`),
  exportCsv: (from: string, to: string) => request<void>('POST', '/export', { from, to }),

  categories: () => request<CategoryDetails[]>('GET', '/categories'),
  createCategory: (name: string, type: CategoryType) => request<Category>('POST', '/categories', { name, type }),
  renameCategory: (id: number, name: string) => request<Category>('PUT', `/categories/${id}`, { name }),
  deleteCategory: (id: number, options: { moveTo?: number; hide?: boolean } = {}) =>
    request<void>('DELETE', `/categories/${id}?${query({ moveTo: options.moveTo, hide: options.hide ? 'true' : undefined })}`),
  addAlias: (id: number, word: string) => request<{ alias: string }>('POST', `/categories/${id}/aliases`, { word }),
  removeAlias: (id: number, word: string) => request<void>('DELETE', `/categories/${id}/aliases/${encodeURIComponent(word)}`),

  rates: () => request<Rates>('GET', '/rates'),
  backup: () => request<void>('POST', '/backup'),
}
