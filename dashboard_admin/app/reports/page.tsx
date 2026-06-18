"use client"

import { useEffect, useMemo, useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Calendar } from "@/components/ui/calendar"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  BarChart,
  Bar,
  LineChart,
  Line,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  ResponsiveContainer,
  Tooltip,
  Legend,
  AreaChart,
  Area,
} from "recharts"
import {
  CalendarIcon,
  Download,
  TrendingUp,
  Users,
  DollarSign,
  RefreshCw,
  AlertCircle,
  FileText,
  CheckCircle2,
  Target,
  Activity,
  CreditCard,
} from "lucide-react"
import { format } from "date-fns"
import { ptBR } from "date-fns/locale"
import { DateRange } from "react-day-picker"
import { usePagarmeCharges, usePagarmeOrders, usePagarmeAnalytics } from "@/hooks/use-pagarme"
import { PagarmeService } from "@/lib/services/pagarme-service"
import { cn } from "@/lib/utils"
import { AppShell } from "@/components/layout/app-shell"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { FileSpreadsheet, FileDown } from "lucide-react"

const TAB_KEYS = new Set(["overview", "analytics", "performance"])

const LEGACY_TAB_MAP: Record<string, "overview" | "analytics" | "performance"> = {
  financial: "overview",
  payments: "analytics",
  evolution: "analytics",
  insights: "analytics",
  performance: "performance",
}

const paymentMethodColors: Record<string, string> = {
  pix: "#3b82f6",
  credit_card: "#10b981",
  debit_card: "#f59e0b",
  boleto: "#ef4444",
  voucher: "#8b5cf6",
}

const formatCurrency = (value: number) =>
  `R$ ${value.toLocaleString("pt-BR", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`

const getMethodLabel = (method: string) => {
  if (method === "credit_card") return "Cartão de Crédito"
  if (method === "debit_card") return "Cartão de Débito"
  if (method === "pix") return "PIX"
  if (method === "boleto") return "Boleto"
  if (method === "voucher") return "Voucher"
  return method.toUpperCase()
}

function ChartLoading() {
  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
      {[1, 2].map((item) => (
        <Card key={item} className="shadow-card">
          <CardHeader>
            <div className="h-6 w-44 animate-skeleton rounded bg-muted" />
          </CardHeader>
          <CardContent>
            <div className="h-80 animate-skeleton rounded bg-muted/60" />
          </CardContent>
        </Card>
      ))}
    </div>
  )
}

