"use client"

import { useState, useMemo } from "react"
import {
  TrendingUp, BarChart3, Activity, ArrowUpRight, ArrowDownRight,
  Loader2, RefreshCw, AlertCircle, Zap, Target, Eye
} from "lucide-react"
import { RouteGuard } from "@/components/auth/route-guard"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { cn } from "@/lib/utils"
import { useFinancialAnalytics } from "@/hooks/use-financial-analytics"
import { RevenueAreaChart } from "@/components/charts/revenue-area-chart"
import { DonutChart } from "@/components/charts/donut-chart"
import { StatusBarChart } from "@/components/charts/status-bar-chart"

const PERIOD_OPTIONS = [
  { value: "7d", label: "7 dias" },
  { value: "30d", label: "30 dias" },
  { value: "90d", label: "3 meses" },
  { value: "ano", label: "12 meses" },
]

export default function FinancialAnalyticsPage() {
  const [period, setPeriod] = useState("90d")
  const {
    totalRevenue, totalTransactions, averageTicket, successRate,
    dailyRevenue, paymentMethods, statusBreakdown,
    topServices, loading, error, warning, refetch,
  } = useFinancialAnalytics(period)

  const formatCurrency = (value: number) =>
    new Intl.NumberFormat("pt-BR", {
      style: "currency",
      currency: "BRL",
      notation: "compact",
      maximumFractionDigits: 1,
    }).format(value)

  const formatFullCurrency = (value: number) =>
    new Intl.NumberFormat("pt-BR", {
      style: "currency",
      currency: "BRL",
    }).format(value)

  const paymentMethodChartData = useMemo(
    () => [
      { name: "Crédito", value: paymentMethods.credit_card, color: "#3b82f6" },
      { name: "Débito", value: paymentMethods.debit_card, color: "#8b5cf6" },
      { name: "PIX", value: paymentMethods.pix, color: "#10b981" },
      { name: "Boleto", value: paymentMethods.boleto, color: "#f59e0b" },
    ],
    [paymentMethods]
  )

  const statusChartData = useMemo(
    () => [
      { name: "Pagos", value: statusBreakdown.paid, color: "#10b981" },
      { name: "Pendentes", value: statusBreakdown.pending, color: "#f59e0b" },
      { name: "Falhas", value: statusBreakdown.failed, color: "#ef4444" },
      { name: "Cancelados", value: statusBreakdown.canceled, color: "#6b7280" },
    ],
    [statusBreakdown]
  )

  // Derived metrics
  const pixPercent =
    totalTransactions > 0
      ? ((paymentMethods.pix / totalTransactions) * 100).toFixed(0)
      : "0"

  const dailyAverage =
    dailyRevenue.length > 0
      ? totalRevenue / dailyRevenue.length
      : 0

  const bestDay =
    dailyRevenue.length > 0
      ? dailyRevenue.reduce((best, d) => (d.receita > best.receita ? d : best))
      : null

  return (
    <RouteGuard requiredPermission="financeiro">
      <div className="space-y-6 animate-fade-in">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-xl bg-gradient-to-br from-cyan-400 to-blue-600 flex items-center justify-center shadow-lg shadow-blue-500/20">
              <Activity className="h-5 w-5 text-white" />
            </div>
            <div>
              <h1 className="text-2xl font-bold tracking-tight text-foreground">
                Analytics Financeiro
              </h1>
              <p className="text-sm text-muted-foreground">
                Tendências, comparativos e métricas avançadas
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <Select value={period} onValueChange={setPeriod}>
              <SelectTrigger className="w-[140px]">
                <SelectValue placeholder="Período" />
              </SelectTrigger>
              <SelectContent>
                {PERIOD_OPTIONS.map((opt) => (
                  <SelectItem key={opt.value} value={opt.value}>
                    {opt.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button variant="outline" size="sm" onClick={refetch} disabled={loading}>
              <RefreshCw className={cn("h-4 w-4 mr-1.5", loading && "animate-spin")} />
              Atualizar
            </Button>
          </div>
        </div>

        {/* Warning */}
        {warning && (
          <div className="rounded-lg border border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-950/30 px-4 py-3 text-sm text-amber-800 dark:text-amber-300 flex items-start gap-2">
            <AlertCircle className="h-4 w-4 mt-0.5 shrink-0" />
            {warning}
          </div>
        )}

        {loading && (
          <div className="space-y-4">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="h-[200px] rounded-xl bg-muted animate-pulse" />
            ))}
          </div>
        )}

        {error && (
          <Card className="shadow-card">
            <CardContent className="py-12 text-center">
              <p className="text-sm text-destructive mb-3">{error}</p>
              <Button size="sm" onClick={refetch}>
                <RefreshCw className="h-3.5 w-3.5 mr-1.5" />
                Tentar novamente
              </Button>
            </CardContent>
          </Card>
        )}

        {!loading && !error && (
          <>
            {/* Insight Cards */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
              <InsightCard
                icon={<Zap className="h-4 w-4" />}
                iconBg="bg-amber-100 dark:bg-amber-950/40"
                iconCl="text-amber-600"
                label="% PIX"
                value={`${pixPercent}%`}
                sub="das transações"
              />
              <InsightCard
                icon={<Target className="h-4 w-4" />}
                iconBg="bg-sky-100 dark:bg-sky-950/40"
                iconCl="text-sky-600"
                label="Ticket Médio"
                value={formatFullCurrency(averageTicket)}
                sub="por transação"
              />
              <InsightCard
                icon={<Eye className="h-4 w-4" />}
                iconBg="bg-violet-100 dark:bg-violet-950/40"
                iconCl="text-violet-600"
                label="Média Diária"
                value={formatFullCurrency(dailyAverage)}
                sub={`em ${dailyRevenue.length} dias`}
              />
              <InsightCard
                icon={<TrendingUp className="h-4 w-4" />}
                iconBg="bg-emerald-100 dark:bg-emerald-950/40"
                iconCl="text-emerald-600"
                label="Melhor Dia"
                value={bestDay ? formatFullCurrency(bestDay.receita) : "—"}
                sub={bestDay ? new Date(bestDay.date + "T00:00:00").toLocaleDateString("pt-BR") : "sem dados"}
              />
            </div>

            {/* Full-width Revenue Trend */}
            <RevenueAreaChart
              data={dailyRevenue}
              title="Tendência de Receita"
              height={350}
              loading={loading}
            />

            {/* Payment Methods + Status */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              <DonutChart
                data={paymentMethodChartData}
                title="Distribuição por Método de Pagamento"
                height={300}
                loading={loading}
              />
              <StatusBarChart
                data={statusChartData}
                title="Status das Transações"
                height={300}
                loading={loading}
              />
            </div>

            {/* Top Services Ranking */}
            <Card className="shadow-card">
              <CardHeader className="pb-2">
                <CardTitle className="text-sm font-semibold flex items-center gap-2">
                  <BarChart3 className="h-4 w-4 text-muted-foreground" />
                  Ranking de Serviços por Receita
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="space-y-1">
                  {topServices.map((svc, i) => {
                    const maxRevenue = topServices[0]?.revenue || 1
                    const barWidth = Math.max((svc.revenue / maxRevenue) * 100, 2)
                    return (
                      <div
                        key={svc.name}
                        className="flex items-center gap-3 py-2.5 px-3 rounded-lg hover:bg-muted/30 transition-colors"
                      >
                        <span className="text-xs font-mono text-muted-foreground w-6 shrink-0">
                          #{i + 1}
                        </span>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center justify-between mb-1">
                            <span className="text-sm font-medium text-foreground truncate">
                              {svc.name}
                            </span>
                            <span className="text-sm font-semibold text-foreground tabular-nums ml-2 shrink-0">
                              {formatFullCurrency(svc.revenue)}
                            </span>
                          </div>
                          <div className="h-2 bg-muted rounded-full overflow-hidden">
                            <div
                              className="h-full bg-gradient-to-r from-blue-500 to-blue-400 rounded-full transition-all duration-500"
                              style={{ width: `${barWidth}%` }}
                            />
                          </div>
                          <div className="flex items-center justify-between mt-1">
                            <span className="text-xs text-muted-foreground">
                              {svc.count} pedidos
                            </span>
                            <span className="text-xs text-muted-foreground">
                              {barWidth.toFixed(0)}% do top 1
                            </span>
                          </div>
                        </div>
                      </div>
                    )
                  })}
                  {topServices.length === 0 && (
                    <div className="flex items-center justify-center h-32 text-sm text-muted-foreground">
                      Nenhum dado de serviço disponível
                    </div>
                  )}
                </div>
              </CardContent>
            </Card>
          </>
        )}
      </div>
    </RouteGuard>
  )
}

function InsightCard({
  icon,
  iconBg,
  iconCl,
  label,
  value,
  sub,
}: {
  icon: React.ReactNode
  iconBg: string
  iconCl: string
  label: string
  value: string
  sub: string
}) {
  return (
    <Card className="shadow-card hover:shadow-card-hover transition-shadow">
      <CardContent className="p-5">
        <div className="flex items-start justify-between mb-2">
          <p className="text-xs font-medium text-muted-foreground">{label}</p>
          <div className={cn("h-8 w-8 rounded-lg flex items-center justify-center", iconBg)}>
            <span className={iconCl}>{icon}</span>
          </div>
        </div>
        <p className="text-xl font-bold text-foreground tabular-nums">{value}</p>
        <p className="text-xs text-muted-foreground mt-1">{sub}</p>
      </CardContent>
    </Card>
  )
}
