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
  profileImage: string | null
  interests: string | null
  lookingFor: string | null
  age: number | null
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
  const [interests, setInterests] = useState<string | null>(null)
  const [lookingFor, setLookingFor] = useState<string | null>(null)
  const [dob, setDob] = useState('')
  const [photo, setPhoto] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const [editing, setEditing] = useState(false)

  const saveProfile = useMutation({
    mutationFn: () =>
      api.put('/users/me', {
        firstName: profile.data?.firstName,
        lastName: profile.data?.lastName,
        bio,
        profession,
        interests,
        lookingFor,
        dateOfBirth: dob || null,
        profileImage: photo,
      }),
    onSuccess: () => {
      setEditing(false)
      queryClient.invalidateQueries({ queryKey: ['real-profile', effectiveUsername] })
      queryClient.invalidateQueries({ queryKey: ['auth-me'] })
      pushToast('Profile saved ✓', '✅')
    },
    onError: (e) => pushToast(apiErrorMessage(e), '⚠️'),
  })

  /** Photo upload: /media is multipart, returns the app-relative URL we then save. */
  const uploadPhoto = async (file: File) => {
    setUploading(true)
    try {
      const form = new FormData()
      form.append('file', file)
      const { data } = await api.post<{ url: string }>('/media', form)
      setPhoto(data.url)
      pushToast('Photo uploaded — tap Save to keep it', '🖼️')
    } catch (e) {
      pushToast(apiErrorMessage(e), '⚠️')
    } finally {
      setUploading(false)
    }
  }

  const logout = useAuthStore((s) => s.logout)

  const startEditing = () => {
    const p0 = profile.data
    setBio(p0?.bio ?? null)
    setProfession(p0?.profession ?? null)
    setInterests(p0?.interests ?? null)
    setLookingFor(p0?.lookingFor ?? null)
    setPhoto(p0?.profileImage ?? null)
    setDob('')
    setEditing(true)
  }

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
  const tags = (p.interests ?? '').split(',').map((t) => t.trim()).filter(Boolean)

  return (
    <div className="mx-auto max-w-2xl space-y-5 p-4 sm:p-6">
      <div className="overflow-hidden rounded-2xl border border-[var(--border)] bg-[var(--surface)]">
        <div className="h-28 bg-gradient-to-br from-indigo-500 via-violet-600 to-fuchsia-600" />
        <div className="px-5 pb-5">
          <div className="-mt-10 mb-3 flex items-end justify-between">
            <div className="flex h-20 w-20 items-center justify-center overflow-hidden rounded-full bg-gradient-to-br from-indigo-500 to-violet-600 text-xl font-bold text-white ring-4 ring-[var(--surface)]">
              {p.profileImage ? (
                <img src={p.profileImage} alt={name} className="h-full w-full object-cover" />
              ) : (
                initials
              )}
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
                <button
                  onClick={async () => {
                    if (!window.confirm(`Block @${p.username}? You will not see each other anywhere, and existing chats and follows are removed.`)) return
                    try {
                      await api.post(`/users/${p.id}/block`)
                      pushToast(`Blocked @${p.username} 🚫`, '🚫')
                      navigate('/home')
                    } catch (e) {
                      pushToast(apiErrorMessage(e), '⚠️')
                    }
                  }}
                  className="rounded-xl border border-rose-500/40 px-3 py-2 text-sm font-medium text-rose-400 transition-colors hover:bg-rose-500/10"
                >
                  Block
                </button>
              </div>
            )}
            {isMe && (
              <button
                onClick={() => (editing ? setEditing(false) : startEditing())}
                className="mb-1 rounded-xl border border-[var(--border)] px-4 py-2 text-sm font-medium hover:bg-[var(--surface-2)]"
              >
                {editing ? 'Cancel' : 'Edit profile'}
              </button>
            )}
          </div>

          <h1 className="text-xl font-bold">
            {name}
            {p.age != null && <span className="font-normal text-[var(--muted)]">, {p.age}</span>}
          </h1>
          <p className="text-sm text-[var(--muted)]">@{p.username}{p.profession ? ` · ${p.profession}` : ''}</p>

          {editing ? (
            <div className="mt-3 space-y-2">
              {/* photo */}
              <div className="flex items-center gap-3">
                <div className="flex h-16 w-16 items-center justify-center overflow-hidden rounded-full bg-gradient-to-br from-indigo-500 to-violet-600 text-lg font-bold text-white">
                  {photo ? <img src={photo} alt="preview" className="h-full w-full object-cover" /> : initials}
                </div>
                <label className="cursor-pointer rounded-xl border border-[var(--border)] px-3 py-2 text-sm font-medium hover:bg-[var(--surface-2)]">
                  {uploading ? 'Uploading…' : photo ? 'Change photo' : 'Add a photo'}
                  <input
                    type="file"
                    accept="image/png,image/jpeg,image/gif,image/webp"
                    className="hidden"
                    onChange={(e) => {
                      const f = e.target.files?.[0]
                      if (f) void uploadPhoto(f)
                    }}
                  />
                </label>
                <p className="text-xs text-[var(--muted)]">PNG/JPG/GIF/WebP · up to 5 MB</p>
              </div>

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
              <input
                value={lookingFor ?? ''}
                onChange={(e) => setLookingFor(e.target.value)}
                placeholder="Looking for… e.g. Coffee & good conversation"
                maxLength={60}
                className="w-full rounded-xl border border-[var(--border)] bg-[var(--surface-2)] px-3 py-2 text-sm outline-none focus:border-[var(--accent)]"
              />
              <input
                value={interests ?? ''}
                onChange={(e) => setInterests(e.target.value)}
                placeholder="Interests — comma separated, e.g. coffee, hiking, techno"
                maxLength={300}
                className="w-full rounded-xl border border-[var(--border)] bg-[var(--surface-2)] px-3 py-2 text-sm outline-none focus:border-[var(--accent)]"
              />
              <label className="block text-xs text-[var(--muted)]">
                Date of birth (used to show your age, 18+)
                <input
                  type="date"
                  value={dob}
                  onChange={(e) => setDob(e.target.value)}
                  className="mt-1 w-full rounded-xl border border-[var(--border)] bg-[var(--surface-2)] px-3 py-2 text-sm text-[var(--text)] outline-none focus:border-[var(--accent)]"
                />
              </label>
              {/* Upload status is stated ON the button: while a photo is uploading
                  the save is blocked, and previously it sat there silently disabled. */}
              <div className="flex items-center gap-3">
                <button
                  onClick={() => saveProfile.mutate()}
                  disabled={saveProfile.isPending || uploading}
                  className="rounded-xl bg-[var(--accent)] px-4 py-2 text-sm font-medium text-white hover:bg-[var(--accent-hover)] disabled:opacity-40"
                >
                  {uploading ? 'Uploading photo…' : saveProfile.isPending ? 'Saving…' : 'Save profile'}
                </button>
                {uploading && (
                  <span className="flex items-center gap-1.5 text-xs text-[var(--muted)]">
                    <span className="h-3 w-3 animate-spin rounded-full border-2 border-[var(--border)] border-t-indigo-400" />
                    finishing upload — save unlocks in a moment
                  </span>
                )}
              </div>
            </div>
          ) : (
            <div className="mt-2 space-y-2">
              {p.lookingFor && (
                <p className="text-sm font-medium text-rose-400">💘 {p.lookingFor}</p>
              )}
              <p className="whitespace-pre-wrap text-sm">{p.bio || 'No bio yet.'}</p>
              {tags.length > 0 && (
                <div className="flex flex-wrap gap-1.5">
                  {tags.map((t) => (
                    <span key={t} className="rounded-full bg-[var(--surface-2)] px-2.5 py-0.5 text-xs text-[var(--muted)]">
                      {t}
                    </span>
                  ))}
                </div>
              )}
            </div>
          )}

          <div className="mt-4 flex gap-6 text-sm">
            <span><strong>{p.postCount}</strong> <span className="text-[var(--muted)]">posts</span></span>
            <span><strong>{p.followerCount}</strong> <span className="text-[var(--muted)]">followers</span></span>
            <span><strong>{p.followingCount}</strong> <span className="text-[var(--muted)]">following</span></span>
          </div>

          {isMe && (
            <div className="mt-4 flex flex-wrap items-center gap-2">
              <Link
                to="/settings"
                className="rounded-xl border border-[var(--border)] px-4 py-2 text-sm font-medium hover:bg-[var(--surface-2)]"
              >
                ⚙️ Settings
              </Link>
              <button
                onClick={async () => {
                  await logout()
                  navigate('/login')
                }}
                className="rounded-xl border border-rose-500/40 px-4 py-2 text-sm font-medium text-rose-400 transition-colors hover:bg-rose-500/10"
              >
                Log out
              </button>
            </div>
          )}
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
