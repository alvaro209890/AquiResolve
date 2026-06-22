"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { cn } from "@/lib/utils"
import {
  AlertCircle, ClipboardList, Clock, DollarSign,
  TrendingUp, UserPlus, Users, CheckCircle,
  Award, Wallet, Star,
} from "lucide-react"
import { FirestoreAnalyticsService } from "@/lib/services/firestore-analytics-simple"
import { Card, CardContent } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"

interface Metric {
  title: string
  value: string
  description: string
  icon: typeof ClipboardList
  variant: "orange" | "blue" | "emerald" | "violet" | "red" | "slate" | "amber" | "teal"
}

const variantStyles = {
  orange:  { icon: "text-orange-600",  bg: "bg-orange-50  dark:bg-orange-950/40" },
  blue:    { icon: "text-blue-600",    bg: "bg-blue-50    dark:bg-blue-950/40"   },
  emerald: { icon: "text-emerald-600", bg: "bg-emerald-50 dark:bg-emerald-950/40"},
  violet:  { icon: "text-violet-600",  bg: "bg-violet-50  dark:bg-violet-950/40" },
  red:     { icon: "text-red-600",     bg: "bg-red-50     dark:bg-red-950/40"    },
  slate:   { icon: "text-muted-foreground",   bg: "bg-muted"  },
  amber:   { icon: "text-amber-600",   bg: "bg-amber-50   dark:bg-amber-950/40"  },
  teal:    { icon: "text-teal-600",    bg: "bg-teal-50    dark:bg-teal-950/40"   },
}

export function DashboardMetrics() {
  const [data, setData] = useState<any>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetch = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      setData(await FirestoreAnalyticsService.getDashboardMetrics())
    } catch {
      setError("Não foi possível carregar as métricas")
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { fetch() }, [fetch])

  const metrics: Metric[] = useMemo(() => {
    if (!data) return []
    const o = data.orders ?? {}
    const u = data.users ?? {}
    const p = data.providers ?? {}
    return [
      { title: "Total de Pedidos",      value: (o.totalOrders          ?? 0).toLocaleString("pt-BR"), icon: ClipboardList, description: "Pedidos na coleção orders",        variant: "orange"  },
      { title: "Pedidos em Aberto",     value: (o.activeOrders         ?? 0).toLocaleString("pt-BR"), icon: Clock,         description: "Pedidos ativos no momento",       variant: "blue"    },
      { title: "Usuários Ativos",       value: (u.activeUsers          ?? 0).toLocaleString("pt-BR"), icon: Users,         description: "Cadastros marcados como ativos",  variant: "emerald" },
      { title: "Novos Usuários",        value: (u.newUsersLast30Days   ?? 0).toLocaleString("pt-BR"), icon: UserPlus,      description: "Criados nos últimos 30 dias",     variant: "violet"  },
      { title: "Pedidos Urgentes",      value: (o.emergencyOrders      ?? 0).toLocaleString("pt-BR"), icon: AlertCircle,   description: "Marcados como emergência",        variant: "red"     },
      { title: "Pedidos Cancelados",    value: (o.cancelledOrders      ?? 0).toLocaleString("pt-BR"), icon: Clock,         description: "Cancelados no histórico atual",   variant: "slate"   },
      { title: "Prestadores Aprovados", value: (p.approvedVerifications ?? 0).toLocaleString("pt-BR"), icon: CheckCircle,   description: "Verificações aprovadas",         variant: "amber"   },
      { title: "Taxa de Aprovação",     value: `${Math.round(p.approvalRate ?? 0)}%`,                  icon: TrendingUp,    description: "Sobre o total de verificações",  variant: "teal"    },
      {
        title: "Receita Total",
        value: (o.totalRevenue ?? 0).toLocaleString("pt-BR", { style: "currency", currency: "BRL" }),
        icon: DollarSign,
        description: "Soma dos pedidos concluídos",
        variant: "emerald",
      },
      {
        title: "Receita (30 dias)",
        value: (o.revenueLast30Days ?? 0).toLocaleString("pt-BR", { style: "currency", currency: "BRL" }),
        icon: DollarSign,
        description: "Pedidos concluídos nos últimos 30 dias",
        variant: "teal",
      },
      {
        title: "Cashback Distribuído",
        value: (o.totalCashbackDistributed ?? 0).toLocaleString("pt-BR", { style: "currency", currency: "BRL" }),
        icon: Award,
        description: "Créditos liquidados em order_settlements",
        variant: "amber",
      },
      {
        title: "Comissões Liquidadas",
        value: (o.totalProviderCommission ?? 0).toLocaleString("pt-BR", { style: "currency", currency: "BRL" }),
        icon: Wallet,
        description: "Repasse acumulado dos prestadores",
        variant: "violet",
      },
      {
        title: "Avaliação Média",
        value: (o.averageRating ?? 0) > 0
          ? `${(o.averageRating as number).toFixed(1)} ★`
          : "—",
        icon: Star,
        description: `Média de ${(o.totalRated ?? 0).toLocaleString("pt-BR")} avaliações`,
        variant: "amber",
      },
      {
        title: "Avaliações Recebidas",
        value: (o.totalRated ?? 0).toLocaleString("pt-BR"),
        icon: Star,
        description: "Pedidos avaliados pelos clientes",
        variant: "teal",
      },
    ]
  }, [data])

  if (loading) {
    return (
      <div className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
        {Array.from({ length: 8 }).map((_, i) => (
          <Card key={i} className="shadow-card">
            <CardContent className="p-5">
              <div className="flex items-start justify-between mb-4">
                <Skeleton className="h-4 w-28 animate-skeleton" />
                <Skeleton className="h-9 w-9 rounded-lg animate-skeleton" />
              </div>
              <Skeleton className="h-8 w-16 mb-2 animate-skeleton" />
              <Skeleton className="h-3 w-32 animate-skeleton" />
            </CardContent>
          </Card>
        ))}
      </div>
    )
  }

  if (error || !data) {
    return (
      <Card className="shadow-card">
        <CardContent className="p-10 text-center">
          <div className="h-10 w-10 rounded-full bg-destructive/10 flex items-center justify-center mx-auto mb-3">
            <AlertCircle className="h-5 w-5 text-destructive" />
          </div>
          <p className="text-sm font-medium text-foreground">Erro ao carregar métricas</p>
          <p className="text-xs text-muted-foreground mt-1">{error}</p>
        </CardContent>
      </Card>
    )
  }

  return (
    <div className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
      {metrics.map((m) => {
        const s = variantStyles[m.variant]
        const Icon = m.icon
        return (
          <Card
            key={m.title}
            className="shadow-card hover:shadow-card-hover transition-shadow duration-200 group"
          >
            <CardContent className="p-5">
              <div className="flex items-start justify-between mb-3">
                <p className="text-xs font-medium text-muted-foreground leading-snug pr-2">
                  {m.title}
                </p>
                <div className={cn("h-9 w-9 rounded-lg flex items-center justify-center shrink-0", s.bg)}>
                  <Icon className={cn("h-4 w-4", s.icon)} aria-hidden />
                </div>
              </div>
              <p className="text-2xl font-bold text-foreground tabular-nums">{m.value}</p>
              <p className="text-xs text-muted-foreground mt-1.5 leading-snug">{m.description}</p>
            </CardContent>
          </Card>
        )
      })}
    </div>
  )
}
