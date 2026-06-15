import { redirect } from "next/navigation"

interface AnalyticsPageProps {
  searchParams?: Promise<{
    tab?: string | string[]
  }>
}

const legacyTabMap: Record<string, string> = {
  performance: "performance",
  evolution: "analytics",
  payments: "analytics",
  insights: "analytics",
  financial: "overview",
}

export default async function AnalyticsPage({ searchParams }: AnalyticsPageProps) {
  const resolvedSearchParams = await searchParams
  const incomingTab = Array.isArray(resolvedSearchParams?.tab) ? resolvedSearchParams?.tab[0] : resolvedSearchParams?.tab
  const mappedTab = incomingTab ? legacyTabMap[incomingTab] || "analytics" : "analytics"

  redirect(`/reports?tab=${mappedTab}`)
}
