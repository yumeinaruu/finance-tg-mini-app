/** The small part of window.Telegram.WebApp the app uses. Everything degrades gracefully outside Telegram. */
interface TelegramWebApp {
  initData: string
  colorScheme: 'light' | 'dark'
  ready(): void
  expand(): void
  showConfirm(message: string, callback: (ok: boolean) => void): void
  BackButton: { show(): void; hide(): void; onClick(cb: () => void): void; offClick(cb: () => void): void }
  HapticFeedback?: {
    notificationOccurred(type: 'error' | 'success' | 'warning'): void
    selectionChanged(): void
  }
  isVersionAtLeast?(version: string): boolean
}

declare global {
  interface Window {
    Telegram?: { WebApp?: TelegramWebApp }
  }
}

export const tg: TelegramWebApp | undefined = window.Telegram?.WebApp?.initData ? window.Telegram.WebApp : undefined

export function initData(): string {
  return tg?.initData || import.meta.env.VITE_INIT_DATA || ''
}

export function confirmAction(message: string): Promise<boolean> {
  if (tg?.isVersionAtLeast?.('6.2')) return new Promise((resolve) => tg!.showConfirm(message, resolve))
  return Promise.resolve(window.confirm(message))
}

export function haptic(type: 'success' | 'error' | 'select') {
  const feedback = tg?.HapticFeedback
  if (!feedback) return
  if (type === 'select') feedback.selectionChanged()
  else feedback.notificationOccurred(type)
}
