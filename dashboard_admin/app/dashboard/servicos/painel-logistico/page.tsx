"use client"

import { useMemo } from "react"
import dynamic from "next/dynamic"
import Link from "next/link"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Truck, MapPin, Loader2, Navigation, Clock, User, Eye } from "lucide-react"
import { GoogleMapsLoader } from "@/components/map/google-maps-loader"
import { useOrdersRealtime } from "@/hooks/use-orders-realtime"

const ProvidersMap = dynamic(
  () => import("@/components/map/providers-map").then((m) => ({ default: m.ProvidersMap })),
  { ssr: false, loading: () => <div className="h-full flex items-center justify-center"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div> }
)

// Estados operacionais que representam um serviço "em campo"
const ACTIVE_STATUSES = ["distributing", "assigned", "in_progress"]

const STATUS_META: Record<string, { label: string; cls: string; icon: typeof Navigation }> = {
  distributing: { label: "Em distribuição", cls: "bg-amber-100 text-amber-800", icon: Clock },
  assigned: { label: "Prestador a caminho", cls: "bg-blue-100 text-blue-800", icon: Navigation },
  in_progress: { label: "Em atendimento", cls: "bg-purple-100 text-purple-800", icon: Truck },
  completed: { label: "Finalizado", cls: "bg-emerald-100 text-emerald-800", icon: MapPin },
}

function formatTime(ts: any): string {
  const date = ts?.toDate?.() ?? (ts ? new Date(ts) : null)
  if (!date) return "—"
  return date.toLocaleString("pt-BR", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" })
}

function formatMoney(value: unknown): string {
  const n = Number(value)
  if (!Number.isFinite(n) || n <= 0) return "—"
  return `R$ ${n.toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

export default function PainelLogisticoPage() {
  const { orders, loading } = useOrdersRealtime()

  const activeOrders = useMemo(
    () => orders.filter((o) => ACTIVE_STATUSES.includes(String(o.status))),
    [orders]
  )

  const counts = useMemo(() => {
    const byStatus = (s: string) => orders.filter((o) => String(o.status) === s).length
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    const completedToday = orders.filter((o) => {
      if (String(o.status) !== "completed") return false
      const d = (o as any).completedAt?.toDate?.() ?? o.createdAt?.toDate?.()
      return d ? d >= today : false
    }).length
    return {
      distributing: byStatus("distributing"),
      assigned: byStatus("assigned"),
      inProgress: byStatus("in_progress"),
      completedToday,
    }
  }, [orders])

  return (
    <main className="flex-1 space-y-6 p-6" style={{ backgroundColor: "var(--background)", color: "var(--foreground)" }}>
      <div className="flex items-center gap-3">
        <div className="h-9 w-9 rounded-lg bg-primary/10 flex items-center justify-center">
          <Truck className="h-5 w-5 text-primary" />
        </div>
        <div>
          <h1 className="text-2xl font-bold text-foreground">Painel Logístico em Tempo Real</h1>
          <p className="text-sm text-muted-foreground">
            Acompanhamento ao vivo dos serviços em campo — atualiza automaticamente
          </p>
        </div>
      </div>

      {/* KPIs operacionais */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        {[
          { label: "Em distribuição", value: counts.distributing, cls: "text-amber-600", bg: "bg-amber-50 dark:bg-amber-950/30" },
          { label: "A caminho", value: counts.assigned, cls: "text-blue-600", bg: "bg-blue-50 dark:bg-blue-950/30" },
          { label: "Em atendimento", value: counts.inProgress, cls: "text-purple-600", bg: "bg-purple-50 dark:bg-purple-950/30" },
          { label: "Finalizados hoje", value: counts.completedToday, cls: "text-emerald-600", bg: "bg-emerald-50 dark:bg-emerald-950/30" },
        ].map((kpi) => (
          <Card key={kpi.label} className="shadow-card">
            <CardContent className={`p-5 ${kpi.bg} rounded-lg`}>
              <p className="text-xs font-medium text-muted-foreground">{kpi.label}</p>
              <p className={`text-2xl font-bold tabular-nums mt-1 ${kpi.cls}`}>{loading ? "—" : kpi.value}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Mapa ao vivo */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <MapPin className="h-5 w-5" />
            Mapa de prestadores em tempo real
          </CardTitle>
          <CardDescription>Prestadores online com GPS ativo</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="rounded-xl overflow-hidden border" style={{ height: 420 }}>
            <GoogleMapsLoader />
            <ProvidersMap />
          </div>
        </CardContent>
      </Card>

      {/* Serviços ativos */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Truck className="h-5 w-5" />
            Serviços em campo
          </CardTitle>
          <CardDescription>{activeOrders.length} serviço(s) ativo(s) no momento</CardDescription>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="flex items-center justify-center py-12">
              <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
              <span className="ml-3 text-sm text-muted-foreground">Carregando serviços...</span>
            </div>
          ) : activeOrders.length === 0 ? (
            <div className="py-12 text-center text-sm text-muted-foreground">
              Nenhum serviço em campo no momento.
            </div>
          ) : (
            <div className="space-y-3">
              {activeOrders.map((order) => {
                const o = order as any
                const meta = STATUS_META[String(order.status)] ?? STATUS_META.distributing
                const Icon = meta.icon
                return (
                  <div
                    key={order.id}
                    className="flex flex-col gap-3 rounded-lg border p-4 sm:flex-row sm:items-center sm:justify-between"
                  >
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <Badge className={meta.cls}>
                          <Icon className="mr-1 h-3 w-3" />
                          {meta.label}
                        </Badge>
                        {o.protocol ? <span className="text-xs text-muted-foreground">#{o.protocol}</span> : null}
                      </div>
                      <p className="font-medium text-foreground">{o.serviceName || o.serviceType || "Serviço"}</p>
                      <div className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground">
                        <span className="flex items-center gap-1"><User className="h-3.5 w-3.5" /> {order.clientName || "Cliente"}</span>
                        <span className="flex items-center gap-1"><Truck className="h-3.5 w-3.5" /> {o.assignedProviderName || o.providerName || "Não atribuído"}</span>
                        <span className="flex items-center gap-1"><MapPin className="h-3.5 w-3.5" /> {order.address || "Sem endereço"}</span>
                        <span className="flex items-center gap-1"><Clock className="h-3.5 w-3.5" /> {formatTime(order.createdAt)}</span>
                      </div>
                    </div>
                    <div className="flex items-center gap-3 sm:flex-col sm:items-end">
                      <span className="font-semibold text-foreground">{formatMoney(o.finalPrice ?? o.estimatedPrice)}</span>
                      <Link href={`/dashboard/servicos/os/${order.id}`}>
                        <Button variant="outline" size="sm" className="gap-1.5">
                          <Eye className="h-3.5 w-3.5" /> Ver OS
                        </Button>
                      </Link>
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </CardContent>
      </Card>
    </main>
  )
}
