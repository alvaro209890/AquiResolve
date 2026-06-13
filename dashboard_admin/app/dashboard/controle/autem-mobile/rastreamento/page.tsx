"use client"

import { useState } from "react"
import { Button } from "@/components/ui/button"
import { MapPin, RefreshCw, Map, List } from "lucide-react"
import { ProvidersMap } from "@/components/map/providers-map"
import { useProviders } from "@/hooks/use-providers"
import { Badge } from "@/components/ui/badge"
import { formatDistanceToNow } from "date-fns"
import { ptBR } from "date-fns/locale"

const STATUS_COLOR: Record<string, string> = {
  disponivel: "bg-emerald-100 text-emerald-800",
  ocupado:    "bg-amber-100 text-amber-800",
  online:     "bg-blue-100 text-blue-800",
  offline:    "bg-muted text-muted-foreground",
}

export default function RastreamentoPage() {
  const [view, setView] = useState<"map" | "list">("map")
  const { providers, loading, refetch } = useProviders({ autoRefresh: true, ativo: true })

  const withGps = providers.filter(p => (p.localizacao?.lat ?? 0) !== 0 || (p.localizacao?.lng ?? 0) !== 0)

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="h-9 w-9 rounded-lg bg-purple-100 flex items-center justify-center">
            <MapPin className="h-5 w-5 text-purple-600" />
          </div>
          <div>
            <h1 className="text-2xl font-bold tracking-tight">Rastreamento em Tempo Real</h1>
            <p className="text-sm text-muted-foreground">
              {withGps.length} prestador(es) com GPS ativo de {providers.length} online
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <div className="flex rounded-lg border overflow-hidden">
            <Button
              variant={view === "map" ? "default" : "ghost"}
              size="sm" className="rounded-none gap-1.5"
              onClick={() => setView("map")}
            >
              <Map className="h-4 w-4" /> Mapa
            </Button>
            <Button
              variant={view === "list" ? "default" : "ghost"}
              size="sm" className="rounded-none gap-1.5"
              onClick={() => setView("list")}
            >
              <List className="h-4 w-4" /> Lista
            </Button>
          </div>
          <Button variant="outline" size="sm" onClick={refetch} disabled={loading} className="gap-2">
            <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} /> Atualizar
          </Button>
        </div>
      </div>

      {view === "map" ? (
        <div className="rounded-xl overflow-hidden border shadow-sm" style={{ height: "calc(100vh - 200px)", minHeight: 480 }}>
          <ProvidersMap />
        </div>
      ) : (
        <div className="divide-y border rounded-xl overflow-hidden">
          {providers.length === 0 && !loading && (
            <div className="flex items-center justify-center h-32 text-sm text-muted-foreground">
              Nenhum prestador online no momento
            </div>
          )}
          {providers.map(p => {
            const lat = p.localizacao?.lat ?? 0
            const lng = p.localizacao?.lng ?? 0
            const hasGps = lat !== 0 || lng !== 0
            const sc = STATUS_COLOR[p.status] ?? STATUS_COLOR.offline
            const lastUpdate = p.ultimaAtualizacao
              ? formatDistanceToNow(new Date(p.ultimaAtualizacao), { addSuffix: true, locale: ptBR })
              : "—"
            return (
              <div key={p.id} className="flex items-center gap-4 px-4 py-3 hover:bg-muted/30">
                <div className="h-9 w-9 rounded-full bg-primary/10 flex items-center justify-center text-sm font-semibold text-primary shrink-0">
                  {p.nome.charAt(0).toUpperCase()}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium">{p.nome}</span>
                    <Badge className={`text-xs ${sc}`}>{p.status}</Badge>
                    {hasGps && <Badge variant="outline" className="text-xs text-green-600 border-green-300">GPS ativo</Badge>}
                  </div>
                  <p className="text-xs text-muted-foreground">
                    {hasGps ? `${lat.toFixed(5)}, ${lng.toFixed(5)}` : "Sem localização"} · {lastUpdate}
                  </p>
                </div>
                {hasGps && (
                  <Button variant="outline" size="sm" asChild>
                    <a href={`https://www.google.com/maps?q=${lat},${lng}`} target="_blank" rel="noopener noreferrer">
                      <MapPin className="h-3 w-3 mr-1" /> Ver
                    </a>
                  </Button>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
