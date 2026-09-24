import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAppStore } from '../store/appStore'
import { useAuthStore } from '../store/authStore'
import { Avatar } from '../components/Avatar'
import { PostCard } from '../components/PostCard'
import { ConnectButton } from '../components/ConnectButton'
import { CURRENT_USER_ID } from '../data/demo'
import { RealProfilePage } from './RealProfilePage'

export function ProfilePage() {
  const authUser = useAuthStore((s) => s.user)
  const { username } = useParams()
  // Signed-in users always get the real page (own profile or by username).
  if (authUser) return <RealProfilePage own={!username} />
  return <DemoProfile />
}

function DemoProfile() {
  const { username } = useParams()
  const users = useAppStore((s) => s.users)
  const posts = useAppStore((s) => s.posts)
  const savedPostIds = useAppStore((s) => s.savedPostIds)
  const followedUserIds = useAppStore((s) => s.followedUserIds)
  const toggleFollow = useAppStore((s) => s.toggleFollow)
  const toggleBlock = useAppStore((s) => s.toggleBlock)
  const blockedUserIds = useAppStore((s) => s.blockedUserIds)
  const pushToast = useAppStore((s) => s.pushToast)
  const [tab, setTab] = useState<'posts' | 'saved' | 'about'>('posts')

  const user = users.find((u) => u.username === username)
  if (!user) {
    return (
      <div className="p-10 text-center">
        <p className="text-sm text-[var(--muted)]">User “{username}” not found.</p>
        <Link to="/home" className="mt-2 inline-block text-sm text-indigo-400 hover:underline">
          ← Back home
        </Link>
      </div>
    )
  }

  const isMe = user.id === CURRENT_USER_ID
  const following = followedUserIds.includes(user.id)
  const blocked = blockedUserIds.includes(user.id)
  const userPosts = posts.filter((p) => p.userId === user.id)
  const savedPosts = posts.filter((p) => savedPostIds.includes(p.id))

  return (
    <div className="mx-auto max-w-2xl space-y-5 p-4 sm:p-6">
      {/* Header */}
      <div className="overflow-hidden rounded-2xl border border-[var(--border)] bg-[var(--surface)]">
        <div className="h-28 bg-gradient-to-br from-indigo-500 via-violet-600 to-fuchsia-600" />
        <div className="px-5 pb-5">
          <div className="-mt-10 mb-3 flex items-end justify-between">
            <Avatar name={user.name} id={user.id} size="xl" online={user.online} className="rounded-full ring-4 ring-[var(--surface)]" />
            {!isMe && (
              <div className="mb-1 flex gap-2">
                <button
                  onClick={() => {
                    toggleFollow(user.id)
                    pushToast(following ? `Unfollowed ${user.name}` : `Following ${user.name}`, following ? '👋' : '✅')
                  }}
                  className={`rounded-xl px-4 py-2 text-sm font-medium transition-all active:scale-95 ${
                    following ? 'bg-[var(--surface-2)] text-[var(--muted)]' : 'bg-[var(--accent)] text-white hover:bg-[var(--accent-hover)]'
                  }`}
                >
                  {following ? 'Following ✓' : 'Follow'}
                </button>
                <ConnectButton userId={user.id} />
                <button
                  onClick={() => pushToast('Messaging arrives with the real backend 💬')}
                  className="rounded-xl bg-[var(--surface-2)] px-4 py-2 text-sm font-medium transition-colors hover:bg-[var(--accent-soft)]"
                >
                  Message
                </button>
              </div>
            )}
          </div>

          <h1 className="text-xl font-bold">{user.name}</h1>
          <p className="text-sm text-[var(--muted)]">@{user.username} · {user.city}</p>
          <p className="mt-2 text-sm">{user.bio}</p>

          <div className="mt-3 flex flex-wrap gap-1.5">
            {user.interests.map((t) => (
              <Link
                key={t}
                to={`/explore?tag=${t}`}
                className="rounded-full bg-[var(--accent-soft)] px-2.5 py-0.5 text-xs font-medium text-indigo-400 hover:bg-indigo-500/25"
              >
                {t}
              </Link>
            ))}
          </div>

          <div className="mt-4 flex gap-6 text-sm">
            <span><strong>{userPosts.length}</strong> <span className="text-[var(--muted)]">posts</span></span>
            <span><strong>{user.followers.toLocaleString()}</strong> <span className="text-[var(--muted)]">followers</span></span>
            <span><strong>{user.following.toLocaleString()}</strong> <span className="text-[var(--muted)]">following</span></span>
            <span><strong>{user.connections}</strong> <span className="text-[var(--muted)]">connections</span></span>
          </div>
        </div>
      </div>

      {/* Moderation (not on own profile) */}
      {!isMe && (
        <div className="flex justify-end">
          <button
            onClick={() => toggleBlock(user.id)}
            className="text-xs text-[var(--muted)] underline decoration-dotted hover:text-rose-400"
          >
            {blocked ? 'Unblock user' : 'Block user'}
          </button>
        </div>
      )}

      {/* Tabs */}
      <div className="flex gap-1 rounded-xl border border-[var(--border)] bg-[var(--surface)] p-1">
        {(['posts', 'saved', 'about'] as const).map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`flex-1 rounded-lg px-3 py-2 text-sm font-medium capitalize transition-colors ${
              tab === t ? 'bg-[var(--accent-soft)] text-indigo-400' : 'text-[var(--muted)] hover:text-[var(--text)]'
            }`}
          >
            {t}
          </button>
        ))}
      </div>

      {tab === 'posts' && (
        <div className="space-y-4">
          {userPosts.length === 0 ? (
            <p className="rounded-2xl border border-dashed border-[var(--border)] p-10 text-center text-sm text-[var(--muted)]">
              No posts yet.
            </p>
          ) : (
            userPosts.map((p) => <PostCard key={p.id} post={p} />)
          )}
        </div>
      )}

      {tab === 'saved' && (
        <div className="space-y-4">
          {isMe ? (
            savedPosts.length === 0 ? (
              <p className="rounded-2xl border border-dashed border-[var(--border)] p-10 text-center text-sm text-[var(--muted)]">
                Nothing saved yet. Tap 🔖 on any post.
              </p>
            ) : (
              savedPosts.map((p) => <PostCard key={p.id} post={p} />)
            )
          ) : (
            <p className="rounded-2xl border border-dashed border-[var(--border)] p-10 text-center text-sm text-[var(--muted)]">
              Saved posts are private.
            </p>
          )}
        </div>
      )}

      {tab === 'about' && (
        <div className="space-y-4 rounded-2xl border border-[var(--border)] bg-[var(--surface)] p-5 text-sm">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-[var(--muted)]">Profession</p>
            <p className="mt-1">{user.profession}</p>
          </div>
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-[var(--muted)]">City</p>
            <p className="mt-1">{user.city}</p>
          </div>
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-[var(--muted)]">Interests</p>
            <p className="mt-1">{user.interests.join(' · ')}</p>
          </div>
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-[var(--muted)]">Status</p>
            <p className="mt-1">{user.online ? '🟢 Online now' : '⚪ Offline'}</p>
          </div>
        </div>
      )}
    </div>
  )
}
