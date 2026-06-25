"use client"

import { useState, useEffect } from "react"
import { usePathname, useRouter } from "next/navigation"
import Link from "next/link"
import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Sheet, SheetContent } from "@/components/ui/sheet"
import { Logo } from "@/components/logo"
import { usePermissions } from "@/hooks/use-permissions"
import { useAuth } from "@/components/auth-provider"
import type { AdminPermission } from "@/lib/admin-permissions"
import {
  LayoutDashboard,
  Users,
  UserCheck,
  ShoppingCart,
  DollarSign,
  BarChart3,
  Settings,
  Shield,
  FileText,
  MessageSquare,
  TrendingUp,
  LogOut,
  ChevronRight,
  ClipboardList,
  MousePointer,
  BadgeCheck,
  Layers,
  Radio,
  Megaphone,
  ImageIcon,
  Flame,
  Handshake,
  BookOpen,
  Wrench,
  Activity,
} from "lucide-react"

const navigation = [
  {
    name: "Painel",
    href: "/dashboard",
    icon: LayoutDashboard,
    permission: "dashboard",
    exact: true,
  },
  {
    name: "Serviços",
    icon: ClipboardList,
    permission: "dashboard",
    children: [
      { name: "Visão Geral", href: "/dashboard/servicos", icon: ClipboardList, permission: "dashboard" },
      { name: "Catálogo do App", href: "/dashboard/servicos/catalogo-app", icon: Layers, permission: "gerenciarCatalogo" },
      { name: "Combos Promocionais", href: "/dashboard/servicos/combos", icon: Flame, permission: "gerenciarCombos" },
      { name: "Checklists de OS", href: "/dashboard/servicos/checklists", icon: ClipboardList, permission: "gestaoPedidos" },
      { name: "Nichos dos Prestadores", href: "/dashboard/servicos/nichos-prestadores", icon: Users, permission: "aprovarPrestadores" },
    ],
  },
  {
    name: "Controle",
    icon: MousePointer,
    permission: "controle",
    children: [
      { name: "Monitoramento de Chat", href: "/dashboard/controle/chat", icon: MessageSquare, permission: "controle" },
      { name: "Monitoramento de Pedidos", href: "/dashboard/controle/monitoramento", icon: Activity, permission: "gestaoPedidos" },
      { name: "Central Operacional", href: "/dashboard/controle/chat-operacional", icon: Radio, permission: "controle" },
      { name: "Chat com Clientes", href: "/dashboard/controle/chat-clientes", icon: Megaphone, permission: "controle" },
      { name: "Chat com Prestadores", href: "/dashboard/controle/chat-prestadores", icon: Wrench, permission: "controle" },
      { name: "Aceitação de Prestadores", href: "/dashboard/controle/aceitacao-prestadores", icon: BadgeCheck, permission: "aprovarPrestadores" },
      { name: "Especialidades", href: "/dashboard/controle/especialidades", icon: Layers, permission: "aprovarPrestadores" },
    ],
  },
  {
    name: "Gestão de Usuários",
    icon: Users,
    permission: "gestaoUsuarios",
    children: [
      { name: "Pessoas cadastradas", href: "/users/clients", icon: Users, permission: "gestaoUsuarios" },
      { name: "Prestadores", href: "/users/providers", icon: UserCheck, permission: "gestaoUsuarios" },
      { name: "Classificação", href: "/users/classificacao-prestadores", icon: Layers, permission: "gestaoUsuarios" },
    ],
  },
  {
    name: "Gestão de Pedidos",
    icon: ShoppingCart,
    permission: "gestaoPedidos",
    children: [
      { name: "Todos os Pedidos", href: "/orders", icon: ShoppingCart, permission: "gestaoPedidos" },
    ],
  },
  {
    name: "Financeiro",
    icon: DollarSign,
    permission: "financeiro",
    children: [
      { name: "Painel Financeiro", href: "/dashboard/financeiro", icon: BarChart3, permission: "financeiro" },
      { name: "Pagamentos", href: "/dashboard/financeiro/faturamento", icon: FileText, permission: "financeiro" },
    ],
  },
  {
    name: "Relatórios",
    icon: BarChart3,
    permission: "relatorios",
    children: [
      { name: "Central de Relatórios", href: "/reports", icon: TrendingUp, permission: "relatorios" },
    ],
  },
  {
    name: "Configurações",
    icon: Settings,
    permission: "configuracoes",
    children: [
      { name: "Geral", href: "/dashboard/configuracoes", icon: Settings, permission: "configuracoes" },
      { name: "Banners da Home", href: "/dashboard/configuracoes/banners", icon: ImageIcon, permission: "visualizarBanners" },
      { name: "Parceiros AquiResolve", href: "/dashboard/configuracoes/parceiros", icon: Handshake, permission: "gerenciarParceiros" },
    ],
  },
  {
    name: "Manual do Painel",
    href: "/dashboard/manual",
    icon: BookOpen,
    permission: "dashboard",
  },
  {
    name: "Área Master",
    href: "/master",
    icon: Shield,
    permission: "gerenciarAdministradores",
    isMaster: true,
  },
]

interface SidebarProps {
  open: boolean
  setOpen: (open: boolean) => void
}

