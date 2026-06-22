"use client"

import dynamic from "next/dynamic"
import { Suspense } from "react"
import { Button } from "@/components/ui/button"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { LayoutGrid, BarChart3, MapPin, RefreshCw, TrendingUp, FileText, Settings, Loader2 } from "lucide-react"
import { GoogleMapsLoader } from "@/components/map/google-maps-loader"
import { DashboardMetrics } from "@/components/dashboard/dashboard-metrics"
import { RecentActivity } from "@/components/dashboard/recent-activity"
import { ServicesAnalytics } from "@/components/dashboard/services-analytics"
import { RatingsBreakdown } from "@/components/dashboard/ratings-breakdown"
import { useAnalytics } from "@/hooks/use-analytics"
import { useEffect, useState, useCallback } from "react"
import Link from "next/link"

const ProvidersMap = dynamic(
  () => import("@/components/map/providers-map").then(m => ({ default: m.ProvidersMap })),
  { ssr: false, loading: () => <div className="h-full flex items-center justify-center"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div> }
)

const AnalyticsDashboard = dynamic(
  () => import("@/components/analytics/analytics-dashboard").then(m => ({ default: m.AnalyticsDashboard })),
  { ssr: false, loading: () => <div className="flex items-center justify-center py-16"><Loader2 className="h-6 w-6 animate-spin text-muted-foreground" /></div> }
)

export default function DashboardPage() {
  const { trackPageView, trackUserAction } = useAnalytics()
  const [refreshKey, setRefreshKey] = useState(0)

  useEffect(() => {
    trackPageView("Dashboard Principal")
  }, [trackPageView])

  const handleRefresh = useCallback(() => {
    setRefreshKey(k => k + 1)
    trackUserAction("atualizar_metricas", "dashboard")
  }, [trackUserAction])

  const currentDate = new Date().toLocaleDateString("pt-BR", {
    weekday: "long",
    year: "numeric",
    month: "long",
    day: "numeric",
  })

  return (
    <div className="w-full space-y-6 animate-fade-in">
      {/* Page header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <div className="h-9 w-9 rounded-lg bg-primary/10 flex items-center justify-center">
              <TrendingUp className="h-5 w-5 text-primary" />
            </div>
            <h1 className="text-2xl font-bold tracking-tight text-foreground">Dashboard</h1>
          </div>
          <p className="text-sm text-muted-foreground mt-1 ml-12 capitalize">{currentDate}</p>
        </div>

        <div className="flex items-center gap-2 ml-12 sm:ml-0">
          <Link href="/reports" onClick={() => trackUserAction("ver_relatorios", "dashboard")}>
            <Button variant="outline" size="sm" className="gap-2">
              <FileText className="h-4 w-4" />
              Relatórios
            </Button>
          </Link>
          <Link href="/dashboard/configuracoes" onClick={() => trackUserAction("abrir_configuracoes", "dashboard")}>
            <Button size="sm" className="gap-2 bg-primary hover:bg-primary-hover text-white shadow-primary">
              <Settings className="h-4 w-4" />
              Configurações
            </Button>
          </Link>
        </div>
      </div>

      {/* Tabs */}
      <Tabs defaultValue="overview" className="w-full">
        <TabsList className="h-10 bg-muted/50 border border-border p-1 rounded-lg w-full sm:w-auto">
          <TabsTrigger
            value="overview"
            className="gap-2 rounded-md text-sm font-medium data-[state=active]:bg-card data-[state=active]:text-foreground data-[state=active]:shadow-sm"
          >
            <LayoutGrid className="h-4 w-4" />
            Visão Geral
          </TabsTrigger>
          <TabsTrigger
            value="analytics"
            className="gap-2 rounded-md text-sm font-medium data-[state=active]:bg-card data-[state=active]:text-foreground data-[state=active]:shadow-sm"
          >
            <BarChart3 className="h-4 w-4" />
            Analytics
          </TabsTrigger>
        </TabsList>

        {/* ── Overview ───────────────────────────────────────── */}
        <TabsContent value="overview" className="mt-6 space-y-8 animate-slide-up">

          {/* KPIs */}
          <section>
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground">
                Métricas Principais
              </h2>
              <Button
                variant="ghost"
                size="sm"
                onClick={handleRefresh}
                className="h-8 gap-1.5 text-muted-foreground hover:text-foreground text-xs"
              >
                <RefreshCw className="h-3.5 w-3.5" />
                Atualizar
              </Button>
            </div>
            <DashboardMetrics key={refreshKey} />
          </section>

          {/* Main grid */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="lg:col-span-2 space-y-3">
              <h2 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground">
                Análise de Serviços
              </h2>
              <ServicesAnalytics />
            </div>

            <div className="lg:col-span-1 space-y-3">
              <h2 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground">
                Atividades Recentes
              </h2>
              <Card className="shadow-card">
                <CardContent className="p-4">
                  <RecentActivity />
                </CardContent>
              </Card>
            </div>
          </div>

          {/* Avaliações */}
          <section>
            <h2 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground mb-3">
              Avaliações
            </h2>
            <RatingsBreakdown />
          </section>

          {/* Map */}
          <section>
            <Card className="shadow-card overflow-hidden">
              <CardHeader className="border-b border-border bg-muted/20 py-4">
                <div className="flex items-center gap-3">
                  <div className="h-8 w-8 rounded-lg bg-primary/10 flex items-center justify-center">
                    <MapPin className="h-4 w-4 text-primary" />
                  </div>
                  <div>
                    <CardTitle className="text-base font-semibold">Rastreamento em Tempo Real</CardTitle>
                    <p className="text-xs text-muted-foreground mt-0.5">Localização dos prestadores ativos</p>
                  </div>
                </div>
              </CardHeader>
              <CardContent className="p-0">
                <div className="h-[400px] sm:h-[440px]">
                  <GoogleMapsLoader />
                  <ProvidersMap />
                </div>
              </CardContent>
            </Card>
          </section>
        </TabsContent>

        {/* ── Analytics ──────────────────────────────────────── */}
        <TabsContent value="analytics" className="mt-6 animate-slide-up">
          <AnalyticsDashboard />
        </TabsContent>
      </Tabs>
    </div>
  )
}
