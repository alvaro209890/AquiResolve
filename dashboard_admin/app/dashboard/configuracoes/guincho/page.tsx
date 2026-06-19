"use client"

import { useCallback, useEffect, useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Switch } from "@/components/ui/switch"
import { useToast } from "@/hooks/use-toast"
import { Truck, DollarSign, Percent, Save, RefreshCw, Info } from "lucide-react"

interface GuinchoConfig {
  enabled: boolean
  baseFee: number
  pricePerKm: number
  providerPercent: number
  minKm: number
}

const DEFAULTS: GuinchoConfig = {
  enabled: true,
  baseFee: 180,
  pricePerKm: 3.9,
  providerPercent: 70,
  minKm: 0,
}

const brl = (v: number) =>
  new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(v || 0)

export default function GuinchoConfigPage() {
  const { toast } = useToast()
  const [config, setConfig] = useState<GuinchoConfig>(DEFAULTS)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  // Exemplo de simulação
  const [simKm, setSimKm] = useState(10)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const res = await fetch("/api/guincho-config")
      const data = await res.json()
      if (data.success && data.config) {
        setConfig({ ...DEFAULTS, ...data.config })
      }
    } catch {
      toast({ title: "Erro ao carregar", description: "Usando valores padrão.", variant: "destructive" })
    } finally {
      setLoading(false)
    }
  }, [toast])

  useEffect(() => {
    load()
  }, [load])

  const save = async () => {
    setSaving(true)
    try {
      const res = await fetch("/api/guincho-config", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(config),
      })
      const data = await res.json()
      if (data.success) {
        toast({ title: "Salvo!", description: "Precificação do guincho atualizada." })
      } else {
        throw new Error(data.error || "Falha ao salvar")
      }
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : String(e)
      toast({ title: "Erro ao salvar", description: message, variant: "destructive" })
    } finally {
      setSaving(false)
    }
  }

  const set = (patch: Partial<GuinchoConfig>) => setConfig((c) => ({ ...c, ...patch }))

  const billableKm = Math.max(simKm, config.minKm || 0)
  const simTotal = config.baseFee + billableKm * config.pricePerKm
  const simProvider = (simTotal * config.providerPercent) / 100
  const simPlatform = simTotal - simProvider

  return (
    <div className="space-y-6 p-4 md:p-6 max-w-3xl">
      <div className="flex items-center gap-3">
        <div className="rounded-lg bg-amber-100 p-2 text-amber-700">
          <Truck className="h-6 w-6" />
        </div>
        <div>
          <h1 className="text-2xl font-bold">Guincho</h1>
          <p className="text-sm text-muted-foreground">
            Taxa de saída + valor por km (origem → destino). O app recalcula sempre por aqui.
          </p>
        </div>
      </div>

      {loading ? (
        <div className="flex items-center gap-2 text-muted-foreground">
          <RefreshCw className="h-4 w-4 animate-spin" /> Carregando…
        </div>
      ) : (
        <>
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center justify-between text-base">
                <span>Serviço de guincho ativo</span>
                <Switch checked={config.enabled} onCheckedChange={(v) => set({ enabled: v })} />
              </CardTitle>
            </CardHeader>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <DollarSign className="h-4 w-4" /> Valores
              </CardTitle>
            </CardHeader>
            <CardContent className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div>
                <label className="text-sm font-medium">Taxa de saída (R$)</label>
                <Input
                  type="number"
                  step="0.01"
                  value={config.baseFee}
                  onChange={(e) => set({ baseFee: Number(e.target.value) })}
                />
                <p className="mt-1 text-xs text-muted-foreground">Cobrada sempre, independente da distância.</p>
              </div>
              <div>
                <label className="text-sm font-medium">Valor por km (R$)</label>
                <Input
                  type="number"
                  step="0.01"
                  value={config.pricePerKm}
                  onChange={(e) => set({ pricePerKm: Number(e.target.value) })}
                />
                <p className="mt-1 text-xs text-muted-foreground">Multiplicado pela distância do trajeto.</p>
              </div>
              <div>
                <label className="text-sm font-medium flex items-center gap-1">
                  <Percent className="h-3 w-3" /> Repasse ao motorista (%)
                </label>
                <Input
                  type="number"
                  step="1"
                  min={0}
                  max={100}
                  value={config.providerPercent}
                  onChange={(e) => set({ providerPercent: Number(e.target.value) })}
                />
                <p className="mt-1 text-xs text-muted-foreground">% do total que o prestador recebe.</p>
              </div>
              <div>
                <label className="text-sm font-medium">Km mínimo cobrado</label>
                <Input
                  type="number"
                  step="0.1"
                  min={0}
                  value={config.minKm}
                  onChange={(e) => set({ minKm: Number(e.target.value) })}
                />
                <p className="mt-1 text-xs text-muted-foreground">0 = cobra exatamente a distância real.</p>
              </div>
            </CardContent>
          </Card>

          <Card className="border-amber-200 bg-amber-50/50">
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <Info className="h-4 w-4" /> Simulação
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="flex items-center gap-2">
                <label className="text-sm">Distância (km):</label>
                <Input
                  type="number"
                  step="0.1"
                  className="w-28"
                  value={simKm}
                  onChange={(e) => setSimKm(Number(e.target.value))}
                />
              </div>
              <div className="grid grid-cols-3 gap-2 text-center text-sm">
                <div className="rounded-lg bg-white p-3 shadow-sm">
                  <div className="text-xs text-muted-foreground">Cliente paga</div>
                  <div className="text-lg font-bold text-amber-700">{brl(simTotal)}</div>
                </div>
                <div className="rounded-lg bg-white p-3 shadow-sm">
                  <div className="text-xs text-muted-foreground">Motorista recebe</div>
                  <div className="text-lg font-bold text-emerald-700">{brl(simProvider)}</div>
                </div>
                <div className="rounded-lg bg-white p-3 shadow-sm">
                  <div className="text-xs text-muted-foreground">Plataforma fica</div>
                  <div className="text-lg font-bold text-slate-700">{brl(simPlatform)}</div>
                </div>
              </div>
              <p className="text-xs text-muted-foreground">
                {brl(config.baseFee)} de saída + {billableKm.toFixed(1)} km × {brl(config.pricePerKm)}
              </p>
            </CardContent>
          </Card>

          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={load} disabled={saving}>
              <RefreshCw className="mr-2 h-4 w-4" /> Recarregar
            </Button>
            <Button onClick={save} disabled={saving}>
              <Save className="mr-2 h-4 w-4" /> {saving ? "Salvando…" : "Salvar"}
            </Button>
          </div>
        </>
      )}
    </div>
  )
}
