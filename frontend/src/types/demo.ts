/** Domain models for the demo data layer. Backend types land in Phase 2+. */

export interface User {
  id: string
  username: string
  name: string
  bio: string
  profession: string
  interests: string[]
  /** Approximate location used ONLY to compute distances client-side in this demo. */
  lat: number
  lng: number
  city: string
  online: boolean
  followers: number
  following: number
  connections: number
}

export interface Comment {
  id: string
  userId: string
  text: string
  createdAt: string
}

export interface Post {
  id: string
  userId: string
  text: string
  image?: string
  likes: number
  comments: Comment[]
  createdAt: string
  tags: string[]
  location?: string
  visibility?: 'public' | 'followers' | 'private'
}

export type StoryKind = 'image' | 'text'

export interface Story {
  id: string
  userId: string
  kind: StoryKind
  image?: string
  text?: string
  background?: string
  createdAt: string
  views: number
}

export interface Message {
  id: string
  from: 'me' | string
  text?: string
  kind: 'text' | 'voice' | 'file'
  /** seconds, for voice notes */
  duration?: number
  fileName?: string
  at: string
}

export interface Conversation {
  id: string
  userId: string
  messages: Message[]
  unread: number
}

export interface Channel {
  id: string
  name: string
  kind: 'text' | 'voice'
  topic?: string
  /** simulated member count for voice channels */
  members?: number
}

export interface Community {
  id: string
  name: string
  icon: string
  color: string
  members: number
  description: string
  joined: boolean
  channels: Channel[]
}

export type NotificationKind =
  | 'like'
  | 'comment'
  | 'follow'
  | 'connection_request'
  | 'connection_accepted'
  | 'mention'
  | 'message'

export interface AppNotification {
  id: string
  kind: NotificationKind
  userId: string
  text: string
  at: string
  read: boolean
}

export interface Toast {
  id: number
  text: string
  icon?: string
}
