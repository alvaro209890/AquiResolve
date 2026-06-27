"use client"

import { useState } from "react"
import {
  BarChart3, Download, FileText, TrendingUp, TrendingDown,
  DollarSign, Receipt, PieChart, ArrowUpRight, Loader2,
  Calendar, Filter, RefreshCw
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
import { useFinancial } from "@/hooks/use-financial"
import { RevenueAreaChart } from "@/components/charts/revenue-area-chart"
import { DonutChart } from "@/components/charts/donut-chart"

const PERIOD_OPTIONS = [
  { value: "semana", label: "Esta semana" },
  { value: "mes", label: "Este mês" },
  { value: "ano", label: "Este ano" },
]

export default function RelatoriosPage() {
  const [periodo, setPeriodo] = useState("mes")
  const [viewMode, setViewMode] = useState<"resumo" | "detalhado">("resumo")
  const { transactions, accounts, stats, loading, error, refetch } = useFinancial({
    autoRefresh: false,
  })

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

  // Build categories for donut charts
  const revenueByCategory = (() => {
    const cats = new Map<string, number>()
    transactions
      .filter((t) => t.tipo === "receita" && t.status === "confirmada")
      .forEach((t) => {
        cats.set(t.categoria, (cats.get(t.categoria) || 0) + t.valor)
      })
    return Array.from(cats.entries())
      .sort(([, a], [, b]) => b - a)
      .slice(0, 6)
      .map(([name, value], i) => ({
        name: name || "Outros",
        value,
        color: [
          "#10b981", "#3b82f6", "#8b5cf6", "#f59e0b", "#ef4444", "#06b6d4",
        ][i] || "#6b7280",
      }))
  })()

  const expenseByCategory = (() => {
    const cats = new Map<string, number>()
    transactions
      .filter((t) => t.tipo === "despesa" && t.status === "confirmada")
      .forEach((t) => {
        cats.set(t.categoria, (cats.get(t.categoria) || 0) + t.valor)
      })
    return Array.from(cats.entries())
      .sort(([, a], [, b]) => b - a)
      .slice(0, 6)
      .map(([name, value], i) => ({
        name: name || "Outros",
        value,
        color: [
          "#f59e0b", "#ef4444", "#8b5cf6", "#3b82f6", "#10b981", "#6b7280",
        ][i] || "#6b7280",
      }))
  })()

  const revenueChartData = (() => {
    const daily = new Map<string, number>()
    transactions
      .filter((t) => t.tipo === "receita" && t.status === "confirmada")
      .forEach((t) => {
        daily.set(t.data, (daily.get(t.data) || 0) + t.valor)
      })
    return Array.from(daily.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([date, receita]) => ({ date, receita, transacoes: 0 }))
  })()

  const handleExportCSV = () => {
    if (transactions.length === 0) return

    const headers = ["Data", "Tipo", "Categoria", "Descrição", "Valor", "Status", "Conta"]
    const rows = transactions.map((t) => [
      t.data,
      t.tipo,
      t.categoria,
      t.descricao,
      t.valor.toFixed(2),
      t.status,
      t.conta?.nome || "",
    ])
    const csv = [headers.join(","), ...rows.map((r) => r.map((c) => `"${c}"`).join(","))].join("\n")
    const blob = new Blob([csv], { type: "text/csv" })
    const url = URL.createObjectURL(blob)
    const a = document.createElement("a")
    a.href = url
    a.download = `relatorio-financeiro-${new Date().toISOString().split("T")[0]}.csv`
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <RouteGuard requiredPermission="financeiro">
      <div className="space-y-6 animate-fade-in">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-xl bg-gradient-to-br from-violet-400 to-violet-600 flex items-center justify-center shadow-lg shadow-violet-500/20">
              <FileText className="h-5 w-5 text-white" />
            </div>
            <div>
              <h1 className="text-2xl font-bold tracking-tight text-foreground">
                Relatórios Financeiros
              </h1>
              <p className="text-sm text-muted-foreground">
                Relatórios com dados reais do Firestore
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <Select value={periodo} onValueChange={setPeriodo}>
              <SelectTrigger className="w-[150px]">
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
            <Button
              variant="outline"
              size="sm"
              onClick={handleExportCSV}
              disabled={transactions.length === 0}
            >
              <Download className="h-4 w-4 mr-1.5" />
              CSV
            </Button>
          </div>
        </div>

        {/* Loading */}
        {loading && (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {Array.from({ length: 6 }).map((_, i) => (
              <Card key={i} className="shadow-card">
                <CardContent className="p-5">
                  <div className="h-4 w-24 rounded bg-muted animate-pulse mb-3" />
                  <div className="h-7 w-16 rounded bg-muted animate-pulse" />
                </CardContent>
              </Card>
            ))}
          </div>
        )}

        {/* Error */}
        {error && (
          <Card className="shadow-card border-destructive/30">
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
            {/* Summary KPIs */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
              {[
                {
                  label: "Receitas",
                  value: formatCompactCurrency(stats?.totalReceitas || 0),
                  icon: <TrendingUp className="h-4 w-4 text-emerald-600" />,
                  bg: "bg-emerald-50 dark:bg-emerald-950/30",
                },
                {
                  label: "Despesas",
                  value: formatCompactCurrency(stats?.totalDespesas || 0),
                  icon: <TrendingDown className="h-4 w-4 text-red-500" />,
                  bg: "bg-red-50 dark:bg-red-950/30",
                },
                {
                  label: "Saldo",
                  value: formatCompactCurrency(stats?.saldo || 0),
                  icon: <DollarSign className="h-4 w-4 text-blue-600" />,
                  bg: "bg-blue-50 dark:bg-blue-950/30",
                },
                {
                  label: "Contas Ativas",
                  value: String(stats?.totalContas || 0),
                  icon: <Calendar className="h-4 w-4 text-violet-600" />,
                  bg: "bg-violet-50 dark:bg-violet-950/30",
                },
              ].map(({ label, value, icon, bg }) => (
                <Card key={label} className="shadow-card">
                  <CardContent className="p-4">
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="text-xs text-muted-foreground">{label}</p>
                        <p className="text-lg font-bold text-foreground tabular-nums">{value}</p>
                      </div>
                      <div className={cn("h-9 w-9 rounded-lg flex items-center justify-center", bg)}>
                        {icon}
                      </div>
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>

            {/* Revenue Chart + Categories */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
              <div className="lg:col-span-2">
                <RevenueAreaChart
                  data={revenueChartData}
                  title="Receitas por Período"
                  height={300}
                  loading={loading}
                />
              </div>
              <DonutChart
                data={revenueByCategory}
                title="Receitas por Categoria"
                height={300}
                loading={loading}
              />
            </div>

            {/* Expense categories + transactions */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              {/* Expense Categories */}
              <Card className="shadow-card">
                <CardHeader className="pb-2">
                  <CardTitle className="text-sm font-semibold flex items-center gap-2">
                    <PieChart className="h-4 w-4 text-muted-foreground" />
                    Despesas por Categoria
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  {expenseByCategory.length > 0 ? (
                    <div className="space-y-2">
                      {expenseByCategory.map((cat) => (
                        <div
                          key={cat.name}
                          className="flex items-center gap-3 p-2.5 rounded-lg hover:bg-muted/50 transition-colors"
                        >
                          <span
                            className="w-3 h-3 rounded-full shrink-0"
                            style={{ backgroundColor: cat.color }}
                          />
                          <span className="text-sm flex-1 text-foreground truncate">
                            {cat.name}
                          </span>
                          <span className="text-sm font-semibold text-red-600 dark:text-red-400 tabular-nums">
                            {formatCurrency(cat.value)}
                          </span>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="flex items-center justify-center h-32 text-sm text-muted-foreground">
                      Nenhuma despesa registrada
                    </div>
                  )}
                </CardContent>
              </Card>

              {/* Transactions Table */}
              <Card className="shadow-card">
                <CardHeader className="pb-2 flex flex-row items-center justify-between">
                  <CardTitle className="text-sm font-semibold flex items-center gap-2">
                    <Receipt className="h-4 w-4 text-muted-foreground" />
                    Transações
                  </CardTitle>
                  <Badge variant="secondary" className="text-xs">
                    {transactions.length} registros
                  </Badge>
                </CardHeader>
                <CardContent className="p-0">
                  <div className="overflow-x-auto max-h-[340px] overflow-y-auto">
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>Data</TableHead>
                          <TableHead>Descrição</TableHead>
                          <TableHead className="text-right">Valor</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {transactions.length === 0 ? (
                          <TableRow>
                            <TableCell colSpan={3} className="text-center py-8 text-muted-foreground">
                              Nenhuma transação registrada
                            </TableCell>
                          </TableRow>
                        ) : (
                          transactions.slice(0, 20).map((t) => (
                            <TableRow key={t.id}>
                              <TableCell className="text-xs text-muted-foreground whitespace-nowrap">
                                {t.data}
                              </TableCell>
                              <TableCell className="text-sm truncate max-w-[200px]">
                                <div className="flex items-center gap-2">
                                  <span
                                    className={cn(
                                      "w-1.5 h-1.5 rounded-full shrink-0",
                                      t.tipo === "receita" ? "bg-emerald-500" : "bg-red-500"
                                    )}
                                  />
                                  {t.descricao}
                                </div>
                              </TableCell>
                              <TableCell
                                className={cn(
                                  "text-right text-sm font-semibold tabular-nums",
                                  t.tipo === "receita"
                                    ? "text-emerald-600 dark:text-emerald-400"
                                    : "text-red-600 dark:text-red-400"
                                )}
                              >
                                {t.tipo === "despesa" ? "-" : ""}
                                {formatCurrency(t.valor)}
                              </TableCell>
                            </TableRow>
                          ))
                        )}
                      </TableBody>
                    </Table>
                  </div>
                </CardContent>
              </Card>
            </div>

            {/* Account Balances */}
            {accounts.length > 0 && (
              <Card className="shadow-card">
                <CardHeader className="pb-2">
                  <CardTitle className="text-sm font-semibold flex items-center gap-2">
                    <DollarSign className="h-4 w-4 text-muted-foreground" />
                    Saldos por Conta
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                    {accounts.map((acc) => (
                      <div
                        key={acc.id}
                        className="flex items-center justify-between p-3 rounded-lg bg-muted/40 hover:bg-muted/60 transition-colors"
                      >
                        <div className="min-w-0">
                          <p className="text-sm font-medium text-foreground truncate">
                            {acc.nome}
                          </p>
                          <p className="text-xs text-muted-foreground">
                            {acc.banco} • {acc.tipo}
                          </p>
                        </div>
                        <div className="text-right shrink-0 ml-3">
                          <p
                            className={cn(
                              "text-sm font-bold tabular-nums",
                              acc.saldo >= 0
                                ? "text-emerald-600 dark:text-emerald-400"
                                : "text-red-600 dark:text-red-400"
                            )}
                          >
                            {formatCompactCurrency(acc.saldo)}
                          </p>
                          <Badge
                            variant={acc.status === "ativa" ? "default" : "secondary"}
                            className="text-xs mt-1"
                          >
                            {acc.status}
                          </Badge>
                        </div>
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>
            )}
          </>
        )}
      </div>
    </RouteGuard>
  )
}
