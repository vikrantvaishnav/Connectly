import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import {
  communities as seedCommunities,
  conversations as seedConversations,
  notifications as seedNotifications,
  posts as seedPosts,
  users as seedUsers,
  CURRENT_USER_ID,
} from '../data/demo'
import type {
  AppNotification,
  Community,
  Conversation,
  Post,
  Toast,
  User,
} from '../types/demo'

export type ConnectionStatus = 'none' | 'request_sent' | 'request_received' | 'connected'

type GeoStatus = 'idle' | 'prompt' | 'granted' | 'denied' | 'unavailable'

interface AppState {
  users: User[]
  posts: Post[]
  communities: Community[]
  conversations: Conversation[]
  notifications: AppNotification[]
  savedPostIds: string[]
  likedPostIds: string[]
  followedUserIds: string[]
  connectionStatuses: Record<string, ConnectionStatus>
  blockedUserIds: string[]
  toasts: Toast[]
  theme: 'dark' | 'light'
  geo: { status: GeoStatus; lat: number; lng: number; accuracyKm: number }
  replyTo: { conversationId: string; messageId: string } | null

  // interactions
  addPost: (text: string, tags: string[], visibility: 'public' | 'followers' | 'private') => void
  toggleLike: (postId: string) => void
  addComment: (postId: string, text: string) => void
  toggleSave: (postId: string) => void
  toggleFollow: (userId: string) => void
  sendConnectionRequest: (userId: string) => void
  acceptConnection: (userId: string) => void
  toggleBlock: (userId: string) => void
  toggleCommunity: (communityId: string) => void
  sendMessage: (conversationId: string, text: string) => void
  markConversationRead: (conversationId: string) => void
  markAllNotificationsRead: () => void
  setReplyTo: (r: AppState['replyTo']) => void
  pushToast: (text: string, icon?: string) => void
  dismissToast: (id: number) => void
  toggleTheme: () => void
  setGeo: (g: AppState['geo']) => void
}

let toastId = 0

/** Canned demo replies so chat feels alive without a backend. */
const REPLIES = [
  'Haha, fair point 😄',
  'On it — give me a sec',
  'That’s exactly what I was thinking!',
  'Let’s hop on a voice channel?',
  'Sending you the files now 📎',
  'Nice nice. Coffee later? ☕',
]

function simulateReply(conversationId: string, otherUserId: string) {
  const delay = 1200 + Math.random() * 1800
  setTimeout(() => {
    useAppStore.setState((s) => ({
      conversations: s.conversations.map((c) =>
        c.id !== conversationId
          ? c
          : {
              ...c,
              messages: [
                ...c.messages,
                {
                  id: `m${Date.now()}`,
                  from: otherUserId,
                  kind: 'text' as const,
                  text: REPLIES[Math.floor(Math.random() * REPLIES.length)],
                  at: new Date().toISOString(),
                },
              ],
            },
      ),
    }))
  }, delay)
}

