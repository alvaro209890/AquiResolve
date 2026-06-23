"use client"

import { useState } from "react"
import { cn } from "@/lib/utils"
import { Sidebar } from "./sidebar"
import { Header } from "./header"
import { AdminAccessGuard } from "@/components/auth/admin-access-guard"

interface AppShellProps {
  children: React.ReactNode
  hideSidebar?: boolean
}

export function AppShell({ children, hideSidebar = false }: AppShellProps) {
  const [sidebarOpen, setSidebarOpen] = useState(false)

  return (
    <div className="min-h-screen bg-background overflow-x-hidden flex flex-col w-full max-w-full">
      {!hideSidebar && <Sidebar open={sidebarOpen} setOpen={setSidebarOpen} />}
      <div className={cn("flex-1 min-w-0 overflow-x-hidden flex flex-col", !hideSidebar && "lg:ml-64")}>
        <Header onMenuClick={() => setSidebarOpen(true)} />
        <main className="flex-1 min-w-0 overflow-x-hidden">
          <div className="px-4 sm:px-6 lg:px-8 py-6">
            <AdminAccessGuard>{children}</AdminAccessGuard>
          </div>
        </main>
      </div>
      {/* O overlay do mobile é o nativo do Sheet (sidebar) — não duplicar aqui. */}
    </div>
  )
}
