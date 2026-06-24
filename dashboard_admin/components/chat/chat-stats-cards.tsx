"use client"

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { useChatStats } from "@/hooks/use-chat"
import {
  AlertTriangle,
  MessageSquare,
  TrendingUp,
  Users,
} from "lucide-react"

export function ChatStatsCards() {
  const { stats, loading } = useChatStats()

  if (loading) {
    return (
      <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
        {[...Array(3)].map((_, i) => (
          <Card key={i} className="bg-card">
            <CardHeader className="pb-2">
              <div className="h-4 rounded bg-muted animate-skeleton"></div>
            </CardHeader>
            <CardContent>
              <div className="mb-2 h-8 rounded bg-muted animate-skeleton"></div>
              <div className="h-3 w-2/3 rounded bg-muted animate-skeleton"></div>
            </CardContent>
          </Card>
        ))}
      </div>
    )
  }

  if (!stats) return null

  const activePercentage =
    stats.totalConversations > 0
      ? ((stats.activeConversations / stats.totalConversations) * 100).toFixed(1)
      : "0.0"

  const items = [
    {
      title: "Conversas ativas",
      value: stats.activeConversations,
      sub: `${activePercentage}% do total`,
      icon: Users,
      cardClass: "border-orange-200 bg-gradient-to-br from-white to-orange-50/60",
      iconClass: "text-orange-500",
      subClass: "text-blue-600",
    },
    {
      title: "Não lidas",
      value: stats.unreadMessages,
      sub: "Requerem atenção",
      icon: AlertTriangle,
      cardClass: "border-red-200 bg-gradient-to-br from-white to-red-50/60",
      iconClass: "text-red-500",
      subClass: "text-red-600",
    },
    {
      title: "Total de mensagens",
      value: stats.totalMessages,
      sub: "Volume monitorado",
      icon: MessageSquare,
      cardClass: "border-blue-200 bg-gradient-to-br from-white to-blue-50/60",
      iconClass: "text-blue-500",
      subClass: "text-muted-foreground",
    },
  ] as const

  return (
    <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
      {items.map(({ title, value, sub, icon: Icon, cardClass, iconClass, subClass }) => (
        <Card key={title} className={`${cardClass} transition-shadow hover:shadow-card-hover`}>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground">
              {title}
            </CardTitle>
            <div className="rounded-full border bg-white/80 dark:bg-card/60 p-2">
              <Icon className={`h-4 w-4 ${iconClass}`} />
            </div>
          </CardHeader>
          <CardContent>
            <div className="flex items-end justify-between gap-3">
              <div className="text-2xl font-bold text-foreground">{value}</div>
              <TrendingUp className="h-4 w-4 text-muted-foreground/50" />
            </div>
            <p className={`mt-1 text-xs ${subClass}`}>
              {sub}
            </p>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}