export function Sidebar({ open, setOpen }: SidebarProps) {
  const pathname = usePathname()
  const [expandedItems, setExpandedItems] = useState<string[]>([])
  const { hasPermission } = usePermissions()
  const { user, logout } = useAuth()
  const router = useRouter()

  useEffect(() => {
    const toExpand: string[] = []
    navigation.forEach(item => {
      if (item.children?.some(c => pathname === c.href || pathname.startsWith(c.href + "/"))) {
        toExpand.push(item.name)
      }
    })
    setExpandedItems(toExpand)
  }, [pathname])

  const toggleExpanded = (name: string) =>
    setExpandedItems(prev =>
      prev.includes(name) ? prev.filter(i => i !== name) : [...prev, name]
    )

  const isActive = (href: string, exact = false) =>
    pathname === href || (!exact && href !== "/" && pathname.startsWith(href + "/"))

  const filteredNavigation = navigation
    .map((item) => item.children
      ? {
          ...item,
          children: item.children.filter((child) =>
            !child.permission || hasPermission(child.permission as AdminPermission)
          ),
        }
      : item
    )
    .filter((item) => item.children
      ? item.children.length > 0
      : !item.permission || hasPermission(item.permission as AdminPermission)
    )

  const userInitial = user?.displayName?.charAt(0) || user?.email?.charAt(0) || "A"
  const userName = user?.displayName || user?.email?.split("@")[0] || "Usuário"

  const SidebarContent = () => (
    <div className="flex h-full flex-col bg-sidebar border-r border-sidebar-border">
      {/* Logo — o fechar (X) no mobile é o nativo do Sheet, não duplicar aqui */}
      <div className="flex h-16 items-center px-5 shrink-0">
        <button
          onClick={() => { router.push("/dashboard"); setOpen(false) }}
          className="flex items-center hover:opacity-75 transition-opacity"
          aria-label="Ir para o dashboard"
        >
          <Logo className="h-8" showText />
        </button>
      </div>

      {/* Nav */}
      <ScrollArea className="flex-1 px-3 py-2">
        <nav className="space-y-0.5 pb-4" aria-label="Navegação principal">
          {filteredNavigation.map(item => (
            <div key={item.name}>
              {item.children ? (
                <div>
                  <button
                    onClick={() => toggleExpanded(item.name)}
                    aria-expanded={expandedItems.includes(item.name)}
                    className={cn(
                      "w-full flex items-center justify-between gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors duration-150",
                      expandedItems.includes(item.name)
                        ? "text-foreground bg-muted"
                        : "text-muted-foreground hover:text-foreground hover:bg-muted/60"
                    )}
                  >
                    <div className="flex items-center gap-3">
                      <item.icon className="h-4 w-4 shrink-0" aria-hidden />
                      <span>{item.name}</span>
                    </div>
                    <ChevronRight
                      className={cn(
                        "h-3.5 w-3.5 shrink-0 transition-transform duration-200",
                        expandedItems.includes(item.name) && "rotate-90"
                      )}
                      aria-hidden
                    />
                  </button>

                  {expandedItems.includes(item.name) && (
                    <div className="ml-4 mt-0.5 space-y-0.5 border-l border-border pl-3 animate-slide-down">
                      {item.children.map(child => (
                        <Link
                          key={child.href}
                          href={child.href}
                          onClick={() => setOpen(false)}
                          aria-current={isActive(child.href) ? "page" : undefined}
                          className={cn(
                            "flex items-center gap-2.5 px-3 py-2 rounded-md text-sm transition-colors duration-150",
                            isActive(child.href)
                              ? "bg-primary text-primary-foreground font-medium"
                              : "text-muted-foreground hover:text-foreground hover:bg-muted/60"
                          )}
                        >
                          <child.icon className="h-3.5 w-3.5 shrink-0" aria-hidden />
                          {child.name}
                        </Link>
                      ))}
                    </div>
                  )}
                </div>
              ) : (
                <Link
                  href={item.href!}
                  onClick={() => setOpen(false)}
                  aria-current={isActive(item.href!) ? "page" : undefined}
                  className={cn(
                    "flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors duration-150",
                    isActive(item.href!, item.exact)
                      ? "bg-primary text-primary-foreground"
                      : "text-muted-foreground hover:text-foreground hover:bg-muted/60",
                    item.isMaster && !isActive(item.href!, item.exact) && "text-muted-foreground/70"
                  )}
                >
                  <item.icon className="h-4 w-4 shrink-0" aria-hidden />
                  {item.name}
                </Link>
              )}
            </div>
          ))}
        </nav>
      </ScrollArea>

      {/* User footer */}
      <div className="border-t border-sidebar-border p-3 shrink-0">
        <div className="flex items-center gap-3 px-2 py-2 rounded-lg hover:bg-muted/50 transition-colors group">
          <div className="h-8 w-8 rounded-full flex items-center justify-center bg-primary text-primary-foreground text-xs font-semibold shrink-0 overflow-hidden">
            {user?.photoURL ? (
              <img src={user.photoURL} alt="" className="h-8 w-8 object-cover" />
            ) : (
              userInitial
            )}
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-semibold text-foreground truncate">{userName}</p>
            <p className="text-xs text-muted-foreground truncate">{user?.email}</p>
          </div>
          <Button
            variant="ghost"
            size="sm"
            onClick={logout}
            className="h-8 w-8 p-0 text-muted-foreground hover:text-destructive hover:bg-destructive/10 rounded-lg opacity-0 group-hover:opacity-100 transition-all"
            aria-label="Sair do sistema"
          >
            <LogOut className="h-3.5 w-3.5" aria-hidden />
          </Button>
        </div>
        <p className="text-[11px] text-muted-foreground/60 text-center mt-2">
          AquiResolve Admin · v3.0
        </p>
      </div>
    </div>
  )

  return (
    <>
      <aside
        className="hidden lg:flex lg:w-64 lg:flex-col lg:fixed lg:inset-y-0 lg:z-30"
        role="complementary"
        aria-label="Menu lateral"
      >
        <SidebarContent />
      </aside>

      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent side="left" className="p-0 w-72" role="dialog" aria-label="Menu lateral">
          <SidebarContent />
        </SheetContent>
      </Sheet>
    </>
  )
}
