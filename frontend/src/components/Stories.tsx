import { useEffect, useState } from 'react'
import { stories as storySeed } from '../data/demo'
import { useAppStore, getUser } from '../store/appStore'
import { artwork, isArtworkKey } from '../lib/geo'
import { CURRENT_USER_ID } from '../data/demo'
import type { Story } from '../types/demo'
import { Avatar } from './Avatar'

export function Stories() {
  const users = useAppStore((s) => s.users)
  const [startIdx, setStartIdx] = useState<number | null>(null)

  // One ring per user (demo data currently has one story per user)
  const rings = storySeed.filter(
    (s, i, arr) => arr.findIndex((x) => x.userId === s.userId) === i,
  )

  return (
    <div className="relative">
      <div className="flex gap-4 overflow-x-auto pb-2">
        {rings.map((s) => {
          const u = users.find((x) => x.id === s.userId)
          if (!u) return null
          const ringStart = storySeed.findIndex((x) => x.userId === s.userId)
          return (
            <button
              key={s.userId}
              onClick={() => setStartIdx(ringStart)}
              className="flex w-16 shrink-0 flex-col items-center gap-1.5"
            >
              <Avatar name={u.name} id={u.id} size="lg" ring />
              <span className="w-full truncate text-center text-[11px] text-[var(--muted)]">
                {u.id === CURRENT_USER_ID ? 'Your story' : u.username}
              </span>
            </button>
          )
        })}
      </div>
      {startIdx !== null && (
        <StoryViewer stories={storySeed} startIndex={startIdx} onClose={() => setStartIdx(null)} />
      )}
    </div>
  )
}

/** Viewer: auto-advances every 5s across ALL users' stories, Esc closes, arrows navigate. */
function StoryViewer({
  stories,
  startIndex,
  onClose,
}: {
  stories: Story[]
  startIndex: number
  onClose: () => void
}) {
  const [idx, setIdx] = useState(startIndex)
  const story = stories[idx]
  const author = getUser(story.userId)

  useEffect(() => {
    const t = setTimeout(() => {
      if (idx < stories.length - 1) setIdx(idx + 1)
      else onClose()
    }, 5000)
    return () => clearTimeout(t)
  }, [idx, stories.length, onClose])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
      if (e.key === 'ArrowRight') setIdx((i) => Math.min(i + 1, stories.length - 1))
      if (e.key === 'ArrowLeft') setIdx((i) => Math.max(i - 1, 0))
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [stories.length, onClose])

  if (!story || !author) return null

  return (
    <div
      className="fixed inset-0 z-[80] flex flex-col bg-black/95"
      role="dialog"
      aria-modal="true"
      aria-label="Story viewer"
    >
      <div className="flex gap-1 p-3">
        {stories.map((s, i) => (
          <div key={s.id} className="h-0.5 flex-1 overflow-hidden rounded-full bg-white/25">
            <div
              className={`h-full bg-white ${i < idx ? 'w-full' : ''}`}
              style={i === idx ? { animation: 'grow 5s linear forwards' } : i > idx ? { width: 0 } : undefined}
            />
          </div>
        ))}
      </div>

      <div className="flex items-center gap-3 px-4 pb-2">
        <Avatar name={author.name} id={author.id} size="sm" />
        <span className="text-sm font-medium text-white">{author.name}</span>
        <button
          onClick={onClose}
          className="ml-auto rounded-full bg-white/10 px-3 py-1 text-sm text-white hover:bg-white/20"
        >
          ✕ Close
        </button>
      </div>

      <div className="flex flex-1 items-center justify-center p-6" onClick={onClose}>
        <div className="max-w-md" onClick={(e) => e.stopPropagation()}>
          {story.kind === 'image' && isArtworkKey(story.image) && (
            <div
              className={`flex h-[60vh] w-72 items-end rounded-3xl bg-gradient-to-br p-4 sm:w-96 ${artwork[story.image].bg}`}
            >
              <p className="rounded-lg bg-black/30 px-2 py-1 text-xs text-white/90 backdrop-blur">
                {artwork[story.image].label}
              </p>
            </div>
          )}
          {story.kind === 'text' && (
            <div
              className={`flex h-[60vh] w-72 items-center justify-center rounded-3xl bg-gradient-to-br p-8 text-center text-2xl font-semibold text-white sm:w-96 ${story.background}`}
            >
              {story.text}
            </div>
          )}
        </div>
      </div>

      <p className="pb-4 text-center text-xs text-white/50">
        {story.views.toLocaleString()} views · click outside to close
      </p>

      <style>{`@keyframes grow { from { width: 0 } to { width: 100% } }`}</style>
    </div>
  )
}
