"use client"

import { useEffect, useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Star } from "lucide-react"
import { FirestoreAnalyticsService } from "@/lib/services/firestore-analytics-simple"

type Distribution = Record<1 | 2 | 3 | 4 | 5, number>

interface State {
  loading: boolean
  averageRating: number
  totalRated: number
  distribution: Distribution
}

const EMPTY_DIST: Distribution = { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 }

export function RatingsBreakdown() {
  const [state, setState] = useState<State>({
    loading: true,
    averageRating: 0,
    totalRated: 0,
    distribution: EMPTY_DIST,
  })

  useEffect(() => {
    let cancelled = false
    FirestoreAnalyticsService.getOrdersMetrics()
      .then((m) => {
        if (cancelled) return
        setState({
          loading: false,
          averageRating: Number(m.averageRating ?? 0),
          totalRated: Number(m.totalRated ?? 0),
          distribution: (m.ratingDistribution as Distribution) ?? EMPTY_DIST,
        })
      })
      .catch(() => {
        if (cancelled) return
        setState({ loading: false, averageRating: 0, totalRated: 0, distribution: EMPTY_DIST })
      })
    return () => {
      cancelled = true
    }
  }, [])

  const { loading, averageRating, totalRated, distribution } = state
  const max = Math.max(1, ...Object.values(distribution))

  return (
    <Card className="shadow-card">
      <CardHeader className="border-b border-border bg-muted/20 py-4">
        <div className="flex items-center gap-3">
          <div className="h-8 w-8 rounded-lg bg-amber-50 dark:bg-amber-950/40 flex items-center justify-center">
            <Star className="h-4 w-4 text-amber-600" />
          </div>
          <div>
            <CardTitle className="text-base font-semibold">Avaliações dos Clientes</CardTitle>
            <p className="text-xs text-muted-foreground mt-0.5">
              Distribuição de notas dadas aos pedidos
            </p>
          </div>
        </div>
      </CardHeader>
      <CardContent className="p-5">
        {loading ? (
          <div className="space-y-3">
            {[5, 4, 3, 2, 1].map((s) => (
              <Skeleton key={s} className="h-4 w-full animate-skeleton" />
            ))}
          </div>
        ) : totalRated === 0 ? (
          <div className="py-8 text-center">
            <Star className="h-8 w-8 mx-auto text-muted-foreground/40 mb-2" />
            <p className="text-sm font-medium text-foreground">Nenhuma avaliação ainda</p>
            <p className="text-xs text-muted-foreground mt-1">
              Os clientes avaliam o serviço após a conclusão.
            </p>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="flex items-baseline gap-3">
              <span className="text-4xl font-bold tabular-nums">
                {averageRating.toFixed(1)}
              </span>
              <span className="text-amber-500 text-lg">★</span>
              <span className="text-sm text-muted-foreground ml-auto">
                {totalRated.toLocaleString("pt-BR")} avaliações
              </span>
            </div>
            <div className="space-y-2">
              {([5, 4, 3, 2, 1] as const).map((stars) => {
                const count = distribution[stars] ?? 0
                const percent = totalRated > 0 ? (count / totalRated) * 100 : 0
                const widthPercent = (count / max) * 100
                return (
                  <div key={stars} className="flex items-center gap-3 text-xs">
                    <span className="w-6 text-foreground font-medium tabular-nums">
                      {stars}★
                    </span>
                    <div className="flex-1 h-2.5 bg-muted rounded-full overflow-hidden">
                      <div
                        className="h-full bg-amber-500 transition-all"
                        style={{ width: `${widthPercent}%` }}
                      />
                    </div>
                    <span className="w-16 text-right text-muted-foreground tabular-nums">
                      {count.toLocaleString("pt-BR")} ({percent.toFixed(0)}%)
                    </span>
                  </div>
                )
              })}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
