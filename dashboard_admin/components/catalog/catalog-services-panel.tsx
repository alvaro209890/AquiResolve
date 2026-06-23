"use client"

import { useEffect, useMemo, useState } from "react"
import { collection, onSnapshot, query, where } from "firebase/firestore"
import { db } from "@/lib/firebase"
import { adminFetch } from "@/lib/admin-api"
import { toast } from "sonner"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { Badge } from "@/components/ui/badge"
import { Switch } from "@/components/ui/switch"
import { Slider } from "@/components/ui/slider"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog"
import {
  ArrowUpDown,
  CheckCircle2,
  Clock,
  Loader2,
  Pencil,
  Plus,
  Search,
  ShieldCheck,
  Trash2,
  Wrench,
  XCircle,
} from "lucide-react"

export interface NicheOption {
  name: string
  slug: string
}

interface CatalogServiceDoc {
  id: string
  niche: string
  nicheSlug: string
  name: string
  slug: string
  description: string
  estimatedTime: string
  estimatedPrice: number
  providerCommissionPercent: number
  providerCommission: number
  isConsult: boolean
  active: boolean
  displayOrder: number
}

interface FormState {
  niche: string
  name: string
  description: string
  estimatedTime: string
  estimatedPrice: number
  providerCommissionPercent: number
  isConsult: boolean
  active: boolean
  displayOrder: string
}

const EMPTY_FORM: FormState = {
  niche: "",
  name: "",
  description: "",
  estimatedTime: "",
  estimatedPrice: 150,
  providerCommissionPercent: 50,
  isConsult: false,
  active: true,
  displayOrder: "0",
}

function formatBRL(value: number): string {
  return value.toLocaleString("pt-BR", { style: "currency", currency: "BRL" })
}

function round2(value: number): number {
  return Math.round(value * 100) / 100
}

