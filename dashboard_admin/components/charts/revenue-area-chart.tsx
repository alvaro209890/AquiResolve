'use client'

import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
} from 'recharts'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useMemo } from 'react'

interface RevenueChartProps {
  data: { date: string; receita: number; transacoes: number }[]
  title?: string
  height?: number
  loading?: boolean
}

const formatCurrency = (value: number) =>
  new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(value)

const formatDateShort = (dateStr: string) => {
  const d = new Date(dateStr + 'T00:00:00')
  return d.toLocaleDateString('pt-BR', { day: '2-digit', month: 'short' })
}

export function RevenueAreaChart({
  data,
  title = 'Receita ao Longo do Tempo',
  height = 300,
  loading = false,
}: RevenueChartProps) {
  const chartData = useMemo(
    () =>
      data.map((d) => ({
        ...d,
        label: formatDateShort(d.date),
        receitaFormatada: d.receita,
      })),
    [data]
  )

  if (loading) {
    return (
      <Card className="shadow-card">
        <CardHeader className="pb-2">
          <CardTitle className="text-sm font-semibold">{title}</CardTitle>
        </CardHeader>
        <CardContent className="flex items-center justify-center" style={{ height }}>
          <div className="space-y-3 w-full animate-pulse">
            <div className="h-4 bg-muted rounded w-1/3 mx-auto" />
            <div className="h-[200px] bg-muted/50 rounded" />
          </div>
        </CardContent>
      </Card>
    )
  }

  if (!data.length) {
    return (
      <Card className="shadow-card">
        <CardHeader className="pb-2">
          <CardTitle className="text-sm font-semibold">{title}</CardTitle>
        </CardHeader>
        <CardContent
          className="flex items-center justify-center text-muted-foreground text-sm"
          style={{ height }}
        >
          Sem dados no período selecionado
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className="shadow-card">
      <CardHeader className="pb-2">
        <CardTitle className="text-sm font-semibold">{title}</CardTitle>
      </CardHeader>
      <CardContent>
        <div style={{ width: '100%', height }}>
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart
              data={chartData}
              margin={{ top: 5, right: 10, left: 10, bottom: 5 }}
            >
              <defs>
                <linearGradient id="colorReceita" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="var(--chart-1, #10b981)" stopOpacity={0.3} />
                  <stop offset="95%" stopColor="var(--chart-1, #10b981)" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid
                strokeDasharray="3 3"
                stroke="var(--border)"
                opacity={0.4}
                vertical={false}
              />
              <XAxis
                dataKey="label"
                tick={{ fontSize: 11, fill: 'var(--muted-foreground)' }}
                tickLine={false}
                axisLine={false}
                interval="preserveStartEnd"
              />
              <YAxis
                tick={{ fontSize: 11, fill: 'var(--muted-foreground)' }}
                tickLine={false}
                axisLine={false}
                tickFormatter={formatCurrency}
                width={60}
              />
              <Tooltip
                contentStyle={{
                  backgroundColor: 'var(--popover)',
                  border: '1px solid var(--border)',
                  borderRadius: '8px',
                  fontSize: 12,
                  color: 'var(--popover-foreground)',
                }}
                formatter={(value: number) => [formatCurrency(value), 'Receita']}
                labelFormatter={(label: string) => label}
              />
              <Area
                type="monotone"
                dataKey="receita"
                stroke="var(--chart-1, #10b981)"
                strokeWidth={2}
                fillOpacity={1}
                fill="url(#colorReceita)"
                name="Receita"
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </CardContent>
    </Card>
  )
}
