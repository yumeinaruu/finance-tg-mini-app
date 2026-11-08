import type { Category, Meta } from './types'

/** Muted, saturated tones that stay readable on light and dark Telegram themes. */
const PALETTE = ['#4C7DF0', '#E8833A', '#2BA88C', '#D6496E', '#8A63D2', '#3AA6C4', '#C9A227', '#6C8A3F', '#B4664A', '#5E6AD2', '#C2567F', '#4E9A62']

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
    return this.byId.get(id) ?? { id, name: 'Без категории', emoji: '', type: 'EXPENSE', archived: true }
  }

  color(id: number): string {
    return this.colors.get(id) ?? '#8E8E93'
  }

  name(id: number): string {
    return this.get(id).name
  }

  active(type?: Category['type']): Category[] {
    return this.all.filter((c) => !c.archived && (type === undefined || c.type === type))
  }
}
