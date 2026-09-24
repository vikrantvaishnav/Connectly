import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, apiErrorMessage } from '../lib/api'
import { useAuthStore } from '../store/authStore'
import { useAppStore } from '../store/appStore'
import { RealPostCard, type RealPost } from '../components/RealFeed'

interface RealProfile {
  id: number
  username: string
  firstName: string | null
  lastName: string | null
  bio: string | null
  profession: string | null
  postCount: number
  followerCount: number
  followingCount: number
  following: boolean
  connectedWithMe: boolean
}

function displayNameOf(p: RealProfile): string {
  return [p.firstName, p.lastName].filter(Boolean).join(' ') || p.username
}

/** Real profile page backed by GET /users/{username}, PUT /users/me (own profile). */
export function RealProfilePage({ own }: { own?: boolean }) {
  const { username } = useParams()
  const me = useAuthStore((s) => s.user)
  const pushToast = useAppStore((s) => s.pushToast)
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const effectiveUsername = own ? (me?.username ?? '') : (username ?? '')

  const profile = useQuery({
    queryKey: ['real-profile', effectiveUsername],
    queryFn: async () => (await api.get<RealProfile>(`/users/${effectiveUsername}`)).data,
    enabled: effectiveUsername.length > 0,
  })

  const posts = useQuery({
    queryKey: ['real-profile-posts', effectiveUsername],
    queryFn: async () => (await api.get<{ posts: RealPost[] }>(`/posts/users/${effectiveUsername}?size=50`)).data.posts,
    enabled: effectiveUsername.length > 0,
  })

  const follow = useMutation({
    mutationFn: () => api.post(`/users/${profile.data?.id}/follow`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['real-profile', effectiveUsername] })
      pushToast('Following ✓', '✅')
    },
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })

  const unfollow = useMutation({
    mutationFn: () => api.delete(`/users/${profile.data?.id}/follow`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['real-profile', effectiveUsername] }),
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })

  const [bio, setBio] = useState<string | null>(null)
  const [profession, setProfession] = useState<string | null>(null)
  const [editing, setEditing] = useState(false)

  const saveProfile = useMutation({
    mutationFn: () => api.put('/users/me', { bio, profession }),
    onSuccess: () => {
      setEditing(false)
      queryClient.invalidateQueries({ queryKey: ['real-profile', effectiveUsername] })
      pushToast('Profile updated', '✅')
    },
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })

  if (profile.isPending) {
    return <div className="p-10 text-center text-sm text-[var(--muted)]">Loading…</div>
  }

  if (profile.isError || !profile.data) {
    return (
      <div className="p-10 text-center">
        <p className="text-sm text-[var(--muted)]">
          {effectiveUsername ? `User “${effectiveUsername}” not found.` : 'Sign in to see your profile.'}
        </p>
        <Link to="/home" className="mt-2 inline-block text-sm text-indigo-400 hover:underline">← Back home</Link>
      </div>
    )
  }

  const p = profile.data
  const isMe = me?.id === p.id
  const name = displayNameOf(p)
  const initials = name.split(' ').map(w => w[0]).slice(0, 2).join('').toUpperCase()
  const showBio = editing ? bio : (p.bio ?? '')
  const showProfession = editing ? profession : (p.profession ?? '')

  return (
    <div className="mx-auto max-w-2xl space-y-5 p-4 sm:p-6">
      <div className="overflow-hidden rounded-2xl border border-[var(--border)] bg-[var(--surface)]">
        <div className="h-28 bg-gradient-to-br from-indigo-500 via-violet-600 to-fuchsia-600" />
        <div className="px-5 pb-5">
          <div className="-mt-10 mb-3 flex items-end justify-between">
            <div className="flex h-20 w-20 items-center justify-center rounded-full bg-gradient-to-br from-indigo-500 to-violet-600 text-xl font-bold text-white ring-4 ring-[var(--surface)]">
              {initials}
            </div>
            {!isMe && (
              <div className="mb-1 flex gap-2">
                {p.following ? (
                  <button
                    onClick={() => unfollow.mutate()}
                    disabled={unfollow.isPending}
                    className="rounded-xl bg-[var(--surface-2)] px-4 py-2 text-sm font-medium text-[var(--muted)] transition-all hover:bg-[var(--accent-soft)]"
                  >
                    Following ✓
                  </button>
                ) : (
                  <button
                    onClick={() => follow.mutate()}
                    disabled={follow.isPending}
                    className="rounded-xl bg-[var(--accent)] px-4 py-2 text-sm font-medium text-white transition-all hover:bg-[var(--accent-hover)]"
                  >
                    Follow
                  </button>
                )}
                <button
                  onClick={async () => {
                    try {
                      const { data } = await api.post<{ id: number }>('/conversations', { userId: p.id })
                      navigate(`/messages/${data.id}`)
                    } catch (e) {
                      pushToast(apiErrorMessage(e), '⚠️')
                    }
                  }}
                  className="rounded-xl bg-[var(--surface-2)] px-4 py-2 text-sm font-medium transition-colors hover:bg-[var(--accent-soft)]"
                >
                  Message
                </button>
              </div>
            )}
            {isMe && (
              <button
                onClick={() => { setEditing(v => !v); setBio(p.bio); setProfession(p.profession) }}
                className="mb-1 rounded-xl border border-[var(--border)] px-4 py-2 text-sm font-medium hover:bg-[var(--surface-2)]"
              >
                {editing ? 'Cancel' : 'Edit profile'}
              </button>
            )}
          </div>

          <h1 className="text-xl font-bold">{name}</h1>
          <p className="text-sm text-[var(--muted)]">@{p.username}{p.profession ? ` · ${p.profession}` : ''}</p>

          {editing ? (
            <div className="mt-3 space-y-2">
              <textarea
                value={showBio ?? ''}
                onChange={(e) => setBio(e.target.value)}
                placeholder="Your bio…"
                rows={3}
                maxLength={500}
                className="w-full resize-none rounded-xl border border-[var(--border)] bg-[var(--surface-2)] px-3 py-2 text-sm outline-none focus:border-[var(--accent)]"
              />
              <input
                value={showProfession ?? ''}
                onChange={(e) => setProfession(e.target.value)}
                placeholder="Profession"
                maxLength={80}
                className="w-full rounded-xl border border-[var(--border)] bg-[var(--surface-2)] px-3 py-2 text-sm outline-none focus:border-[var(--accent)]"
              />
              <button
                onClick={() => saveProfile.mutate()}
                disabled={saveProfile.isPending}
                className="rounded-xl bg-[var(--accent)] px-4 py-2 text-sm font-medium text-white hover:bg-[var(--accent-hover)] disabled:opacity-40"
              >
                {saveProfile.isPending ? 'Saving…' : 'Save'}
              </button>
            </div>
          ) : (
            <p className="mt-2 whitespace-pre-wrap text-sm">{p.bio || 'No bio yet.'}</p>
          )}

          <div className="mt-4 flex gap-6 text-sm">
            <span><strong>{p.postCount}</strong> <span className="text-[var(--muted)]">posts</span></span>
            <span><strong>{p.followerCount}</strong> <span className="text-[var(--muted)]">followers</span></span>
            <span><strong>{p.followingCount}</strong> <span className="text-[var(--muted)]">following</span></span>
          </div>
        </div>
      </div>

      <h2 className="px-1 pt-2 text-sm font-semibold text-[var(--muted)]">Posts</h2>
      {posts.isPending && <div className="h-28 animate-pulse rounded-2xl bg-[var(--surface)]" />}
      {posts.data?.length === 0 && (
        <p className="rounded-2xl border border-dashed border-[var(--border)] p-10 text-center text-sm text-[var(--muted)]">
          No posts yet.
        </p>
      )}
      <div className="space-y-4">
        {posts.data?.map((post) => <RealPostCard key={post.id} post={post} showSave={false} />)}
      </div>
    </div>
  )
}