export const useAppStore = create<AppState>()(
  persist(
    (set, get) => ({
      users: seedUsers,
      posts: seedPosts,
      communities: seedCommunities,
      conversations: seedConversations,
      notifications: seedNotifications,
      savedPostIds: ['p2'],
      likedPostIds: [],
      followedUserIds: ['u1', 'u2'],
      connectionStatuses: { u1: 'connected', u8: 'request_received' },
      blockedUserIds: [],
      toasts: [],
      theme: 'dark',
      geo: { status: 'idle', lat: 19.1176, lng: 72.906, accuracyKm: 0.5 },
      replyTo: null,

      addPost: (text, tags, visibility) =>
        set((s) => ({
          posts: [
            {
              id: `p${Date.now()}`,
              userId: CURRENT_USER_ID,
              text,
              likes: 0,
              comments: [],
              createdAt: new Date().toISOString(),
              tags,
              visibility,
            },
            ...s.posts,
          ],
        })),

      toggleLike: (postId) =>
        set((s) => {
          const liked = s.likedPostIds.includes(postId)
          return {
            likedPostIds: liked
              ? s.likedPostIds.filter((id) => id !== postId)
              : [...s.likedPostIds, postId],
            posts: s.posts.map((p) =>
              p.id === postId ? { ...p, likes: p.likes + (liked ? -1 : 1) } : p,
            ),
          }
        }),

      addComment: (postId, text) =>
        set((s) => ({
          posts: s.posts.map((p) =>
            p.id === postId
              ? {
                  ...p,
                  comments: [
                    ...p.comments,
                    { id: `c${Date.now()}`, userId: CURRENT_USER_ID, text, createdAt: new Date().toISOString() },
                  ],
                }
              : p,
          ),
        })),

      toggleSave: (postId) =>
        set((s) => {
          const saved = s.savedPostIds.includes(postId)
          get().pushToast(saved ? 'Removed from saved' : 'Post saved', saved ? '🗑️' : '🔖')
          return {
            savedPostIds: saved
              ? s.savedPostIds.filter((id) => id !== postId)
              : [...s.savedPostIds, postId],
          }
        }),

      toggleFollow: (userId) =>
        set((s) => {
          const following = s.followedUserIds.includes(userId)
          return {
            followedUserIds: following
              ? s.followedUserIds.filter((id) => id !== userId)
              : [...s.followedUserIds, userId],
          }
        }),

      sendConnectionRequest: (userId) => {
        set((s) => ({ connectionStatuses: { ...s.connectionStatuses, [userId]: 'request_sent' } }))
        get().pushToast('Connection request sent', '🤝')
        // Demo: the other side "accepts" after a moment
        setTimeout(() => {
          useAppStore.setState((s) => ({
            connectionStatuses: { ...s.connectionStatuses, [userId]: 'connected' },
          }))
          useAppStore.getState().pushToast(`${useAppStore.getState().users.find((u) => u.id === userId)?.name ?? 'They'} accepted your request!`, '🎉')
        }, 3500)
      },

      acceptConnection: (userId) => {
        set((s) => ({ connectionStatuses: { ...s.connectionStatuses, [userId]: 'connected' } }))
        get().pushToast('You are now connected', '🤝')
      },

      toggleBlock: (userId) =>
        set((s) => {
          const blocked = s.blockedUserIds.includes(userId)
          get().pushToast(blocked ? 'User unblocked' : 'User blocked', blocked ? '🔓' : '🚫')
          return {
            blockedUserIds: blocked
              ? s.blockedUserIds.filter((id) => id !== userId)
              : [...s.blockedUserIds, userId],
          }
        }),

      toggleCommunity: (communityId) =>
        set((s) => ({
          communities: s.communities.map((c) =>
            c.id === communityId ? { ...c, joined: !c.joined, members: c.members + (c.joined ? -1 : 1) } : c,
          ),
        })),

      sendMessage: (conversationId, text) => {
        const conv = get().conversations.find((c) => c.id === conversationId)
        if (!conv) return
        set((s) => ({
          conversations: s.conversations.map((c) =>
            c.id === conversationId
              ? {
                  ...c,
                  messages: [
                    ...c.messages,
                    { id: `m${Date.now()}`, from: 'me' as const, kind: 'text' as const, text, at: new Date().toISOString() },
                  ],
                }
              : c,
          ),
        }))
        simulateReply(conversationId, conv.userId)
      },

      markConversationRead: (conversationId) =>
        set((s) => ({
          conversations: s.conversations.map((c) =>
            c.id === conversationId ? { ...c, unread: 0 } : c,
          ),
        })),

      markAllNotificationsRead: () =>
        set((s) => ({
          notifications: s.notifications.map((n) => ({ ...n, read: true })),
        })),

      setReplyTo: (r) => set({ replyTo: r }),

      pushToast: (text, icon) => {
        const id = ++toastId
        set((s) => ({ toasts: [...s.toasts, { id, text, icon }] }))
        setTimeout(() => get().dismissToast(id), 2600)
      },

      dismissToast: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),

      toggleTheme: () =>
        set((s) => ({ theme: s.theme === 'dark' ? 'light' : 'dark' })),

      setGeo: (g) => set({ geo: g }),
    }),
    {
      name: 'connectly-demo',
      partialize: (s) => ({
        posts: s.posts,
        likedPostIds: s.likedPostIds,
        savedPostIds: s.savedPostIds,
        followedUserIds: s.followedUserIds,
        connectionStatuses: s.connectionStatuses,
        blockedUserIds: s.blockedUserIds,
        communities: s.communities,
        conversations: s.conversations,
        notifications: s.notifications,
        theme: s.theme,
      }),
    },
  ),
)

// Apply theme class whenever theme changes
if (typeof document !== 'undefined') {
  const apply = (t: 'dark' | 'light') => {
    document.documentElement.classList.toggle('dark', t === 'dark')
  }
  apply(useAppStore.getState().theme)
  useAppStore.subscribe((s, prev) => {
    if (s.theme !== prev.theme) apply(s.theme)
  })
}

export function getUser(id: string): User | undefined {
  return useAppStore.getState().users.find((u) => u.id === id)
}
