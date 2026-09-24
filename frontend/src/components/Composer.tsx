import { useState, type FormEvent } from 'react'
import { useAppStore, getUser } from '../store/appStore'
import { CURRENT_USER_ID } from '../data/demo'
import { Avatar } from './Avatar'

export function Composer() {
  const users = useAppStore((s) => s.users)
  const addPost = useAppStore((s) => s.addPost)
  const pushToast = useAppStore((s) => s.pushToast)
  const me = getUser(CURRENT_USER_ID) ?? users[0]
  const [text, setText] = useState('')
  const [tagInput, setTagInput] = useState('')
  const [tags, setTags] = useState<string[]>([])
  const [visibility, setVisibility] = useState<'public' | 'followers' | 'private'>('public')

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const t = text.trim()
    if (!t) return
    addPost(t, tags, visibility)
    setText('')
    setTags([])
    setTagInput('')
    pushToast('Posted! Visible in Home and Explore', '✨')
  }

  return (
    <form
      onSubmit={submit}
      className="rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-4 transition-shadow focus-within:shadow-lg"
    >
      <div className="flex gap-3">
        <Avatar name={me.name} id={me.id} online />
        <textarea
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="What's happening?"
          rows={2}
          className="min-h-[2.5rem] flex-1 resize-none bg-transparent text-[15px] outline-none placeholder:text-[var(--muted)]"
        />
      </div>

      {tags.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-2 pl-13">
          {tags.map((t) => (
            <span
              key={t}
              className="flex items-center gap-1 rounded-full bg-[var(--accent-soft)] px-2.5 py-0.5 text-xs font-medium text-indigo-400"
            >
              #{t}
              <button
                type="button"
                onClick={() => setTags(tags.filter((x) => x !== t))}
                className="text-indigo-300 hover:text-indigo-200"
                aria-label={`Remove tag ${t}`}
              >
                ✕
              </button>
            </span>
          ))}
        </div>
      )}

      <div className="mt-3 flex items-center gap-2 border-t border-[var(--border)] pt-3">
        <input
          value={tagInput}
          onChange={(e) => setTagInput(e.target.value)}
          onKeyDown={(e) => {
            if ((e.key === 'Enter' || e.key === ' ') && tagInput.trim()) {
              e.preventDefault()
              const t = tagInput.trim().replace(/^#/, '').toLowerCase()
              if (t && !tags.includes(t)) setTags([...tags, t])
              setTagInput('')
            }
          }}
          placeholder="Add #tag (Enter)"
          className="w-40 rounded-lg border border-[var(--border)] bg-[var(--surface-2)] px-2.5 py-1.5 text-xs outline-none focus:border-[var(--accent)]"
        />
        <select
          value={visibility}
          onChange={(e) => setVisibility(e.target.value as 'public' | 'followers' | 'private')}
          className="rounded-lg border border-[var(--border)] bg-[var(--surface-2)] px-2.5 py-1.5 text-xs outline-none"
          aria-label="Post visibility"
        >
          <option value="public">🌍 Public</option>
          <option value="followers">👥 Followers</option>
          <option value="private">🔒 Only me</option>
        </select>
        <button
          type="submit"
          disabled={!text.trim()}
          className="ml-auto rounded-xl bg-[var(--accent)] px-5 py-2 text-sm font-medium text-white transition-all hover:bg-[var(--accent-hover)] active:scale-95 disabled:cursor-not-allowed disabled:opacity-40"
        >
          Post
        </button>
      </div>
    </form>
  )
}