function EmptyDataState({ onRefresh }: { onRefresh: () => void }) {
  return (
    <Card className="shadow-card">
      <CardContent className="p-12">
        <div className="text-center">
          <FileText className="mx-auto mb-4 h-16 w-16 text-muted-foreground/40" />
          <h3 className="mb-2 text-lg font-semibold text-foreground">Nenhum dado disponível</h3>
          <p className="mb-6 text-muted-foreground">Não encontramos dados para o período selecionado.</p>
          <Button onClick={onRefresh} variant="outline">
            <RefreshCw className="mr-2 h-4 w-4" />
            Atualizar dados
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}

export default function ReportsPage() {
  const [activeTab, setActiveTab] = useState<"overview" | "analytics" | "performance">("overview")
  const [dateRange, setDateRange] = useState<DateRange | undefined>({
    from: new Date(new Date().getFullYear(), new Date().getMonth(), 1),
    to: new Date(),
  })

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const requestedTab = params.get("tab")

    if (!requestedTab) return

    const mappedTab = LEGACY_TAB_MAP[requestedTab] || requestedTab
    if (TAB_KEYS.has(mappedTab)) {
      setActiveTab(mappedTab as "overview" | "analytics" | "performance")
    }
  }, [])

  const fromDate = dateRange?.from || new Date(new Date().getFullYear(), new Date().getMonth(), 1)
  const toDate = dateRange?.to || new Date()

  const {
    charges,
    loading: chargesLoading,
    error: chargesError,
    refetch: refetchCharges,
  } = usePagarmeCharges({ autoRefresh: true })

  const {
    orders,
    loading: ordersLoading,
    error: ordersError,
    refetch: refetchOrders,
  } = usePagarmeOrders({ autoRefresh: true })

  const {
    analytics,
    loading: analyticsLoading,
    error: analyticsError,
    refetch: refetchAnalytics,
  } = usePagarmeAnalytics(format(fromDate, "yyyy-MM-dd"), format(toDate, "yyyy-MM-dd"))

  const isLoading = chargesLoading || ordersLoading || analyticsLoading
  const hasError = chargesError || ordersError || analyticsError

  const paidCharges = useMemo(
    () => (charges || []).filter((charge) => charge.status === "paid"),
    [charges]
  )

  const stats = useMemo(() => {
    const totalPedidos = orders?.length || 0
    const totalCobrancas = charges?.length || 0
    const pedidosPagos = paidCharges.length
    const receitaTotal = paidCharges.reduce(
      (sum, charge) => sum + PagarmeService.fromCents(charge.paid_amount || charge.amount),
      0
    )

    const clientesUnicos = new Set((charges || []).map((charge) => charge.customer.id)).size
    const ticketMedio = pedidosPagos > 0 ? receitaTotal / pedidosPagos : 0
    const taxaAprovacao = totalCobrancas > 0 ? (pedidosPagos / totalCobrancas) * 100 : 0

    return {
      totalPedidos,
      totalCobrancas,
      pedidosPagos,
      receitaTotal,
      clientesUnicos,
      ticketMedio,
      taxaAprovacao,
    }
  }, [orders, charges, paidCharges])

  const dailyData = useMemo(() => {
    if (!charges || charges.length === 0) return []

    const last7Days = Array.from({ length: 7 }, (_, index) => {
      const date = new Date()
      date.setHours(0, 0, 0, 0)
      date.setDate(date.getDate() - (6 - index))
      return date
    })

    return last7Days.map((date) => {
      const dayCharges = charges.filter((charge) => {
        const createdAt = new Date(charge.created_at)
        return createdAt.toDateString() === date.toDateString()
      })

      const paidDayCharges = dayCharges.filter((charge) => charge.status === "paid")

      return {
        label: format(date, "dd/MM"),
        pedidos: dayCharges.length,
        receita: paidDayCharges.reduce(
          (sum, charge) => sum + PagarmeService.fromCents(charge.paid_amount || charge.amount),
          0
        ),
      }
    })
  }, [charges])

  const evolutionData = useMemo(() => {
    if (!charges || charges.length === 0) return []

    const last30Days = Array.from({ length: 30 }, (_, index) => {
      const date = new Date()
      date.setHours(0, 0, 0, 0)
      date.setDate(date.getDate() - (29 - index))
      return date
    })

    return last30Days.map((date) => {
      const dayCharges = charges.filter((charge) => {
        const createdAt = new Date(charge.created_at)
        return createdAt.toDateString() === date.toDateString()
      })

      const paid = dayCharges.filter((charge) => charge.status === "paid")
      const pending = dayCharges.filter((charge) => charge.status === "pending")
      const failed = dayCharges.filter(
        (charge) => charge.status === "failed" || charge.status === "canceled"
      )

      return {
        label: format(date, "dd/MM"),
        receita: paid.reduce(
          (sum, charge) => sum + PagarmeService.fromCents(charge.paid_amount || charge.amount),
          0
        ),
        paid: paid.length,
        pending: pending.length,
        failed: failed.length,
      }
    })
  }, [charges])

  const paymentMethodData = useMemo(() => {
    if (!paidCharges.length) return []

    const grouped = paidCharges.reduce((accumulator, charge) => {
      const method = charge.payment_method
      if (!accumulator[method]) {
        accumulator[method] = { count: 0, value: 0 }
      }

      accumulator[method].count += 1
      accumulator[method].value += PagarmeService.fromCents(charge.paid_amount || charge.amount)
      return accumulator
    }, {} as Record<string, { count: number; value: number }>)

    return Object.entries(grouped)
      .map(([method, data]) => ({
        key: method,
        name: getMethodLabel(method),
        count: data.count,
        value: data.value,
        color: paymentMethodColors[method] || "#6b7280",
      }))
      .sort((a, b) => b.value - a.value)
  }, [paidCharges])

  const hourlyData = useMemo(() => {
    if (!charges || charges.length === 0) return []

    const hours = Array.from({ length: 24 }, (_, hour) => hour)

    return hours.map((hour) => {
      const hourCharges = charges.filter((charge) => new Date(charge.created_at).getHours() === hour)

      const hourRevenue = hourCharges
        .filter((charge) => charge.status === "paid")
        .reduce((sum, charge) => sum + PagarmeService.fromCents(charge.paid_amount || charge.amount), 0)

      return {
        hour: `${hour.toString().padStart(2, "0")}:00`,
        transactions: hourCharges.length,
        revenue: hourRevenue,
      }
    })
  }, [charges])

  const topHour = useMemo(() => {
    if (!hourlyData.length) return null
    return hourlyData.reduce((best, current) =>
      current.revenue > best.revenue ? current : best
    )
  }, [hourlyData])

  const topPaymentMethod = paymentMethodData[0]
  const hasData = (charges && charges.length > 0) || (orders && orders.length > 0)

  const handleDateChange = (range: DateRange | undefined) => {
    if (range?.from && range?.to) {
      setDateRange(range)
    }
  }

  const handleRefresh = () => {
    refetchCharges()
    refetchOrders()
    refetchAnalytics()
  }

  const reportFileName = (ext: string) => `relatorio-aquiresolve-${format(new Date(), "yyyy-MM-dd")}.${ext}`

  const triggerDownload = (blob: Blob, filename: string) => {
    const url = URL.createObjectURL(blob)
    const link = document.createElement("a")
    link.href = url
    link.setAttribute("download", filename)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  }

  const handleExportCsv = () => {
    if (!dailyData.length) return
    const csvHeader = "Data,Pedidos,Receita"
    const csvRows = dailyData.map((row) => `${row.label},${row.pedidos},${row.receita.toFixed(2)}`)
    const csvContent = [csvHeader, ...csvRows].join("\n")
    triggerDownload(new Blob(["﻿" + csvContent], { type: "text/csv;charset=utf-8;" }), reportFileName("csv"))
  }

  const handleExportExcel = () => {
    if (!dailyData.length) return
    const rowsHtml = dailyData
      .map((row) => `<tr><td>${row.label}</td><td>${row.pedidos}</td><td>${row.receita.toFixed(2)}</td></tr>`)
      .join("")
    const summaryHtml = `
      <tr><td colspan="3"></td></tr>
      <tr><td><b>Receita total</b></td><td colspan="2">${stats.receitaTotal.toFixed(2)}</td></tr>
      <tr><td><b>Pedidos pagos</b></td><td colspan="2">${stats.pedidosPagos}</td></tr>
      <tr><td><b>Ticket médio</b></td><td colspan="2">${stats.ticketMedio.toFixed(2)}</td></tr>
      <tr><td><b>Taxa de aprovação</b></td><td colspan="2">${stats.taxaAprovacao.toFixed(1)}%</td></tr>`
    const html = `
      <html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel">
      <head><meta charset="utf-8" /></head>
      <body>
        <table border="1">
          <thead><tr><th>Data</th><th>Pedidos</th><th>Receita (R$)</th></tr></thead>
          <tbody>${rowsHtml}${summaryHtml}</tbody>
        </table>
      </body></html>`
    triggerDownload(new Blob(["﻿" + html], { type: "application/vnd.ms-excel;charset=utf-8;" }), reportFileName("xls"))
  }

  const handleExportPdf = async () => {
    if (!dailyData.length) return
    const { jsPDF } = await import("jspdf")
    const doc = new jsPDF()
    const pageWidth = doc.internal.pageSize.getWidth()

    doc.setFontSize(16)
    doc.setTextColor(27, 94, 32)
    doc.text("AquiResolve — Relatório", 14, 18)
    doc.setFontSize(10)
    doc.setTextColor(120)
    doc.text(`Gerado em ${format(new Date(), "dd/MM/yyyy HH:mm", { locale: ptBR })}`, 14, 25)

    // Resumo
    doc.setTextColor(0)
    doc.setFontSize(11)
    let y = 36
    const resumo: [string, string][] = [
      ["Receita total", formatCurrency(stats.receitaTotal)],
      ["Pedidos pagos", String(stats.pedidosPagos)],
      ["Ticket médio", formatCurrency(stats.ticketMedio)],
      ["Taxa de aprovação", `${stats.taxaAprovacao.toFixed(1)}%`],
      ["Clientes únicos", String(stats.clientesUnicos)],
    ]
    resumo.forEach(([label, value]) => {
      doc.setTextColor(90)
      doc.text(`${label}:`, 14, y)
      doc.setTextColor(0)
      doc.text(value, 70, y)
      y += 7
    })

    // Tabela diária
    y += 6
    doc.setFontSize(12)
    doc.setTextColor(13, 71, 161)
    doc.text("Evolução diária", 14, y)
    y += 8
    doc.setFontSize(10)
    doc.setTextColor(0)
    doc.setDrawColor(220)
    doc.text("Data", 14, y)
    doc.text("Pedidos", 90, y)
    doc.text("Receita", 140, y)
    doc.line(14, y + 2, pageWidth - 14, y + 2)
    y += 9
    dailyData.forEach((row) => {
      if (y > 280) { doc.addPage(); y = 20 }
      doc.text(String(row.label), 14, y)
      doc.text(String(row.pedidos), 90, y)
      doc.text(formatCurrency(row.receita), 140, y)
      y += 7
    })

    doc.save(reportFileName("pdf"))
  }

  return (
    <AppShell>
      <div className="space-y-6 animate-fade-in">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex items-center gap-3">
            <div className="h-9 w-9 rounded-lg bg-primary/10 flex items-center justify-center shrink-0">
              <Activity className="h-5 w-5 text-primary" />
            </div>
            <div>
              <h1 className="text-2xl font-bold text-foreground">Central de Relatórios e Análises</h1>
              <p className="text-sm text-muted-foreground">
                Tudo em um único painel: relatórios, análise de evolução e performance.
              </p>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <Popover>
              <PopoverTrigger asChild>
                <Button variant="outline" className="w-full justify-start text-left font-normal lg:w-[280px]">
                  <CalendarIcon className="mr-2 h-4 w-4" />
                  {dateRange?.from ? (
                    dateRange.to ? (
                      <>
                        {format(dateRange.from, "dd/MM/yyyy", { locale: ptBR })} -{" "}
                        {format(dateRange.to, "dd/MM/yyyy", { locale: ptBR })}
                      </>
                    ) : (
                      format(dateRange.from, "dd/MM/yyyy", { locale: ptBR })
                    )
                  ) : (
                    <span>Selecionar período</span>
                  )}
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-auto p-0" align="start">
                <Calendar
                  initialFocus
                  mode="range"
                  defaultMonth={dateRange?.from}
                  selected={dateRange}
                  onSelect={handleDateChange}
                  numberOfMonths={2}
                />
              </PopoverContent>
            </Popover>

            <Button variant="outline" onClick={handleRefresh} disabled={isLoading} className="flex items-center gap-2">
              <RefreshCw className={cn("h-4 w-4", isLoading && "animate-spin")} />
              {isLoading ? "Atualizando..." : "Atualizar"}
            </Button>

            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button disabled={!dailyData.length || isLoading}>
                  <Download className="mr-2 h-4 w-4" />
                  Exportar
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem onClick={handleExportPdf}>
                  <FileText className="mr-2 h-4 w-4 text-red-600" />
                  PDF
                </DropdownMenuItem>
                <DropdownMenuItem onClick={handleExportExcel}>
                  <FileSpreadsheet className="mr-2 h-4 w-4 text-emerald-600" />
                  Excel (.xls)
                </DropdownMenuItem>
                <DropdownMenuItem onClick={handleExportCsv}>
                  <FileDown className="mr-2 h-4 w-4 text-blue-600" />
                  CSV
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>

        {hasError && (
          <div className="rounded-lg border border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-950/30 px-4 py-3">
            <div className="flex items-center gap-3">
              <AlertCircle className="h-5 w-5 text-amber-600 dark:text-amber-400 shrink-0" />
              <div className="flex-1 min-w-0">
                <p className="font-medium text-amber-900 dark:text-amber-200 text-sm">Erro ao carregar dados</p>
                <p className="text-xs text-amber-700 dark:text-amber-300 mt-0.5">
                  {chargesError || ordersError || analyticsError || "Não foi possível atualizar os relatórios."}
                </p>
              </div>
              <Button variant="outline" size="sm" onClick={handleRefresh} className="shrink-0">
                Tentar novamente
              </Button>
            </div>
          </div>
        )}

        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          {[
            { label: "Pedidos",          value: stats.totalPedidos,              icon: TrendingUp,  iconBg: "bg-blue-50 dark:bg-blue-950/40",      iconCl: "text-blue-600",    sub: <><FileText className="mr-1 h-3 w-3" />Total no período</>         },
            { label: "Receita",          value: formatCurrency(stats.receitaTotal), icon: DollarSign, iconBg: "bg-emerald-50 dark:bg-emerald-950/40", iconCl: "text-emerald-600", sub: <><DollarSign className="mr-1 h-3 w-3" />Pagamentos aprovados</>   },
            { label: "Taxa de Aprovação",value: `${stats.taxaAprovacao.toFixed(1)}%`, icon: Target, iconBg: "bg-primary/10",                        iconCl: "text-primary",     sub: <><CheckCircle2 className="mr-1 h-3 w-3" />{stats.pedidosPagos} de {stats.totalCobrancas}</> },
            { label: "Clientes Únicos",  value: stats.clientesUnicos,            icon: Users,       iconBg: "bg-violet-50 dark:bg-violet-950/40",  iconCl: "text-violet-600",  sub: <>Ticket {formatCurrency(stats.ticketMedio)}</>                    },
          ].map(({ label, value, icon: Icon, iconBg, iconCl, sub }) => (
            <Card key={label} className="shadow-card hover:shadow-card-hover transition-shadow">
              <CardContent className="p-5">
                <div className="flex items-start justify-between mb-3">
                  <p className="text-xs font-medium text-muted-foreground leading-snug">{label}</p>
                  <div className={cn("h-9 w-9 rounded-lg flex items-center justify-center shrink-0", iconBg)}>
                    <Icon className={cn("h-4 w-4", iconCl)} />
                  </div>
                </div>
                <p className="text-2xl font-bold text-foreground tabular-nums">{value}</p>
                <p className="mt-1 flex items-center text-xs text-muted-foreground">{sub}</p>
              </CardContent>
            </Card>
          ))}
        </div>

        <Tabs value={activeTab} onValueChange={(value) => setActiveTab(value as typeof activeTab)} className="space-y-6">
          <TabsList className="grid w-full grid-cols-1 gap-2 bg-transparent p-0 md:grid-cols-3">
            <TabsTrigger value="overview" className="border border-border bg-card data-[state=active]:border-primary data-[state=active]:text-primary data-[state=active]:shadow-sm">
              Relatórios e Análises
            </TabsTrigger>
            <TabsTrigger value="analytics" className="border border-border bg-card data-[state=active]:border-primary data-[state=active]:text-primary data-[state=active]:shadow-sm">
              Painel de Análise
            </TabsTrigger>
            <TabsTrigger value="performance" className="border border-border bg-card data-[state=active]:border-primary data-[state=active]:text-primary data-[state=active]:shadow-sm">
              Performance
            </TabsTrigger>
          </TabsList>

          <TabsContent value="overview" className="space-y-6">
            {isLoading ? (
              <ChartLoading />
            ) : !hasData ? (
              <EmptyDataState onRefresh={handleRefresh} />
            ) : (
              <>
                <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
                  <Card className="shadow-card">
                    <CardHeader>
                      <CardTitle>Receita nos últimos 7 dias</CardTitle>
                    </CardHeader>
                    <CardContent>
                      <div className="h-80">
                        <ResponsiveContainer width="100%" height="100%">
                          <BarChart data={dailyData}>
                            <XAxis dataKey="label" tick={{ fontSize: 12 }} />
                            <YAxis tick={{ fontSize: 12 }} />
                            <Tooltip formatter={(value: number | string) => [formatCurrency(Number(value) || 0), "Receita"]} />
                            <Bar dataKey="receita" fill="#10b981" radius={[4, 4, 0, 0]} />
                          </BarChart>
                        </ResponsiveContainer>
                      </div>
                    </CardContent>
                  </Card>

                  <Card className="shadow-card">
                    <CardHeader>
                      <CardTitle>Pedidos por dia</CardTitle>
                    </CardHeader>
                    <CardContent>
                      <div className="h-80">
                        <ResponsiveContainer width="100%" height="100%">
                          <LineChart data={dailyData}>
                            <XAxis dataKey="label" tick={{ fontSize: 12 }} />
                            <YAxis tick={{ fontSize: 12 }} />
                            <Tooltip />
                            <Line
                              type="monotone"
                              dataKey="pedidos"
                              stroke="#3b82f6"
                              strokeWidth={3}
                              dot={{ fill: "#3b82f6", r: 4 }}
                              activeDot={{ r: 6 }}
                            />
                          </LineChart>
                        </ResponsiveContainer>
                      </div>
                    </CardContent>
                  </Card>
                </div>

                <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
                  <Card className="shadow-card">
                    <CardHeader>
                      <CardTitle>Métodos de pagamento</CardTitle>
                    </CardHeader>
                    <CardContent>
                      <div className="h-80">
                        <ResponsiveContainer width="100%" height="100%">
                          <PieChart>
                            <Pie
                              data={paymentMethodData}
                              cx="50%"
                              cy="50%"
                              outerRadius={100}
                              dataKey="value"
                              nameKey="name"
                            >
                              {paymentMethodData.map((entry) => (
                                <Cell key={entry.key} fill={entry.color} />
                              ))}
                            </Pie>
                            <Tooltip formatter={(value: number | string) => [formatCurrency(Number(value) || 0), "Receita"]} />
                            <Legend />
                          </PieChart>
                        </ResponsiveContainer>
                      </div>
                    </CardContent>
                  </Card>

                  <Card className="shadow-card">
                    <CardHeader>
                      <CardTitle>Distribuição por método</CardTitle>
                    </CardHeader>
                    <CardContent>
                      <div className="space-y-4">
                        {paymentMethodData.map((method) => {
                          const maxValue = Math.max(...paymentMethodData.map((item) => item.value), 1)

                          return (
                            <div key={method.key} className="rounded-lg bg-muted/50 p-3">
                              <div className="flex items-center justify-between">
                                <div className="flex items-center gap-3">
                                  <span className="h-3 w-3 rounded-full shrink-0" style={{ backgroundColor: method.color }} />
                                  <span className="font-medium text-foreground text-sm">{method.name}</span>
                                </div>
                                <span className="text-sm font-semibold text-foreground tabular-nums">{formatCurrency(method.value)}</span>
                              </div>
                              <div className="mt-2 h-2 w-full rounded-full bg-muted">
                                <div
                                  className="h-2 rounded-full"
                                  style={{
                                    width: `${(method.value / maxValue) * 100}%`,
                                    backgroundColor: method.color,
                                  }}
                                />
                              </div>
                              <p className="mt-1 text-xs text-muted-foreground">{method.count} transações</p>
                            </div>
                          )
                        })}
                      </div>
                    </CardContent>
                  </Card>
                </div>
              </>
            )}
          </TabsContent>

          <TabsContent value="analytics" className="space-y-6">
            {isLoading ? (
              <ChartLoading />
            ) : !hasData ? (
              <EmptyDataState onRefresh={handleRefresh} />
            ) : (
              <>
                <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
                  <Card className="shadow-card">
                    <CardHeader>
                      <CardTitle>Evolução da receita (30 dias)</CardTitle>
                    </CardHeader>
                    <CardContent>
                      <div className="h-80">
                        <ResponsiveContainer width="100%" height="100%">
                          <AreaChart data={evolutionData}>
                            <XAxis dataKey="label" tick={{ fontSize: 12 }} />
                            <YAxis tick={{ fontSize: 12 }} />
                            <Tooltip formatter={(value: number | string) => [formatCurrency(Number(value) || 0), "Receita"]} />
                            <Area type="monotone" dataKey="receita" stroke="#3b82f6" fill="#3b82f6" fillOpacity={0.25} />
                          </AreaChart>
                        </ResponsiveContainer>
                      </div>
                    </CardContent>
                  </Card>

                  <Card className="shadow-card">
                    <CardHeader>
                      <CardTitle>Status das cobranças</CardTitle>
                    </CardHeader>
                    <CardContent>
                      <div className="h-80">
                        <ResponsiveContainer width="100%" height="100%">
                          <LineChart data={evolutionData}>
                            <XAxis dataKey="label" tick={{ fontSize: 12 }} />
                            <YAxis tick={{ fontSize: 12 }} />
                            <Tooltip />
                            <Line type="monotone" dataKey="paid" stroke="#10b981" strokeWidth={2} name="Pagas" />
                            <Line type="monotone" dataKey="pending" stroke="#f59e0b" strokeWidth={2} name="Pendentes" />
                            <Line type="monotone" dataKey="failed" stroke="#ef4444" strokeWidth={2} name="Falhas" />
                          </LineChart>
                        </ResponsiveContainer>
                      </div>
                    </CardContent>
                  </Card>
                </div>

                <Card className="shadow-card">
                  <CardHeader>
                    <CardTitle>Resumo analítico do período</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
                      {[
                        { label: "Pedidos no período",      value: analytics?.total_orders ?? stats.totalPedidos },
                        { label: "Faturamento no período",  value: formatCurrency(analytics ? PagarmeService.fromCents(analytics.total_amount) : stats.receitaTotal) },
                        { label: "Clientes no período",     value: analytics?.total_customers ?? stats.clientesUnicos },
                        { label: "Assinaturas no período",  value: analytics?.total_subscriptions ?? 0 },
                      ].map(({ label, value }) => (
                        <div key={label} className="rounded-lg bg-muted/50 p-4">
                          <p className="text-xs font-medium text-muted-foreground">{label}</p>
                          <p className="mt-1 text-2xl font-bold text-foreground tabular-nums">{value}</p>
                        </div>
                      ))}
                    </div>
                  </CardContent>
                </Card>
              </>
            )}
          </TabsContent>

          <TabsContent value="performance" className="space-y-6">
            {isLoading ? (
              <ChartLoading />
            ) : !hasData ? (
              <EmptyDataState onRefresh={handleRefresh} />
            ) : (
              <>
                <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
                  <Card className="shadow-card">
                    <CardHeader>
                      <CardTitle>Transações por hora</CardTitle>
                    </CardHeader>
                    <CardContent>
                      <div className="h-80">
                        <ResponsiveContainer width="100%" height="100%">
                          <BarChart data={hourlyData}>
                            <XAxis dataKey="hour" tick={{ fontSize: 10 }} interval={2} />
                            <YAxis tick={{ fontSize: 12 }} />
                            <Tooltip />
                            <Bar dataKey="transactions" fill="#3b82f6" radius={[2, 2, 0, 0]} />
                          </BarChart>
                        </ResponsiveContainer>
                      </div>
                    </CardContent>
                  </Card>

                  <Card className="shadow-card">
                    <CardHeader>
                      <CardTitle>Receita por hora</CardTitle>
                    </CardHeader>
                    <CardContent>
                      <div className="h-80">
                        <ResponsiveContainer width="100%" height="100%">
                          <AreaChart data={hourlyData}>
                            <XAxis dataKey="hour" tick={{ fontSize: 10 }} interval={2} />
                            <YAxis tick={{ fontSize: 12 }} />
                            <Tooltip formatter={(value: number | string) => [formatCurrency(Number(value) || 0), "Receita"]} />
                            <Area type="monotone" dataKey="revenue" stroke="#10b981" fill="#10b981" fillOpacity={0.3} />
                          </AreaChart>
                        </ResponsiveContainer>
                      </div>
                    </CardContent>
                  </Card>
                </div>

                <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
                  {[
                    { icon: Activity,  label: "Melhor horário",     value: topHour?.hour || "N/A",                  sub: "Maior receita por hora",        iconCl: "text-blue-600",    iconBg: "bg-blue-50 dark:bg-blue-950/40"      },
                    { icon: CreditCard,label: "Método líder",       value: topPaymentMethod?.name || "N/A",          sub: "Maior participação na receita", iconCl: "text-emerald-600", iconBg: "bg-emerald-50 dark:bg-emerald-950/40" },
                    { icon: Target,    label: "Taxa de aprovação",  value: `${stats.taxaAprovacao.toFixed(1)}%`,     sub: "Cobranças aprovadas",           iconCl: "text-primary",     iconBg: "bg-primary/10"                        },
                    { icon: TrendingUp,label: "Ticket médio",       value: formatCurrency(stats.ticketMedio),        sub: "Valor por cobrança paga",       iconCl: "text-violet-600",  iconBg: "bg-violet-50 dark:bg-violet-950/40"  },
                  ].map(({ icon: Icon, label, value, sub, iconCl, iconBg }) => (
                    <Card key={label} className="shadow-card hover:shadow-card-hover transition-shadow">
                      <CardContent className="p-5">
                        <div className="flex items-start justify-between mb-3">
                          <p className="text-xs font-medium text-muted-foreground leading-snug">{label}</p>
                          <div className={cn("h-9 w-9 rounded-lg flex items-center justify-center shrink-0", iconBg)}>
                            <Icon className={cn("h-4 w-4", iconCl)} />
                          </div>
                        </div>
                        <p className="text-2xl font-bold text-foreground tabular-nums">{value}</p>
                        <p className="mt-1 text-xs text-muted-foreground">{sub}</p>
                      </CardContent>
                    </Card>
                  ))}
                </div>
              </>
            )}
          </TabsContent>
        </Tabs>
      </div>
    </AppShell>
  )
}
