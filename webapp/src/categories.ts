import type { Category, Meta } from './types'

/** Chart colors, assigned to categories in a stable order. */
const PALETTE = ['#3E88F7', '#F59E0B', '#10B981', '#EF4444', '#8B5CF6', '#06B6D4', '#F97316', '#EC4899', '#84CC16', '#64748B', '#A16207', '#14B8A6']

export class Categories {
  private readonly byId: Map<number, Category>
  private readonly colors: Map<number, string>

  constructor(readonly all: Category[]) {
    this.byId = new Map(all.map((c) => [c.id, c]))
    this.colors = new Map(all.map((c, i) => [c.id, PALETTE[i % PALETTE.length]]))
  }

  static from(meta: Meta) {
    return new Categories(meta.categories)
  }

  get(id: number): Category {
    return this.byId.get(id) ?? { id, name: 'Без категории', emoji: '❔', type: 'EXPENSE', archived: true }
  }

  color(id: number): string {
    return this.colors.get(id) ?? '#94A3B8'
  }

  label(id: number): string {
    const c = this.get(id)
    return `${c.emoji} ${c.name}`
  }

  active(type?: Category['type']): Category[] {
    return this.all.filter((c) => !c.archived && (type === undefined || c.type === type))
  }
}
