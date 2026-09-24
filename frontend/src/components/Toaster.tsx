import { useAppStore } from '../store/appStore'

export function Toaster() {
  const toasts = useAppStore((s) => s.toasts)
  const dismiss = useAppStore((s) => s.dismissToast)

  return (
    <div className="pointer-events-none fixed bottom-20 left-1/2 z-[60] flex w-full max-w-sm -translate-x-1/2 flex-col gap-2 px-4 md:bottom-6">
      {toasts.map((t) => (
        <button
          key={t.id}
          onClick={() => dismiss(t.id)}
          className="animate-toast-in pointer-events-auto flex items-center gap-2 rounded-xl border border-[var(--border)] bg-[var(--surface)] px-4 py-3 text-left text-sm shadow-xl"
        >
          {t.icon && <span aria-hidden="true">{t.icon}</span>}
          <span>{t.text}</span>
        </button>
      ))}
    </div>
  )
}
