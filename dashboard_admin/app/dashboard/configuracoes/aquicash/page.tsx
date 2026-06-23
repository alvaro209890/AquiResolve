"use client"

import { useCallback, useState, useEffect } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import { Switch } from "@/components/ui/switch"
import { useToast } from "@/hooks/use-toast"
import { adminFetch } from "@/lib/admin-api"
import { DollarSign, Award, Percent, Save, RefreshCw, Info, Plus, Trash2 } from "lucide-react"

interface CashbackConfig {
  enabled: boolean
  activePhase: "growth" | "launch"
  earnPercentage: number
  allowRedeem: boolean
  maxRedeemPercentage: number
  tiersEnabled: boolean
  bronzeRate: number
  silverRate: number
  goldRate: number
  silverThreshold: number
  goldThreshold: number
  directDiscountEnabled: boolean
  directDiscount2: number
  directDiscount3: number
  directDiscount4Plus: number
  combosEnabled: boolean
  comboEletricaHidraulicaInstalacoes: number
  comboEletricaHidraulica: number
  comboInstalacoesHidraulica: number
  comboVeiculos: number
}

interface CashbackCampaign {
  id: string
  name: string
  enabled: boolean
  bonusPercentage: number
  maxCashbackPerOrder?: number
  startsAt?: string | null
  endsAt?: string | null
}

interface CampaignDraft {
  name: string
  bonusPercentage: number
  maxCashbackPerOrder: number
  startsAt: string
  endsAt: string
  enabled: boolean
}

const DEFAULTS: CashbackConfig = {
  enabled: true,
  activePhase: "growth",
  earnPercentage: 5,
  allowRedeem: true,
  maxRedeemPercentage: 100,
  tiersEnabled: true,
  bronzeRate: 3,
  silverRate: 5,
  goldRate: 8,
  silverThreshold: 500,
  goldThreshold: 1500,
  directDiscountEnabled: true,
  directDiscount2: 5,
  directDiscount3: 10,
  directDiscount4Plus: 15,
  combosEnabled: true,
  comboEletricaHidraulicaInstalacoes: 15,
  comboEletricaHidraulica: 10,
  comboInstalacoesHidraulica: 10,
  comboVeiculos: 15,
}

const CAMPAIGN_DEFAULTS: CampaignDraft = {
  name: "",
  bonusPercentage: 5,
  maxCashbackPerOrder: 0,
  startsAt: "",
  endsAt: "",
  enabled: true,
}

function NumberField({
  label,
  value,
  onChange,
  suffix = "%",
  min = 0,
  max = 100,
}: {
  label: string
  value: number
  onChange: (v: number) => void
  suffix?: string
  min?: number
  max?: number
}) {
  return (
    <div className="flex items-center justify-between gap-4">
      <span className="text-sm text-muted-foreground">{label}</span>
      <div className="flex items-center gap-1">
        <Input
          type="number"
          className="w-24 h-8 text-right"
          value={value}
          min={min}
          max={max}
          onChange={e => onChange(Number(e.target.value))}
        />
        <span className="text-sm text-muted-foreground">{suffix}</span>
      </div>
    </div>
  )
}

