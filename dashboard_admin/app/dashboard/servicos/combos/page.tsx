"use client"

import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Switch } from "@/components/ui/switch"
import { useToast } from "@/hooks/use-toast"
import { storage } from "@/lib/firebase"
import { getDownloadURL, ref, uploadBytes } from "firebase/storage"
import {
  Flame,
  Plus,
  Save,
  Trash2,
  RefreshCw,
  Upload,
  Pencil,
  X,
  Check,
  AlertTriangle,
  CheckCircle2,
} from "lucide-react"

interface ComboItem {
  niche: string
  serviceName: string
  serviceId: string
}

interface Combo {
  id: string
  name: string
  description: string
  imageUrl: string
  items: ComboItem[]
  fullPrice: number
  promoPrice: number
  savings: number
  discountPercent: number
  active: boolean
  displayOrder: number
}

interface CatalogServiceRow {
  id: string
  niche: string
  name: string
  estimatedPrice: number
  active: boolean
}

interface CashbackConfig {
  combosEnabled: boolean
  comboEletricaHidraulicaInstalacoes: number
  comboEletricaHidraulica: number
  comboInstalacoesHidraulica: number
  comboVeiculos: number
}

const EMPTY: Combo = {
  id: "",
  name: "",
  description: "",
  imageUrl: "",
  items: [],
  fullPrice: 0,
  promoPrice: 0,
  savings: 0,
  discountPercent: 10,
  active: true,
  displayOrder: 0,
}

// Grupos de categorias do PromotionManager (app) — replicados para prever o desconto do carrinho.
const ELETRICA = ["Elétrica"]
const HIDRAULICA = [
  "Encanador",
  "Hidráulica",
  "Caixa d'água",
  "Desentupimento manual",
  "Desentupimento com maquinário até 2 m",
  "Caça-vazamentos",
]
const INSTALACOES = ["Instalação", "Eletrodomésticos", "Ar condicionado"]
const VEICULOS = ["Serviços automotivos"]

function inGroup(niche: string, group: string[]): boolean {
  return group.some((g) => g.toLowerCase() === niche.trim().toLowerCase())
}

// Replica PromotionManager.bestCombo: maior % entre os combos ativados pelas categorias.
function expectedCartCombo(
  niches: string[],
  cfg: CashbackConfig | null
): { label: string; percent: number } | null {
  if (!cfg || !cfg.combosEnabled) return null
  const hasEle = niches.some((n) => inGroup(n, ELETRICA))
  const hasHid = niches.some((n) => inGroup(n, HIDRAULICA))
  const hasInst = niches.some((n) => inGroup(n, INSTALACOES))
  const veiculos = niches.filter((n) => inGroup(n, VEICULOS)).length

  const candidates: { label: string; percent: number }[] = []
  if (hasEle && hasHid && hasInst)
    candidates.push({ label: "Elétrica + Hidráulica + Instalações", percent: cfg.comboEletricaHidraulicaInstalacoes })
  if (veiculos >= 2) candidates.push({ label: "Manutenção de veículos", percent: cfg.comboVeiculos })
  if (hasEle && hasHid) candidates.push({ label: "Elétrica + Hidráulica", percent: cfg.comboEletricaHidraulica })
  if (hasInst && hasHid) candidates.push({ label: "Instalações + Hidráulica", percent: cfg.comboInstalacoesHidraulica })

  const valid = candidates.filter((c) => c.percent > 0)
  if (valid.length === 0) return null
  return valid.reduce((best, c) => (c.percent > best.percent ? c : best))
}

function money(value: number): string {
  return `R$ ${value.toFixed(2).replace(".", ",")}`
}

