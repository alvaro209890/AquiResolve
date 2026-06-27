"use client"

import { useState, useMemo } from "react"
import {
  AlertCircle, CreditCard, DollarSign, Loader2, PieChart, TrendingUp,
  RefreshCw, Users, Building2, Wallet, Receipt, ShoppingCart,
  BarChart3, TicketPercent, ArrowUpRight, ArrowDownRight, TrendingDown,
  ChevronDown
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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { cn } from "@/lib/utils"
import { useFinancialAnalytics } from "@/hooks/use-financial-analytics"
import { RevenueAreaChart } from "@/components/charts/revenue-area-chart"
import { DonutChart } from "@/components/charts/donut-chart"
import { StatusBarChart } from "@/components/charts/status-bar-chart"

const PERIOD_OPTIONS = [
  { value: "7d", label: "7 dias" },
  { value: "30d", label: "30 dias" },
  { value: "90d", label: "90 dias" },
  { value: "mes", label: "Este mês" },
  { value: "ano", label: "Este ano" },
]

const PAYMENT_COLORS = {
  credit_card: "#3b82f6",
  debit_card: "#8b5cf6",
  pix: "#10b981",
  boleto: "#f59e0b",
}

const STATUS_COLORS: Record<string, string> = {
  paid: "#10b981",
  pending: "#f59e0b",
  failed: "#ef4444",
  canceled: "#6b7280",
}

export default function FinanceiroPage() {
  const [period, setPeriod] = useState("30d")
  const {
    totalRevenue, totalTransactions, averageTicket, successRate,
    totalCustomers, totalProviders, platformCommission, cashbackDistributed,
    pendingProviderPayouts, dailyRevenue, paymentMethods, statusBreakdown,
    topServices, recentTransactions, loading, error, warning, refetch,
  } = useFinancialAnalytics(period)

  const formatCurrency = (value: number) =>
    new Intl.NumberFormat("pt-BR", {
      style: "currency",
      currency: "BRL",
    }).format(Number.isFinite(value) ? value : 0)

  const formatCompactCurrency = (value: number) =>
    new Intl.NumberFormat("pt-BR", {
      style: "currency",
      currency: "BRL",
      notation: "compact",
      maximumFractionDigits: 1,
    }).format(Number.isFinite(value) ? value : 0)

  const formatNumber = (value: number) =>
    new Intl.NumberFormat("pt-BR").format(value)

  // Donut chart data
  const paymentMethodChartData = useMemo(
    () => [
      { name: "Crédito", value: paymentMethods.credit_card, color: PAYMENT_COLORS.credit_card },
      { name: "Débito", value: paymentMethods.debit_card, color: PAYMENT_COLORS.debit_card },
      { name: "PIX", value: paymentMethods.pix, color: PAYMENT_COLORS.pix },
      { name: "Boleto", value: paymentMethods.boleto, color: PAYMENT_COLORS.boleto },
    ],
    [paymentMethods]
  )

  // Status bar chart data
  const statusChartData = useMemo(
    () => [
      { name: "Pagos", value: statusBreakdown.paid, color: STATUS_COLORS.paid },
      { name: "Pendentes", value: statusBreakdown.pending, color: STATUS_COLORS.pending },
      { name: "Falhas", value: statusBreakdown.failed, color: STATUS_COLORS.failed },
      { name: "Cancelados", value: statusBreakdown.canceled, color: STATUS_COLORS.canceled },
    ],
    [statusBreakdown]
  )

  const getStatusBadge = (status: string) => {
    const map: Record<string, { label: string; variant: "default" | "secondary" | "destructive" | "outline" }> = {
      completed: { label: "Pago", variant: "default" },
      pending: { label: "Pendente", variant: "secondary" },
      failed: { label: "Falhou", variant: "destructive" },
      canceled: { label: "Cancelado", variant: "outline" },
      cancelled: { label: "Cancelado", variant: "outline" },
    }
    const info = map[status?.toLowerCase()] || { label: status || "—", variant: "outline" as const }
    return <Badge variant={info.variant}>{info.label}</Badge>
  }

  const revenueMargin = totalRevenue > 0
    ? ((totalRevenue - pendingProviderPayouts) / totalRevenue) * 100
    : 0

  return (
    <RouteGuard requiredPermission="financeiro">
      <div className="space-y-6 animate-fade-in">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-xl bg-gradient-to-br from-emerald-400 to-emerald-600 flex items-center justify-center shadow-lg shadow-emerald-500/20">
              <DollarSign className="h-5 w-5 text-white" />
            </div>
            <div>
              <h1 className="text-2xl font-bold tracking-tight text-foreground">
                Financeiro
              </h1>
              <p className="text-sm text-muted-foreground">
                Dashboard financeiro • Dados do Firestore
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

        {/* Error */}
        {error && (
          <Card className="shadow-card border-destructive/30">
            <CardContent className="py-16 text-center">
              <div className="h-10 w-10 rounded-full bg-destructive/10 flex items-center justify-center mx-auto mb-3">
                <AlertCircle className="h-5 w-5 text-destructive" />
              </div>
              <p className="text-sm font-medium text-foreground mb-1">Erro ao carregar dados</p>
              <p className="text-xs text-muted-foreground mb-4">{error}</p>
              <Button size="sm" onClick={refetch}>
                <RefreshCw className="h-3.5 w-3.5 mr-1.5" />
                Tentar novamente
              </Button>
            </CardContent>
          </Card>
        )}

        {/* Loading */}
        {loading && (
          <div className="space-y-6">
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
              {Array.from({ length: 8 }).map((_, i) => (
                <Card key={i} className="shadow-card">
                  <CardContent className="p-5">
                    <div className="h-4 w-24 rounded bg-muted animate-skeleton mb-3" />
                    <div className="h-7 w-20 rounded bg-muted animate-skeleton" />
                  </CardContent>
                </Card>
              ))}
            </div>
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
              <div className="lg:col-span-2">
                <Card className="shadow-card">
                  <CardContent className="p-5">
                    <div className="h-4 w-32 rounded bg-muted animate-skeleton mb-4" />
                    <div className="h-[300px] rounded bg-muted animate-skeleton" />
                  </CardContent>
                </Card>
              </div>
              <Card className="shadow-card">
                <CardContent className="p-5">
                  <div className="h-4 w-24 rounded bg-muted animate-skeleton mb-4" />
                  <div className="h-[300px] rounded bg-muted animate-skeleton" />
                </CardContent>
              </Card>
            </div>
          </div>
        )}

        {/* KPI Cards — Row 1: Revenue Metrics */}
        {!loading && !error && (
          <>
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
              {[
                {
                  label: "Receita Total",
                  value: formatCompactCurrency(totalRevenue),
                  icon: DollarSign,
                  iconBg: "bg-emerald-100 dark:bg-emerald-950/50",
                  iconCl: "text-emerald-600 dark:text-emerald-400",
                  trend: "+12%",
                  trendUp: true,
                },
                {
                  label: "Ticket Médio",
                  value: formatCurrency(averageTicket),
                  icon: TicketPercent,
                  iconBg: "bg-blue-100 dark:bg-blue-950/50",
                  iconCl: "text-blue-600 dark:text-blue-400",
                  trend: `R$${Math.round(averageTicket).toLocaleString()}/pedido`,
                  trendUp: null,
                },
                {
                  label: "Transações",
                  value: formatNumber(totalTransactions),
                  icon: ShoppingCart,
                  iconBg: "bg-violet-100 dark:bg-violet-950/50",
                  iconCl: "text-violet-600 dark:text-violet-400",
                  trend: `${successRate}% sucesso`,
                  trendUp: successRate > 70,
                },
                {
                  label: "Conversão",
                  value: `${successRate}%`,
                  icon: TrendingUp,
                  iconBg: "bg-sky-100 dark:bg-sky-950/50",
                  iconCl: "text-sky-600 dark:text-sky-400",
                  trend: statusBreakdown.paid > 0 ? `${statusBreakdown.paid} pedidos pagos` : "—",
                  trendUp: true,
                },
              ].map(({ label, value, icon: Icon, iconBg, iconCl, trend, trendUp }) => (
                <KpiCard
                  key={label}
                  label={label}
                  value={value}
                  icon={<Icon className={cn("h-4 w-4", iconCl)} />}
                  iconBg={iconBg}
                  trend={trend}
                  trendUp={trendUp}
                />
              ))}
            </div>

            {/* KPI Cards — Row 2: Platform/Providers */}
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
              {[
                {
                  label: "Comissão Plataforma",
                  value: formatCompactCurrency(platformCommission),
                  icon: Building2,
                  iconBg: "bg-indigo-100 dark:bg-indigo-950/50",
                  iconCl: "text-indigo-600 dark:text-indigo-400",
                  trend: "Receita AquiResolve",
                  trendUp: true,
                },
                {
                  label: "Cashback Distribuído",
                  value: formatCompactCurrency(cashbackDistributed),
                  icon: Wallet,
                  iconBg: "bg-amber-100 dark:bg-amber-950/50",
                  iconCl: "text-amber-600 dark:text-amber-400",
                  trend: "Programa AquiCash",
                  trendUp: null,
                },
                {
                  label: "A Pagar Prestadores",
                  value: formatCompactCurrency(pendingProviderPayouts),
                  icon: Users,
                  iconBg: "bg-rose-100 dark:bg-rose-950/50",
                  iconCl: "text-rose-600 dark:text-rose-400",
                  trend: `${totalProviders} prestadores`,
                  trendUp: null,
                },
                {
                  label: "Margem Líquida",
                  value: `${revenueMargin.toFixed(1)}%`,
                  icon: Receipt,
                  iconBg: "bg-teal-100 dark:bg-teal-950/50",
                  iconCl: "text-teal-600 dark:text-teal-400",
                  trend: `${totalCustomers} clientes`,
                  trendUp: revenueMargin > 30,
                },
              ].map(({ label, value, icon, iconBg, iconCl, trend, trendUp }) => (
                <KpiCard
                  key={label}
                  label={label}
                  value={value}
                  icon={icon}
                  iconBg={iconBg}
                  trend={trend}
                  trendUp={trendUp}
                />
              ))}
            </div>

            {/* Charts Row */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
              {/* Revenue Chart — 2 cols */}
              <div className="lg:col-span-2">
                <RevenueAreaChart
                  data={dailyRevenue}
                  title="Evolução da Receita"
                  height={300}
                  loading={loading}
                />
              </div>

              {/* Payment Methods Donut */}
              <DonutChart
                data={paymentMethodChartData}
                title="Métodos de Pagamento"
                height={300}
                loading={loading}
              />
            </div>

            {/* Second Charts Row */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              {/* Status Breakdown */}
              <StatusBarChart
                data={statusChartData}
                title="Status das Transações"
                height={280}
                loading={loading}
              />

              {/* Top Services */}
              <Card className="shadow-card">
                <CardHeader className="pb-2">
                  <CardTitle className="text-sm font-semibold flex items-center gap-2">
                    <BarChart3 className="h-4 w-4 text-muted-foreground" />
                    Top Serviços por Receita
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  {topServices.length > 0 ? (
                    <div className="space-y-2 max-h-[280px] overflow-y-auto">
                      {topServices.map((svc, i) => (
                        <div
                          key={svc.name}
                          className="flex items-center justify-between p-2.5 rounded-lg bg-muted/30 hover:bg-muted/50 transition-colors"
                        >
                          <div className="flex items-center gap-3 min-w-0">
                            <span className="text-xs font-bold text-muted-foreground w-6 shrink-0">
                              #{i + 1}
                            </span>
                            <div className="min-w-0">
                              <p className="text-sm font-medium text-foreground truncate">
                                {svc.name}
                              </p>
                              <p className="text-xs text-muted-foreground">
                                {svc.count} pedidos
                              </p>
                            </div>
                          </div>
                          <span className="text-sm font-semibold text-emerald-600 dark:text-emerald-400 tabular-nums shrink-0 ml-2">
                            {formatCompactCurrency(svc.revenue)}
                          </span>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="flex items-center justify-center h-48 text-sm text-muted-foreground">
                      Nenhum serviço com receita no período
                    </div>
                  )}
                </CardContent>
              </Card>
            </div>

            {/* Recent Transactions Table */}
            <Card className="shadow-card">
              <CardHeader className="pb-3 flex flex-row items-center justify-between">
                <CardTitle className="text-sm font-semibold flex items-center gap-2">
                  <Receipt className="h-4 w-4 text-muted-foreground" />
                  Transações Recentes
                </CardTitle>
                <Button variant="ghost" size="sm" className="text-xs" asChild>
                  <a href="/dashboard/financeiro/faturamento">
                    Ver todas
                    <ArrowUpRight className="h-3 w-3 ml-1" />
                  </a>
                </Button>
              </CardHeader>
              <CardContent className="p-0">
                <div className="overflow-x-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>ID</TableHead>
                        <TableHead>Serviço</TableHead>
                        <TableHead>Método</TableHead>
                        <TableHead className="text-right">Valor</TableHead>
                        <TableHead>Status</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {recentTransactions.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={5} className="text-center py-8 text-muted-foreground">
                            Nenhuma transação no período
                          </TableCell>
                        </TableRow>
                      ) : (
                        recentTransactions.map((tx) => (
                          <TableRow key={tx.id}>
                            <TableCell className="font-mono text-xs text-muted-foreground">
                              #{tx.id}
                            </TableCell>
                            <TableCell className="text-sm truncate max-w-[200px]">
                              {tx.service || "—"}
                            </TableCell>
                            <TableCell>
                              <Badge variant="outline" className="text-xs capitalize">
                                {tx.method?.replace("_", " ") || "—"}
                              </Badge>
                            </TableCell>
                            <TableCell className="text-right font-semibold tabular-nums">
                              {formatCurrency(tx.amount)}
                            </TableCell>
                            <TableCell>{getStatusBadge(tx.status)}</TableCell>
                          </TableRow>
                        ))
                      )}
                    </TableBody>
                  </Table>
                </div>
              </CardContent>
            </Card>
          </>
        )}
      </div>
    </RouteGuard>
  )
}

// Inline KPI Card component
function KpiCard({
  label,
  value,
  icon,
  iconBg,
  trend,
  trendUp,
}: {
  label: string
  value: string
  icon: React.ReactNode
  iconBg: string
  trend?: string
  trendUp: boolean | null
}) {
  return (
    <Card className="shadow-card hover:shadow-card-hover transition-shadow">
      <CardContent className="p-5">
        <div className="flex items-start justify-between mb-3">
          <p className="text-xs font-medium text-muted-foreground leading-snug">
            {label}
          </p>
          <div className={cn("h-9 w-9 rounded-lg flex items-center justify-center shrink-0", iconBg)}>
            {icon}
          </div>
        </div>
        <p className="text-xl font-bold text-foreground tabular-nums truncate">
          {value}
        </p>
        {trend && (
          <div className="flex items-center gap-1 mt-1">
            {trendUp === true && (
              <ArrowUpRight className="h-3 w-3 text-emerald-500" />
            )}
            {trendUp === false && (
              <ArrowDownRight className="h-3 w-3 text-red-500" />
            )}
            <p
              className={cn(
                "text-xs",
                trendUp === true ? "text-emerald-600 dark:text-emerald-400" :
                trendUp === false ? "text-red-600 dark:text-red-400" :
                "text-muted-foreground"
              )}
            >
              {trend}
            </p>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