export function CatalogServicesPanel({ niches }: { niches: NicheOption[] }) {
  const [selectedNiche, setSelectedNiche] = useState<string>("")
  const [services, setServices] = useState<CatalogServiceDoc[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [search, setSearch] = useState("")
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)

  // Seleciona o primeiro nicho assim que a lista chega (se ainda não há seleção).
  useEffect(() => {
    if (!selectedNiche && niches.length > 0) {
      setSelectedNiche(niches[0].name)
      setForm((current) => ({ ...current, niche: niches[0].name }))
    }
  }, [niches, selectedNiche])

  // Lista em tempo real dos serviços do nicho selecionado.
  useEffect(() => {
    if (!db || !selectedNiche) {
      setServices([])
      setLoading(false)
      return
    }
    setLoading(true)
    const q = query(collection(db, "catalog_services"), where("niche", "==", selectedNiche))
    const unsubscribe = onSnapshot(
      q,
      (snapshot) => {
        const next = snapshot.docs
          .map((snapshotDoc) => {
            const data = snapshotDoc.data()
            return {
              id: snapshotDoc.id,
              niche: String(data.niche ?? ""),
              nicheSlug: String(data.nicheSlug ?? ""),
              name: String(data.name ?? data.title ?? data.label ?? "Serviço"),
              slug: String(data.slug ?? ""),
              description: String(data.description ?? ""),
              estimatedTime: String(data.estimatedTime ?? ""),
              estimatedPrice: Number(data.estimatedPrice ?? 0),
              providerCommissionPercent: Number(data.providerCommissionPercent ?? 0),
              providerCommission: Number(data.providerCommission ?? 0),
              isConsult: Boolean(data.isConsult ?? false),
              active: Boolean(data.active ?? data.isActive ?? data.enabled ?? true),
              displayOrder: Number(data.displayOrder ?? data.order ?? data.sortOrder ?? 0),
            } satisfies CatalogServiceDoc
          })
          .sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name, "pt-BR"))
        setServices(next)
        setLoading(false)
      },
      () => setLoading(false)
    )
    return () => unsubscribe()
  }, [selectedNiche])

  const filteredServices = useMemo(() => {
    const needle = search.trim().toLowerCase()
    if (!needle) return services
    return services.filter(
      (service) =>
        service.name.toLowerCase().includes(needle) ||
        service.description.toLowerCase().includes(needle)
    )
  }, [search, services])

  // Prévia ao vivo da divisão do valor.
  const split = useMemo(() => {
    const price = form.isConsult ? 0 : Number(form.estimatedPrice) || 0
    const percent = form.isConsult ? 0 : Number(form.providerCommissionPercent) || 0
    const provider = round2((price * percent) / 100)
    const platform = round2(price - provider)
    return { price, percent, provider, platform }
  }, [form.estimatedPrice, form.providerCommissionPercent, form.isConsult])

  const resetForm = () => {
    setForm({ ...EMPTY_FORM, niche: selectedNiche || niches[0]?.name || "" })
    setEditingId(null)
  }

  const fillForm = (service: CatalogServiceDoc) => {
    setEditingId(service.id)
    setForm({
      niche: service.niche,
      name: service.name,
      description: service.description,
      estimatedTime: service.estimatedTime,
      estimatedPrice: service.estimatedPrice,
      providerCommissionPercent: service.providerCommissionPercent,
      isConsult: service.isConsult,
      active: service.active,
      displayOrder: String(service.displayOrder),
    })
  }

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!form.niche.trim()) {
      toast.error("Selecione o nicho do serviço")
      return
    }
    if (!form.name.trim()) {
      toast.error("Informe o nome do serviço")
      return
    }

    const nicheSlug = niches.find((n) => n.name === form.niche)?.slug
    const displayOrder = Number(form.displayOrder || 0)

    setSaving(true)
    try {
      const res = await adminFetch("/api/catalog/services", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          id: editingId || undefined,
          niche: form.niche.trim(),
          nicheSlug,
          name: form.name.trim(),
          description: form.description.trim(),
          estimatedTime: form.estimatedTime.trim(),
          estimatedPrice: form.estimatedPrice,
          providerCommissionPercent: form.providerCommissionPercent,
          isConsult: form.isConsult,
          active: form.active,
          displayOrder: Number.isFinite(displayOrder) ? displayOrder : 0,
        }),
      })
      const data = await res.json().catch(() => ({}))
      if (!res.ok || !data.success) {
        toast.error(`Erro ao salvar serviço: ${data.error ?? res.statusText}`)
        return
      }
      toast.success(editingId ? "Serviço atualizado" : "Serviço cadastrado")
      // Se o serviço foi criado em outro nicho, foca nesse nicho para vê-lo.
      if (form.niche !== selectedNiche) setSelectedNiche(form.niche)
      resetForm()
    } finally {
      setSaving(false)
    }
  }

  const handleToggleActive = async (service: CatalogServiceDoc) => {
    const res = await adminFetch("/api/catalog/services", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        id: service.id,
        niche: service.niche,
        nicheSlug: service.nicheSlug,
        name: service.name,
        description: service.description,
        estimatedTime: service.estimatedTime,
        estimatedPrice: service.estimatedPrice,
        providerCommissionPercent: service.providerCommissionPercent,
        isConsult: service.isConsult,
        active: !service.active,
        displayOrder: service.displayOrder,
      }),
    })
    const data = await res.json().catch(() => ({}))
    if (!res.ok || !data.success) {
      toast.error(`Erro ao atualizar serviço: ${data.error ?? res.statusText}`)
      return
    }
    toast.success(service.active ? "Serviço desativado" : "Serviço ativado")
  }

  const handleDelete = async (service: CatalogServiceDoc) => {
    const res = await adminFetch(`/api/catalog/services?id=${encodeURIComponent(service.id)}`, {
      method: "DELETE",
    })
    const data = await res.json().catch(() => ({}))
    if (!res.ok || !data.success) {
      toast.error(`Erro ao remover serviço: ${data.error ?? res.statusText}`)
      return
    }
    toast.success("Serviço removido")
    if (editingId === service.id) resetForm()
  }

  const activeCount = services.filter((s) => s.active).length

  return (
    <div className="grid gap-6 2xl:grid-cols-[460px_minmax(0,1fr)]">
      {/* Formulário */}
      <Card className="border-border/70 shadow-sm">
        <CardHeader className="border-b">
          <CardTitle className="flex items-center gap-2 text-lg">
            {editingId ? <Pencil className="h-5 w-5" /> : <Plus className="h-5 w-5" />}
            {editingId ? "Editar serviço" : "Novo serviço"}
          </CardTitle>
          <CardDescription>
            Defina nicho, valor ao cliente e o percentual do prestador. O app passa a exibir e cobrar por esses valores.
          </CardDescription>
        </CardHeader>
        <CardContent className="pt-4">
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label>Nicho</Label>
              <Select
                value={form.niche}
                onValueChange={(value) => setForm((current) => ({ ...current, niche: value }))}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Selecione o nicho" />
                </SelectTrigger>
                <SelectContent>
                  {niches.map((niche) => (
                    <SelectItem key={niche.slug || niche.name} value={niche.name}>
                      {niche.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="svc-name">Nome do serviço</Label>
              <Input
                id="svc-name"
                value={form.name}
                onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
                placeholder="Ex.: Instalação de tomada"
                required
              />
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="svc-time">Tempo estimado</Label>
                <Input
                  id="svc-time"
                  value={form.estimatedTime}
                  onChange={(event) => setForm((current) => ({ ...current, estimatedTime: event.target.value }))}
                  placeholder="Ex.: 1-2h"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="svc-order">Ordem de exibição</Label>
                <Input
                  id="svc-order"
                  type="number"
                  value={form.displayOrder}
                  onChange={(event) => setForm((current) => ({ ...current, displayOrder: event.target.value }))}
                />
              </div>
            </div>

            {/* Preço sob consulta */}
            <div className="flex items-center justify-between rounded-xl border bg-muted/20 px-4 py-3">
              <div>
                <p className="text-sm font-medium text-foreground">Preço sob consulta</p>
                <p className="text-xs text-muted-foreground">Sem valor fixo — o app mostra &quot;A consultar&quot;.</p>
              </div>
              <Switch
                checked={form.isConsult}
                onCheckedChange={(checked) => setForm((current) => ({ ...current, isConsult: checked }))}
              />
            </div>

            {!form.isConsult && (
              <>
                {/* Valor do serviço */}
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <Label htmlFor="svc-price">Valor do serviço (cliente paga)</Label>
                    <span className="text-sm font-semibold text-foreground">{formatBRL(split.price)}</span>
                  </div>
                  <Input
                    id="svc-price"
                    type="number"
                    min={0}
                    step={5}
                    value={form.estimatedPrice}
                    onChange={(event) =>
                      setForm((current) => ({ ...current, estimatedPrice: Math.max(0, Number(event.target.value) || 0) }))
                    }
                  />
                  <Slider
                    min={0}
                    max={2000}
                    step={5}
                    value={[Math.min(2000, form.estimatedPrice)]}
                    onValueChange={([value]) => setForm((current) => ({ ...current, estimatedPrice: value }))}
                  />
                </div>

                {/* % do prestador */}
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <Label>% do prestador</Label>
                    <span className="text-sm font-semibold text-foreground">{split.percent}%</span>
                  </div>
                  <Slider
                    min={0}
                    max={100}
                    step={1}
                    value={[form.providerCommissionPercent]}
                    onValueChange={([value]) =>
                      setForm((current) => ({ ...current, providerCommissionPercent: value }))
                    }
                  />
                </div>

                {/* Prévia da divisão */}
                <div className="grid grid-cols-3 gap-2">
                  <div className="rounded-lg border bg-blue-50/70 p-3 text-center">
                    <p className="text-[11px] font-medium uppercase tracking-wide text-blue-700">Cliente paga</p>
                    <p className="mt-1 text-sm font-bold text-blue-700">{formatBRL(split.price)}</p>
                  </div>
                  <div className="rounded-lg border bg-emerald-50/70 p-3 text-center">
                    <p className="text-[11px] font-medium uppercase tracking-wide text-emerald-700">Prestador recebe</p>
                    <p className="mt-1 text-sm font-bold text-emerald-700">{formatBRL(split.provider)}</p>
                  </div>
                  <div className="rounded-lg border bg-orange-50/70 p-3 text-center">
                    <p className="text-[11px] font-medium uppercase tracking-wide text-orange-700">Plataforma fica</p>
                    <p className="mt-1 text-sm font-bold text-orange-700">{formatBRL(split.platform)}</p>
                  </div>
                </div>
              </>
            )}

            <div className="space-y-2">
              <Label htmlFor="svc-desc">Descrição</Label>
              <Textarea
                id="svc-desc"
                rows={3}
                value={form.description}
                onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
                placeholder="Detalhes que ajudam o cliente a escolher o serviço."
              />
            </div>

            <div className="flex items-center justify-between rounded-xl border bg-muted/20 px-4 py-3">
              <div>
                <p className="text-sm font-medium text-foreground">Disponível no aplicativo</p>
                <p className="text-xs text-muted-foreground">Desative para ocultar do app sem apagar.</p>
              </div>
              <Switch
                checked={form.active}
                onCheckedChange={(checked) => setForm((current) => ({ ...current, active: checked }))}
              />
            </div>

            <div className="flex flex-wrap gap-2">
              <Button type="submit" disabled={saving} className="gap-2">
                {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <ShieldCheck className="h-4 w-4" />}
                {editingId ? "Salvar alterações" : "Cadastrar serviço"}
              </Button>
              <Button type="button" variant="outline" onClick={resetForm} disabled={saving}>
                Limpar
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {/* Lista */}
      <Card className="border-border/70 shadow-sm">
        <CardHeader className="border-b">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
            <div className="space-y-3">
              <div>
                <CardTitle className="text-lg">Serviços do nicho</CardTitle>
                <CardDescription>Escolha o nicho para gerenciar seus serviços e preços.</CardDescription>
              </div>
              <div className="flex flex-wrap items-center gap-3">
                <div className="w-full sm:w-64">
                  <Select value={selectedNiche} onValueChange={setSelectedNiche}>
                    <SelectTrigger>
                      <SelectValue placeholder="Selecione o nicho" />
                    </SelectTrigger>
                    <SelectContent>
                      {niches.map((niche) => (
                        <SelectItem key={niche.slug || niche.name} value={niche.name}>
                          {niche.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <Badge variant="outline" className="bg-white/70">{services.length} serviços</Badge>
                <Badge variant="outline" className="bg-white/70">{activeCount} ativos</Badge>
              </div>
            </div>
            <div className="relative w-full lg:w-72">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Buscar serviço"
                className="pl-10"
              />
            </div>
          </div>
        </CardHeader>
        <CardContent className="pt-4">
          {loading ? (
            <div className="flex h-52 items-center justify-center text-muted-foreground">
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              Carregando serviços...
            </div>
          ) : filteredServices.length === 0 ? (
            <div className="flex h-52 flex-col items-center justify-center text-center">
              <Wrench className="mb-3 h-10 w-10 text-muted-foreground/35" />
              <p className="text-sm font-medium text-foreground">Nenhum serviço neste nicho</p>
              <p className="mt-1 text-sm text-muted-foreground">Cadastre um serviço à esquerda.</p>
            </div>
          ) : (
            <div className="space-y-3">
              {filteredServices.map((service) => (
                <div key={service.id} className="rounded-2xl border bg-card p-4 shadow-sm transition-shadow hover:shadow-md">
                  <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                    <div className="min-w-0 space-y-3">
                      <div className="flex flex-wrap items-center gap-2">
                        <h3 className="text-base font-semibold text-foreground">{service.name}</h3>
                        <Badge variant={service.active ? "default" : "outline"}>
                          {service.active ? (
                            <><CheckCircle2 className="mr-1 h-3.5 w-3.5" />Ativo</>
                          ) : (
                            <><XCircle className="mr-1 h-3.5 w-3.5" />Inativo</>
                          )}
                        </Badge>
                      </div>

                      <div className="flex flex-wrap gap-2 text-xs">
                        {service.isConsult ? (
                          <span className="inline-flex items-center gap-1 rounded-full border bg-muted/40 px-2 py-1 font-medium">
                            A consultar
                          </span>
                        ) : (
                          <>
                            <span className="inline-flex items-center gap-1 rounded-full border bg-blue-50 px-2 py-1 font-medium text-blue-700">
                              Cliente {formatBRL(service.estimatedPrice)}
                            </span>
                            <span className="inline-flex items-center gap-1 rounded-full border bg-emerald-50 px-2 py-1 font-medium text-emerald-700">
                              Prestador {formatBRL(service.providerCommission)} ({service.providerCommissionPercent}%)
                            </span>
                          </>
                        )}
                        {service.estimatedTime ? (
                          <span className="inline-flex items-center gap-1 rounded-full border px-2 py-1 text-muted-foreground">
                            <Clock className="h-3 w-3" />
                            {service.estimatedTime}
                          </span>
                        ) : null}
                        <span className="inline-flex items-center gap-1 rounded-full border px-2 py-1 text-muted-foreground">
                          <ArrowUpDown className="h-3 w-3" />
                          Ordem {service.displayOrder}
                        </span>
                      </div>

                      {service.description ? (
                        <p className="text-sm leading-6 text-muted-foreground">{service.description}</p>
                      ) : null}
                    </div>

                    <div className="flex flex-wrap gap-2 lg:w-[260px] lg:justify-end">
                      <Button type="button" variant="outline" onClick={() => fillForm(service)} className="gap-2">
                        <Pencil className="h-4 w-4" />
                        Editar
                      </Button>
                      <Button
                        type="button"
                        variant={service.active ? "secondary" : "default"}
                        onClick={() => handleToggleActive(service)}
                      >
                        {service.active ? "Desativar" : "Ativar"}
                      </Button>
                      <AlertDialog>
                        <AlertDialogTrigger asChild>
                          <Button type="button" variant="outline" className="gap-2 text-red-600 hover:text-red-700">
                            <Trash2 className="h-4 w-4" />
                            Excluir
                          </Button>
                        </AlertDialogTrigger>
                        <AlertDialogContent>
                          <AlertDialogHeader>
                            <AlertDialogTitle>Remover &quot;{service.name}&quot;?</AlertDialogTitle>
                            <AlertDialogDescription>
                              Esta ação não pode ser desfeita. O serviço sairá do catálogo do aplicativo.
                            </AlertDialogDescription>
                          </AlertDialogHeader>
                          <AlertDialogFooter>
                            <AlertDialogCancel>Cancelar</AlertDialogCancel>
                            <AlertDialogAction
                              className="bg-red-600 hover:bg-red-700"
                              onClick={() => handleDelete(service)}
                            >
                              Remover
                            </AlertDialogAction>
                          </AlertDialogFooter>
                        </AlertDialogContent>
                      </AlertDialog>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
