"use client"

import Link from "next/link"
import { useTheme } from "next-themes"
import { useAuth } from "@/components/auth-provider"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Logo } from "@/components/logo"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { NotificationBell } from "@/components/layout/notification-bell"
import { LogOut, User, Settings, Search, Menu, Moon, Sun } from "lucide-react"

interface HeaderProps {
  onMenuClick?: () => void
}

export function Header({ onMenuClick }: HeaderProps) {
  const { user, logout } = useAuth()
  const { theme, setTheme } = useTheme()

  const userInitial = user?.displayName?.charAt(0) || user?.email?.charAt(0) || "A"
  const userName = user?.displayName || user?.email?.split("@")[0] || "Admin"

  return (
    <header className="bg-card border-b border-border sticky top-0 z-20 h-16">
      <div className="flex items-center justify-between h-full px-4 lg:px-6">

        {/* Left — mobile menu + logo */}
        <div className="flex items-center gap-3">
          <Button
            variant="ghost"
            size="sm"
            onClick={onMenuClick}
            className="lg:hidden h-9 w-9 p-0 text-muted-foreground hover:text-foreground"
            aria-label="Abrir menu"
          >
            <Menu className="h-5 w-5" />
          </Button>
          <Link href="/dashboard" className="lg:hidden hover:opacity-80 transition-opacity">
            <Logo className="h-7" showText />
          </Link>
        </div>

        {/* Center — search */}
        <div className="flex-1 max-w-sm mx-4 hidden md:block">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground h-4 w-4 pointer-events-none" />
            <Input
              placeholder="Buscar..."
              className="pl-9 h-9 bg-muted/50 border-transparent focus:border-border focus:bg-background text-sm transition-colors"
            />
          </div>
        </div>

        {/* Right — actions */}
        <div className="flex items-center gap-1">
          {/* Mobile search */}
          <Button
            variant="ghost"
            size="sm"
            className="md:hidden h-9 w-9 p-0 text-muted-foreground hover:text-foreground"
            aria-label="Buscar"
          >
            <Search className="h-4 w-4" />
          </Button>

          {/* Notification bell */}
          <NotificationBell />

          {/* Dark mode toggle */}
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
            className="h-9 w-9 p-0 text-muted-foreground hover:text-foreground relative"
            aria-label="Alternar tema"
          >
            <Sun className="h-4 w-4 rotate-0 scale-100 transition-all dark:-rotate-90 dark:scale-0" />
            <Moon className="absolute h-4 w-4 rotate-90 scale-0 transition-all dark:rotate-0 dark:scale-100" />
          </Button>

          {/* User menu */}
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant="ghost"
                size="sm"
                className="h-9 w-9 p-0 rounded-full ml-0.5"
                aria-label="Menu do usuário"
              >
                <Avatar className="h-8 w-8">
                  <AvatarImage src={user?.photoURL || undefined} alt={userName} />
                  <AvatarFallback className="bg-primary text-primary-foreground text-xs font-semibold">
                    {userInitial}
                  </AvatarFallback>
                </Avatar>
              </Button>
            </DropdownMenuTrigger>

            <DropdownMenuContent align="end" className="w-56 animate-scale-in">
              <DropdownMenuLabel className="pb-2">
                <p className="text-sm font-semibold text-foreground">{userName}</p>
                <p className="text-xs text-muted-foreground font-normal mt-0.5 truncate">{user?.email}</p>
              </DropdownMenuLabel>

              <DropdownMenuSeparator />

              <DropdownMenuItem asChild>
                <Link href="/dashboard/configuracoes" className="cursor-pointer">
                  <User className="mr-2.5 h-4 w-4" />
                  Perfil
                </Link>
              </DropdownMenuItem>

              <DropdownMenuItem asChild>
                <Link href="/dashboard/configuracoes" className="cursor-pointer">
                  <Settings className="mr-2.5 h-4 w-4" />
                  Configurações
                </Link>
              </DropdownMenuItem>

              <DropdownMenuSeparator />

              <DropdownMenuItem
                onClick={logout}
                className="text-destructive focus:text-destructive focus:bg-destructive/10 cursor-pointer"
              >
                <LogOut className="mr-2.5 h-4 w-4" />
                Sair
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>
    </header>
  )
}
