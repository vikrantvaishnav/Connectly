import { Outlet } from 'react-router-dom'
import { BottomNav } from '../components/BottomNav'
import { Sidebar } from '../components/Sidebar'
import { TopBar } from '../components/TopBar'

export function AppLayout() {
  return (
    <div className="min-h-screen bg-[var(--bg)] text-[var(--text)]">
      <TopBar />
      <div className="flex">
        <Sidebar />
        <main className="min-w-0 flex-1 pb-16 md:pb-0">
          <Outlet />
        </main>
      </div>
      <BottomNav />
    </div>
  )
}
