import { useEffect, lazy, Suspense } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuthStore } from './store/authStore'
import { AppLayout } from './layouts/AppLayout'
import { Toaster } from './components/Toaster'

// Route-level code splitting: each page ships in its own chunk, so the first
// paint only downloads what the visitor's route needs (the initial bundle was
// 525 kB — most of it code for pages a user may never open).
const HomePage = lazy(() => import('./pages/HomePage').then(m => ({ default: m.HomePage })))
const ExplorePage = lazy(() => import('./pages/ExplorePage').then(m => ({ default: m.ExplorePage })))
const DiscoverPage = lazy(() => import('./pages/DiscoverPage').then(m => ({ default: m.DiscoverPage })))
const NearbyPage = lazy(() => import('./pages/NearbyPage').then(m => ({ default: m.NearbyPage })))
const RequestsPage = lazy(() => import('./pages/RequestsPage').then(m => ({ default: m.RequestsPage })))
const MessagesPage = lazy(() => import('./pages/MessagesPage').then(m => ({ default: m.MessagesPage })))
const CommunitiesPage = lazy(() => import('./pages/CommunitiesPage').then(m => ({ default: m.CommunitiesPage })))
const CommunityDetailPage = lazy(() => import('./pages/CommunityDetailPage').then(m => ({ default: m.CommunityDetailPage })))
const NotificationsPage = lazy(() => import('./pages/NotificationsPage').then(m => ({ default: m.NotificationsPage })))
const ProfilePage = lazy(() => import('./pages/ProfilePage').then(m => ({ default: m.ProfilePage })))
const SettingsPage = lazy(() => import('./pages/SettingsPage').then(m => ({ default: m.SettingsPage })))
const SecuritySettingsPage = lazy(() => import('./pages/SecuritySettingsPage').then(m => ({ default: m.SecuritySettingsPage })))
const PostDetailPage = lazy(() => import('./pages/PostDetailPage').then(m => ({ default: m.PostDetailPage })))
const VoiceRoomsPanel = lazy(() => import('./components/VoiceRooms').then(m => ({ default: m.VoiceRoomsPanel })))
const LoginPage = lazy(() => import('./pages/auth/LoginPage').then(m => ({ default: m.LoginPage })))
const RegisterPage = lazy(() => import('./pages/auth/RegisterPage').then(m => ({ default: m.RegisterPage })))
const ForgotPasswordPage = lazy(() => import('./pages/auth/ForgotPasswordPage').then(m => ({ default: m.ForgotPasswordPage })))
const ResetPasswordPage = lazy(() => import('./pages/auth/ResetPasswordPage').then(m => ({ default: m.ResetPasswordPage })))
const VerifyEmailPage = lazy(() => import('./pages/auth/VerifyEmailPage').then(m => ({ default: m.VerifyEmailPage })))
const OAuthCallbackPage = lazy(() => import('./pages/auth/OAuthCallbackPage').then(m => ({ default: m.OAuthCallbackPage })))

function RouteFallback() {
  return (
    <div className="flex min-h-[50vh] items-center justify-center text-sm text-[var(--muted)]">
      Loading…
    </div>
  )
}

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      // Keep visited pages' data hot: navigating back is instant instead of
      // re-hitting the API, which matters a lot on Render's free tier.
      staleTime: 60_000,
      gcTime: 5 * 60_000,
      // Refetching everything on every tab focus made the app feel sluggish.
      refetchOnWindowFocus: false,
    },
  },
})

export default function App() {
  // Restore the session once for the whole app (survives page reloads on any route).
  useEffect(() => {
    useAuthStore.getState().bootstrap()
  }, [])

  return (
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={<RouteFallback />}>
      <Routes>
        {/* Auth routes (no sidebar layout) */}
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/verify-email" element={<Navigate to="/verify-email/pending" replace />} />
        <Route path="/verify-email/pending" element={<VerifyEmailPage />} />
        <Route path="/verify-email/:token" element={<VerifyEmailPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password/:token" element={<ResetPasswordPage />} />
        <Route path="/reset-password" element={<Navigate to="/forgot-password" replace />} />
        <Route path="/oauth/callback" element={<OAuthCallbackPage />} />

        {/* Main application routes */}
        <Route element={<AppLayout />}>
          <Route path="/" element={<Navigate to="/home" replace />} />
          <Route path="/home" element={<HomePage />} />
          <Route path="/explore" element={<ExplorePage />} />
          <Route path="/nearby" element={<NearbyPage />} />
          <Route path="/discover" element={<DiscoverPage />} />
          <Route path="/requests" element={<RequestsPage />} />
          <Route path="/voice" element={<VoiceRoomsPanel />} />
          <Route path="/messages" element={<MessagesPage />} />
          <Route path="/messages/:conversationId" element={<MessagesPage />} />
          <Route path="/communities" element={<CommunitiesPage />} />
          <Route path="/communities/:communityId" element={<CommunityDetailPage />} />
          <Route path="/notifications" element={<NotificationsPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/profile/:username" element={<ProfilePage />} />
          <Route path="/post/:postId" element={<PostDetailPage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/settings/security" element={<SecuritySettingsPage />} />
        </Route>

        {/* Catch-all */}
        <Route path="*" element={<Navigate to="/home" replace />} />
      </Routes>
      </Suspense>
      <Toaster />
    </QueryClientProvider>
  )
}
