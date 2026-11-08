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
  onEvent?(event: 'themeChanged', callback: () => void): void
  setHeaderColor?(color: string): void
  setBackgroundColor?(color: string): void
  setBottomBarColor?(color: string): void
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

/** Mirrors the Telegram color scheme on <html data-theme> and blends Telegram's header into the app background. */
export function syncTheme() {
  if (!tg) return
  const apply = () => {
    document.documentElement.dataset.theme = tg!.colorScheme
  }
  apply()
  tg.onEvent?.('themeChanged', apply)
  if (tg.isVersionAtLeast?.('6.1')) {
    tg.setHeaderColor?.('secondary_bg_color')
    tg.setBackgroundColor?.('secondary_bg_color')
  }
  if (tg.isVersionAtLeast?.('7.10')) tg.setBottomBarColor?.('secondary_bg_color')
}