export default function AquiCashConfigPage() {
  const [config, setConfig] = useState<CashbackConfig>(DEFAULTS)
  const [campaigns, setCampaigns] = useState<CashbackCampaign[]>([])
  const [campaignDraft, setCampaignDraft] = useState<CampaignDraft>(CAMPAIGN_DEFAULTS)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [campaignSaving, setCampaignSaving] = useState(false)
  const [campaignUpdating, setCampaignUpdating] = useState<string | null>(null)
  const { toast } = useToast()

  const set = <K extends keyof CashbackConfig>(key: K, value: CashbackConfig[K]) =>
    setConfig(prev => ({ ...prev, [key]: value }))

  const setDraft = <K extends keyof CampaignDraft>(key: K, value: CampaignDraft[K]) =>
    setCampaignDraft(prev => ({ ...prev, [key]: value }))

  const loadCampaigns = useCallback(async () => {
    const res = await adminFetch("/api/cashback-campaigns")
    const data = await res.json()
    if (data.success) {
      setCampaigns(data.campaigns || [])
    }
  }, [])

  useEffect(() => {
    async function load() {
      try {
        const configResponse = await adminFetch("/api/cashback-config")
        const configData = await configResponse.json()
        if (configData.success && configData.config) setConfig({ ...DEFAULTS, ...configData.config })
        await loadCampaigns()
      } catch {
        // página mantém defaults locais quando o backend não responde
      } finally {
        setLoading(false)
      }
    }

    void load()
  }, [loadCampaigns])

  async function save() {
    setSaving(true)
    try {
      const res = await adminFetch("/api/cashback-config", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(config),
      })
      const data = await res.json()
      if (!data.success) throw new Error(data.error)
      toast({ title: "AquiCash salvo", description: "Configurações salvas no Firestore com sucesso." })
    } catch (e: unknown) {
      toast({
        title: "Erro ao salvar",
        description: e instanceof Error ? e.message : String(e),
        variant: "destructive",
      })
    } finally {
      setSaving(false)
    }
  }

  async function createCampaign() {
    setCampaignSaving(true)
    try {
      const res = await adminFetch("/api/cashback-campaigns", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          ...campaignDraft,
          startsAt: campaignDraft.startsAt || null,
          endsAt: campaignDraft.endsAt || null,
        }),
      })
      const data = await res.json()
      if (!data.success) throw new Error(data.error)
      setCampaignDraft(CAMPAIGN_DEFAULTS)
      await loadCampaigns()
      toast({ title: "Campanha criada", description: "A campanha será considerada nos próximos serviços concluídos." })
    } catch (e: unknown) {
      toast({
        title: "Erro ao criar campanha",
        description: e instanceof Error ? e.message : String(e),
        variant: "destructive",
      })
    } finally {
      setCampaignSaving(false)
    }
  }

  async function updateCampaign(id: string, payload: Partial<CashbackCampaign>) {
    setCampaignUpdating(id)
    try {
      const res = await adminFetch(`/api/cashback-campaigns/${id}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      })
      const data = await res.json()
      if (!data.success) throw new Error(data.error)
      await loadCampaigns()
    } catch (e: unknown) {
      toast({
        title: "Erro ao atualizar campanha",
        description: e instanceof Error ? e.message : String(e),
        variant: "destructive",
      })
    } finally {
      setCampaignUpdating(null)
    }
  }

  async function deleteCampaign(id: string) {
    if (!confirm("Remover esta campanha de cashback?")) return
    setCampaignUpdating(id)
    try {
      const res = await adminFetch(`/api/cashback-campaigns/${id}`, { method: "DELETE" })
      const data = await res.json()
      if (!data.success) throw new Error(data.error)
      await loadCampaigns()
      toast({ title: "Campanha removida" })
    } catch (e: unknown) {
      toast({
        title: "Erro ao remover campanha",
        description: e instanceof Error ? e.message : String(e),
        variant: "destructive",
      })
    } finally {
      setCampaignUpdating(null)
    }
  }

  function formatCampaignDate(value?: string | null) {
    if (!value) return "Sem limite"
    return new Date(value).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" })
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-48">
        <RefreshCw className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    )
  }

  return (
    <div className="space-y-6 max-w-3xl">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="h-9 w-9 rounded-lg bg-yellow-500/10 flex items-center justify-center">
            <Award className="h-5 w-5 text-yellow-600" />
          </div>
          <div>
            <h1 className="text-2xl font-bold tracking-tight">Programa AquiCash</h1>
            <p className="text-sm text-muted-foreground">
              Configurações de cashback e descontos do app mobile
            </p>
          </div>
        </div>
        <Button onClick={save} disabled={saving} className="gap-2">
          <Save className="h-4 w-4" />
          {saving ? "Salvando…" : "Salvar"}
        </Button>
      </div>

      {/* Geral */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <DollarSign className="h-4 w-4" />
            Configurações Gerais
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium">Programa Ativo</p>
              <p className="text-xs text-muted-foreground">Habilita/desabilita o cashback no app mobile</p>
            </div>
            <Switch checked={config.enabled} onCheckedChange={v => set("enabled", v)} />
          </div>

          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium">Permitir Resgate</p>
              <p className="text-xs text-muted-foreground">Clientes podem usar cashback como desconto</p>
            </div>
            <Switch checked={config.allowRedeem} onCheckedChange={v => set("allowRedeem", v)} />
          </div>

          <NumberField
            label="Máximo resgatável por pedido (%)"
            value={config.maxRedeemPercentage}
            onChange={v => set("maxRedeemPercentage", v)}
          />

          <div className="space-y-2">
            <p className="text-sm font-medium">Fase Ativa</p>
            <p className="text-xs text-muted-foreground">
              <strong>1ª Fase — Lançamento:</strong> desconto direto por nº de serviços no carrinho
              <br />
              <strong>2ª Fase — Crescimento:</strong> cashback em níveis Bronze/Prata/Ouro
            </p>
            <div className="flex gap-2">
              <Button
                variant={config.activePhase === "growth" ? "default" : "outline"}
                size="sm"
                onClick={() => set("activePhase", "growth")}
              >
                2ª Fase — Crescimento
              </Button>
              <Button
                variant={config.activePhase === "launch" ? "default" : "outline"}
                size="sm"
                onClick={() => set("activePhase", "launch")}
              >
                1ª Fase — Lançamento
              </Button>
            </div>
            <Badge variant={config.activePhase === "growth" ? "default" : "secondary"}>
              {config.activePhase === "growth" ? "Cashback em níveis ativo" : "Desconto direto ativo"}
            </Badge>
          </div>
        </CardContent>
      </Card>

      {/* 2ª Fase — Níveis */}
      <Card className={config.activePhase !== "growth" ? "opacity-60" : ""}>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Award className="h-4 w-4" />
            2ª Fase — Cashback em Níveis (Bronze / Prata / Ouro)
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium">Níveis habilitados</p>
              <p className="text-xs text-muted-foreground">Se desligado, usa percentual único</p>
            </div>
            <Switch checked={config.tiersEnabled} onCheckedChange={v => set("tiersEnabled", v)} />
          </div>

          {config.tiersEnabled ? (
            <div className="space-y-3 pl-2 border-l-2 border-muted">
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                <span className="text-orange-600 font-semibold">Bronze</span> — até R$ {config.silverThreshold}
              </div>
              <NumberField label="Taxa Bronze" value={config.bronzeRate} onChange={v => set("bronzeRate", v)} />

              <div className="flex items-center gap-2 text-xs text-muted-foreground mt-2">
                <span className="text-gray-500 font-semibold">Prata</span> — R$ {config.silverThreshold} a R$ {config.goldThreshold}
              </div>
              <NumberField label="Limite inferior Prata (R$)" value={config.silverThreshold} onChange={v => set("silverThreshold", v)} suffix="R$" min={0} max={99999} />
              <NumberField label="Taxa Prata" value={config.silverRate} onChange={v => set("silverRate", v)} />

              <div className="flex items-center gap-2 text-xs text-muted-foreground mt-2">
                <span className="text-yellow-600 font-semibold">Ouro</span> — acima de R$ {config.goldThreshold}
              </div>
              <NumberField label="Limite inferior Ouro (R$)" value={config.goldThreshold} onChange={v => set("goldThreshold", v)} suffix="R$" min={0} max={99999} />
              <NumberField label="Taxa Ouro" value={config.goldRate} onChange={v => set("goldRate", v)} />
            </div>
          ) : (
            <NumberField label="Taxa única de cashback" value={config.earnPercentage} onChange={v => set("earnPercentage", v)} />
          )}
        </CardContent>
      </Card>

      {/* 1ª Fase — Desconto direto */}
      <Card className={config.activePhase !== "launch" ? "opacity-60" : ""}>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Percent className="h-4 w-4" />
            1ª Fase — Desconto Direto por Nº de Serviços
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-center justify-between">
            <p className="text-sm font-medium">Desconto direto habilitado</p>
            <Switch checked={config.directDiscountEnabled} onCheckedChange={v => set("directDiscountEnabled", v)} />
          </div>
          <NumberField label="2 serviços no carrinho" value={config.directDiscount2} onChange={v => set("directDiscount2", v)} />
          <NumberField label="3 serviços no carrinho" value={config.directDiscount3} onChange={v => set("directDiscount3", v)} />
          <NumberField label="4+ serviços no carrinho" value={config.directDiscount4Plus} onChange={v => set("directDiscount4Plus", v)} />
        </CardContent>
      </Card>

      {/* Combos */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Info className="h-4 w-4" />
            Combos Especiais (valem nas duas fases)
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-center justify-between">
            <p className="text-sm font-medium">Combos habilitados</p>
            <Switch checked={config.combosEnabled} onCheckedChange={v => set("combosEnabled", v)} />
          </div>
          <NumberField label="Elétrica + Hidráulica + Instalações" value={config.comboEletricaHidraulicaInstalacoes} onChange={v => set("comboEletricaHidraulicaInstalacoes", v)} />
          <NumberField label="Elétrica + Hidráulica" value={config.comboEletricaHidraulica} onChange={v => set("comboEletricaHidraulica", v)} />
          <NumberField label="Instalações + Hidráulica" value={config.comboInstalacoesHidraulica} onChange={v => set("comboInstalacoesHidraulica", v)} />
          <NumberField label="Veículos (2+ serviços automotivos)" value={config.comboVeiculos} onChange={v => set("comboVeiculos", v)} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Percent className="h-4 w-4" />
            Campanhas promocionais de cashback
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-5">
          <div className="grid gap-3 md:grid-cols-2">
            <div className="space-y-2">
              <span className="text-sm font-medium">Nome da campanha</span>
              <Input
                value={campaignDraft.name}
                onChange={e => setDraft("name", e.target.value)}
                placeholder="Ex.: Semana do cliente"
              />
            </div>
            <NumberField
              label="Bônus de cashback"
              value={campaignDraft.bonusPercentage}
              onChange={v => setDraft("bonusPercentage", v)}
            />
            <NumberField
              label="Limite por OS"
              value={campaignDraft.maxCashbackPerOrder}
              onChange={v => setDraft("maxCashbackPerOrder", v)}
              suffix="R$"
              max={99999}
            />
            <div className="flex items-center justify-between gap-4">
              <span className="text-sm text-muted-foreground">Campanha ativa</span>
              <Switch checked={campaignDraft.enabled} onCheckedChange={v => setDraft("enabled", v)} />
            </div>
            <div className="space-y-2">
              <span className="text-sm font-medium">Início</span>
              <Input
                type="datetime-local"
                value={campaignDraft.startsAt}
                onChange={e => setDraft("startsAt", e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <span className="text-sm font-medium">Fim</span>
              <Input
                type="datetime-local"
                value={campaignDraft.endsAt}
                onChange={e => setDraft("endsAt", e.target.value)}
              />
            </div>
          </div>

          <div className="flex justify-end">
            <Button onClick={createCampaign} disabled={campaignSaving || !campaignDraft.name.trim()} className="gap-2">
              <Plus className="h-4 w-4" />
              {campaignSaving ? "Criando…" : "Criar campanha"}
            </Button>
          </div>

          <div className="space-y-3">
            {campaigns.length === 0 ? (
              <p className="rounded border border-dashed p-4 text-sm text-muted-foreground">
                Nenhuma campanha promocional cadastrada.
              </p>
            ) : (
              campaigns.map(campaign => (
                <div key={campaign.id} className="flex flex-col gap-3 rounded border p-3 sm:flex-row sm:items-center sm:justify-between">
                  <div className="min-w-0 space-y-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="font-medium">{campaign.name}</p>
                      <Badge variant={campaign.enabled ? "default" : "secondary"}>
                        {campaign.enabled ? "Ativa" : "Pausada"}
                      </Badge>
                      <Badge variant="outline">+{campaign.bonusPercentage}%</Badge>
                    </div>
                    <p className="text-xs text-muted-foreground">
                      {formatCampaignDate(campaign.startsAt)} até {formatCampaignDate(campaign.endsAt)}
                      {campaign.maxCashbackPerOrder ? ` · limite R$ ${campaign.maxCashbackPerOrder}` : ""}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <Switch
                      checked={campaign.enabled}
                      disabled={campaignUpdating === campaign.id}
                      onCheckedChange={v => void updateCampaign(campaign.id, { enabled: v })}
                    />
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      disabled={campaignUpdating === campaign.id}
                      onClick={() => void deleteCampaign(campaign.id)}
                      aria-label="Remover campanha"
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              ))
            )}
          </div>
        </CardContent>
      </Card>

      <div className="flex justify-end">
        <Button onClick={save} disabled={saving} className="gap-2">
          <Save className="h-4 w-4" />
          {saving ? "Salvando…" : "Salvar configurações"}
        </Button>
      </div>
    </div>
  )
}