export default function CombosPage() {
  const { toast } = useToast()
  const [combos, setCombos] = useState<Combo[]>([])
  const [services, setServices] = useState<CatalogServiceRow[]>([])
  const [cashbackCfg, setCashbackCfg] = useState<CashbackConfig | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [form, setForm] = useState<Combo>(EMPTY)
  const [showForm, setShowForm] = useState(false)
  const [serviceSearch, setServiceSearch] = useState("")
  const [showServices, setShowServices] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [combosRes, servicesRes, cfgRes] = await Promise.all([
        fetch("/api/combos").then((r) => r.json()),
        fetch("/api/catalog/services").then((r) => r.json()),
        fetch("/api/cashback-config").then((r) => r.json()),
      ])
      if (combosRes.success) setCombos(combosRes.combos as Combo[])
      if (servicesRes.success) {
        setServices(
          (servicesRes.services as CatalogServiceRow[]).filter((s) => s.active && s.name && s.niche)
        )
      }
      if (cfgRes.success && cfgRes.config) setCashbackCfg(cfgRes.config as CashbackConfig)
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : String(e)
      toast({ title: "Erro ao carregar", description: message, variant: "destructive" })
    } finally {
      setLoading(false)
    }
  }, [toast])

  useEffect(() => {
    load()
  }, [load])

  const set = (patch: Partial<Combo>) => setForm((f) => ({ ...f, ...patch }))

  const startNew = () => {
    setForm({ ...EMPTY, displayOrder: combos.length })
    setServiceSearch("")
    setShowServices(false)
    setShowForm(true)
  }

  const startEdit = (c: Combo) => {
    setForm({ ...c })
    setServiceSearch("")
    setShowServices(false)
    setShowForm(true)
  }

  const cancelForm = () => {
    setShowForm(false)
    setForm(EMPTY)
    setShowServices(false)
  }

  const isSelected = (svc: CatalogServiceRow) => form.items.some((it) => it.serviceId === svc.id)

  const toggleItem = (svc: CatalogServiceRow) => {
    setForm((f) => {
      const exists = f.items.some((it) => it.serviceId === svc.id)
      const items = exists
        ? f.items.filter((it) => it.serviceId !== svc.id)
        : [...f.items, { niche: svc.niche, serviceName: svc.name, serviceId: svc.id }]
      return { ...f, items }
    })
  }

  // fullPrice = soma dos preços do catálogo dos itens selecionados (fonte de verdade da cobrança).
  const computedFull = useMemo(() => {
    return form.items.reduce((sum, it) => {
      const svc = services.find((s) => s.id === it.serviceId)
      return sum + (svc?.estimatedPrice ?? 0)
    }, 0)
  }, [form.items, services])

  const computedPromo = useMemo(
    () => Math.round(computedFull * (1 - form.discountPercent / 100) * 100) / 100,
    [computedFull, form.discountPercent]
  )
  const computedSavings = useMemo(
    () => Math.round((computedFull - computedPromo) * 100) / 100,
    [computedFull, computedPromo]
  )

  // Coerência: o carrinho aplica o desconto pelas categorias dos itens (PromotionManager).
  const cartCombo = useMemo(
    () => expectedCartCombo(form.items.map((it) => it.niche), cashbackCfg),
    [form.items, cashbackCfg]
  )

  const filteredServices = useMemo(() => {
    const q = serviceSearch.trim().toLowerCase()
    const list = q
      ? services.filter((s) => s.name.toLowerCase().includes(q) || s.niche.toLowerCase().includes(q))
      : services
    return [...list].sort((a, b) => a.niche.localeCompare(b.niche, "pt-BR") || a.name.localeCompare(b.name, "pt-BR"))
  }, [services, serviceSearch])

  const handleUpload = async (file: File) => {
    if (!storage) {
      toast({ title: "Storage indisponível", description: "Cole a URL da imagem manualmente.", variant: "destructive" })
      return
    }
    if (file.size > 10 * 1024 * 1024) {
      toast({ title: "Imagem muito grande", description: "Máximo de 10 MB.", variant: "destructive" })
      return
    }
    setUploading(true)
    try {
      const safeName = file.name.replace(/[^a-zA-Z0-9._-]/g, "_")
      const path = `combo_images/${Date.now()}_${safeName}`
      const snap = await uploadBytes(ref(storage, path), file, { contentType: file.type })
      const url = await getDownloadURL(snap.ref)
      set({ imageUrl: url })
      toast({ title: "Imagem enviada!", description: "Pré-visualização atualizada." })
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : String(e)
      toast({ title: "Erro no upload", description: message, variant: "destructive" })
    } finally {
      setUploading(false)
    }
  }

  const save = async () => {
    if (!form.name.trim()) {
      toast({ title: "Nome obrigatório", variant: "destructive" })
      return
    }
    if (form.items.length < 2) {
      toast({ title: "Selecione ao menos 2 serviços", variant: "destructive" })
      return
    }
    setSaving(true)
    try {
      const payload = { ...form, fullPrice: computedFull }
      const res = await fetch("/api/combos", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      })
      const data = await res.json()
      if (!data.success) throw new Error(data.error || "Falha ao salvar")
      toast({ title: "Combo salvo!", description: "Já aparece no app sem precisar de novo APK." })
      cancelForm()
      await load()
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : String(e)
      toast({ title: "Erro ao salvar", description: message, variant: "destructive" })
    } finally {
      setSaving(false)
    }
  }

  const toggleActive = async (c: Combo) => {
    try {
      const res = await fetch("/api/combos", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...c, active: !c.active }),
      })
      const data = await res.json()
      if (!data.success) throw new Error(data.error || "Falha")
      await load()
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : String(e)
      toast({ title: "Erro ao atualizar", description: message, variant: "destructive" })
    }
  }

  const remove = async (c: Combo) => {
    if (!confirm(`Remover o combo "${c.name || c.id}"?`)) return
    try {
      const res = await fetch(`/api/combos?id=${encodeURIComponent(c.id)}`, { method: "DELETE" })
      const data = await res.json()
      if (!data.success) throw new Error(data.error || "Falha")
      toast({ title: "Combo removido" })
      await load()
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : String(e)
      toast({ title: "Erro ao remover", description: message, variant: "destructive" })
    }
  }

  return (
    <div className="space-y-6 p-4 md:p-6 max-w-4xl">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="rounded-lg bg-orange-100 p-2 text-orange-700">
            <Flame className="h-6 w-6" />
          </div>
          <div>
            <h1 className="text-2xl font-bold">Combos Promocionais</h1>
            <p className="text-sm text-muted-foreground">
              Vitrine de combos na Home do app. O desconto cobrado é recalculado no carrinho pelas
              categorias dos serviços — aqui você cura a oferta.
            </p>
          </div>
        </div>
        {!showForm && (
          <Button onClick={startNew} className="gap-2">
            <Plus className="h-4 w-4" /> Novo combo
          </Button>
        )}
      </div>

      {showForm && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center justify-between text-base">
              <span>{form.id ? "Editar combo" : "Novo combo"}</span>
              <Button variant="ghost" size="sm" onClick={cancelForm} className="gap-1">
                <X className="h-4 w-4" /> Cancelar
              </Button>
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {/* Imagem */}
            <div>
              <label className="text-sm font-medium">Imagem do combo (recomendado ~1200×600)</label>
              <div className="mt-2 flex flex-col gap-3 sm:flex-row sm:items-start">
                <div className="relative h-28 w-full overflow-hidden rounded-lg border bg-muted sm:w-56">
                  {form.imageUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={form.imageUrl} alt="Prévia do combo" className="h-full w-full object-cover" />
                  ) : (
                    <div className="flex h-full w-full items-center justify-center text-xs text-muted-foreground">
                      Sem imagem
                    </div>
                  )}
                </div>
                <div className="flex-1 space-y-2">
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/*"
                    className="hidden"
                    onChange={(e) => {
                      const file = e.target.files?.[0]
                      if (file) handleUpload(file)
                      e.target.value = ""
                    }}
                  />
                  <Button
                    type="button"
                    variant="outline"
                    className="gap-2"
                    disabled={uploading}
                    onClick={() => fileInputRef.current?.click()}
                  >
                    {uploading ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
                    {uploading ? "Enviando…" : "Enviar imagem"}
                  </Button>
                  <Input
                    placeholder="ou cole a URL da imagem (https://...)"
                    value={form.imageUrl}
                    onChange={(e) => set({ imageUrl: e.target.value })}
                  />
                </div>
              </div>
            </div>

            {/* Nome / Descrição */}
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div>
                <label className="text-sm font-medium">Nome</label>
                <Input value={form.name} onChange={(e) => set({ name: e.target.value })} placeholder="Combo Casa Nova" />
              </div>
              <div>
                <label className="text-sm font-medium">Descrição</label>
                <Input
                  value={form.description}
                  onChange={(e) => set({ description: e.target.value })}
                  placeholder="Elétrica + hidráulica + montagem"
                />
              </div>
            </div>

            {/* Itens do combo */}
            <div>
              <div className="flex items-center justify-between mb-2">
                <label className="text-sm font-medium">
                  Serviços do combo ({form.items.length} selecionado{form.items.length === 1 ? "" : "s"})
                </label>
                <Button 
                  type="button"
                  variant="outline" 
                  size="sm" 
                  onClick={() => setShowServices(!showServices)}
                >
                  {showServices ? "Fechar seletor" : "Selecionar serviços"}
                </Button>
              </div>

              {form.items.length > 0 && (
                <div className="flex flex-wrap gap-2 mb-3">
                  {form.items.map(it => (
                    <div key={it.serviceId} className="flex items-center gap-1 bg-orange-100 text-orange-800 px-2 py-1 rounded-md text-xs">
                      <span>{it.serviceName}</span>
                      <button 
                        type="button" 
                        onClick={() => setForm(f => ({ ...f, items: f.items.filter(x => x.serviceId !== it.serviceId) }))}
                        className="text-orange-600 hover:text-orange-900"
                      >
                        <X className="h-3 w-3" />
                      </button>
                    </div>
                  ))}
                </div>
              )}

              {showServices && (
                <div className="rounded-lg border p-3 bg-muted/20">
                  <div className="flex items-center justify-between gap-2 mb-2">
                    <Input
                      placeholder="Buscar serviço ou nicho…"
                      value={serviceSearch}
                      onChange={(e) => setServiceSearch(e.target.value)}
                    />
                    <Button type="button" variant="ghost" size="icon" onClick={() => setShowServices(false)}>
                      <X className="h-4 w-4" />
                    </Button>
                  </div>
                  <div className="max-h-56 overflow-y-auto rounded-lg border bg-background">
                    {filteredServices.length === 0 ? (
                      <p className="p-3 text-sm text-muted-foreground">Nenhum serviço encontrado.</p>
                    ) : (
                      filteredServices.map((svc) => {
                        const selected = isSelected(svc)
                        return (
                          <button
                            type="button"
                            key={svc.id}
                            onClick={() => toggleItem(svc)}
                            className={`flex w-full items-center justify-between gap-2 border-b px-3 py-2 text-left text-sm last:border-b-0 hover:bg-muted ${
                              selected ? "bg-orange-50" : ""
                            }`}
                          >
                            <span className="min-w-0">
                              <span className="block truncate font-medium">{svc.name}</span>
                              <span className="block truncate text-xs text-muted-foreground">
                                {svc.niche} · {money(svc.estimatedPrice)}
                              </span>
                            </span>
                            {selected && <Check className="h-4 w-4 shrink-0 text-orange-600" />}
                          </button>
                        )
                      })
                    )}
                  </div>
                </div>
              )}
            </div>

            {/* % e ordem */}
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div>
                <label className="text-sm font-medium">Desconto anunciado (%)</label>
                <Input
                  type="number"
                  min={0}
                  max={100}
                  value={form.discountPercent}
                  onChange={(e) => set({ discountPercent: Number(e.target.value) })}
                />
              </div>
              <div>
                <label className="text-sm font-medium">Ordem de exibição</label>
                <Input
                  type="number"
                  value={form.displayOrder}
                  onChange={(e) => set({ displayOrder: Number(e.target.value) })}
                />
              </div>
            </div>

            {/* Resumo de preço calculado */}
            <div className="rounded-lg border bg-muted/40 p-3 text-sm">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Valor cheio (soma do catálogo)</span>
                <span className="font-medium">{money(computedFull)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Valor promocional ({form.discountPercent}%)</span>
                <span className="font-medium text-orange-600">{money(computedPromo)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Economia exibida</span>
                <span className="font-medium text-emerald-600">{money(computedSavings)}</span>
              </div>
            </div>

            {/* Aviso de coerência com o desconto real do carrinho */}
            {form.items.length >= 2 &&
              (cartCombo ? (
                cartCombo.percent === form.discountPercent ? (
                  <div className="flex items-start gap-2 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">
                    <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" />
                    <span>
                      Coerente: no carrinho esses serviços ativam o <strong>Combo {cartCombo.label}</strong> ={" "}
                      <strong>{cartCombo.percent}%</strong> — igual ao desconto anunciado.
                    </span>
                  </div>
                ) : (
                  <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
                    <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                    <span>
                      Atenção: o carrinho aplicará o <strong>Combo {cartCombo.label}</strong> ={" "}
                      <strong>{cartCombo.percent}%</strong>, mas você anunciou{" "}
                      <strong>{form.discountPercent}%</strong>. Ajuste o % anunciado para{" "}
                      {cartCombo.percent}% para não prometer mais do que será cobrado.
                    </span>
                  </div>
                )
              ) : (
                <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
                  <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                  <span>
                    As categorias escolhidas <strong>não ativam nenhum combo de desconto</strong> no carrinho.
                    O cliente verá o combo, mas o carrinho não aplicará o desconto automático. Combine
                    categorias como Elétrica + Hidráulica, Instalações + Hidráulica ou 2+ Automotivos.
                  </span>
                </div>
              ))}

            <div className="flex items-center justify-between rounded-lg border p-3">
              <span className="text-sm font-medium">Combo ativo</span>
              <Switch checked={form.active} onCheckedChange={(v) => set({ active: v })} />
            </div>

            <div className="flex justify-end">
              <Button onClick={save} disabled={saving || uploading} className="gap-2">
                {saving ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                Salvar combo
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Lista */}
      {loading ? (
        <div className="flex items-center gap-2 text-muted-foreground">
          <RefreshCw className="h-4 w-4 animate-spin" /> Carregando…
        </div>
      ) : combos.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center gap-2 py-10 text-center text-muted-foreground">
            <Flame className="h-8 w-8" />
            <p>Nenhum combo cadastrado. Crie o primeiro para ele aparecer na Home do app.</p>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-3">
          {combos.map((c) => (
            <Card key={c.id} className={c.active ? "" : "opacity-60"}>
              <CardContent className="flex items-center gap-4 p-3">
                <div className="relative h-16 w-28 shrink-0 overflow-hidden rounded-md border bg-muted">
                  {c.imageUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={c.imageUrl} alt={c.name} className="h-full w-full object-cover" />
                  ) : (
                    <div className="flex h-full w-full items-center justify-center text-xs text-muted-foreground">
                      sem foto
                    </div>
                  )}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium">{c.name}</p>
                  <p className="truncate text-sm text-muted-foreground">
                    {c.items.length} serviços · {c.discountPercent}% · {money(c.promoPrice)}
                  </p>
                  <p className="mt-1 truncate text-xs text-muted-foreground">
                    {c.items.map((it) => it.serviceName).join(", ")}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-1">
                  <Switch checked={c.active} onCheckedChange={() => toggleActive(c)} />
                  <Button variant="ghost" size="icon" onClick={() => startEdit(c)}>
                    <Pencil className="h-4 w-4" />
                  </Button>
                  <Button variant="ghost" size="icon" onClick={() => remove(c)}>
                    <Trash2 className="h-4 w-4 text-destructive" />
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
