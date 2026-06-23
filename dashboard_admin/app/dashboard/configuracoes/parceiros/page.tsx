"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Switch } from "@/components/ui/switch"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { useToast } from "@/hooks/use-toast"
import { adminFetch } from "@/lib/admin-api"
import { storage } from "@/lib/firebase"
import { getDownloadURL, ref, uploadBytes } from "firebase/storage"
import {
  Handshake,
  Plus,
  Save,
  Trash2,
  RefreshCw,
  Upload,
  Pencil,
  X,
  GripVertical,
} from "lucide-react"

interface Partner {
  id: string
  name: string
  logoUrl: string
  bannerUrl: string
  description: string
  benefitType: string
  benefitLabel: string
  couponCode: string
  url: string
  active: boolean
  displayOrder: number
}

const EMPTY: Partner = {
  id: "",
  name: "",
  logoUrl: "",
  bannerUrl: "",
  description: "",
  benefitType: "discount",
  benefitLabel: "",
  couponCode: "",
  url: "",
  active: true,
  displayOrder: 0,
}

// Tipos de benefício (precisam casar com o app: PartnerRepository/Partner).
const BENEFIT_TYPES: { value: string; label: string; hint: string }[] = [
  { value: "discount", label: "Desconto", hint: "Ex.: 10% OFF na primeira compra" },
  { value: "cashback", label: "Cashback", hint: "Ex.: 5% de volta em compras" },
  { value: "coupon", label: "Cupom", hint: "Mostra o código copiável no app" },
  { value: "link", label: "Apenas link", hint: "Leva o cliente ao site do parceiro" },
]

export default function ParceirosConfigPage() {
  const { toast } = useToast()
  const [partners, setPartners] = useState<Partner[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [uploadingLogo, setUploadingLogo] = useState(false)
  const [uploadingBanner, setUploadingBanner] = useState(false)
  const [form, setForm] = useState<Partner>(EMPTY)
  const [showForm, setShowForm] = useState(false)
  const logoInputRef = useRef<HTMLInputElement>(null)
  const bannerInputRef = useRef<HTMLInputElement>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const res = await adminFetch("/api/partners")
      const data = await res.json()
      if (data.success) setPartners(data.partners as Partner[])
      else throw new Error(data.error || "Falha ao carregar")
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : String(e)
      toast({ title: "Erro ao carregar parceiros", description: message, variant: "destructive" })
    } finally {
      setLoading(false)
    }
  }, [toast])

  useEffect(() => {
    load()
  }, [load])

  const set = (patch: Partial<Partner>) => setForm((f) => ({ ...f, ...patch }))

  const startNew = () => {
    setForm({ ...EMPTY, displayOrder: partners.length })
    setShowForm(true)
  }

  const startEdit = (p: Partner) => {
    setForm({ ...p })
    setShowForm(true)
  }

  const cancelForm = () => {
    setShowForm(false)
    setForm(EMPTY)
  }

  const handleUpload = async (file: File, target: "logo" | "banner") => {
    if (!storage) {
      toast({ title: "Storage indisponível", description: "Cole a URL da imagem manualmente.", variant: "destructive" })
      return
    }
    if (file.size > 10 * 1024 * 1024) {
      toast({ title: "Imagem muito grande", description: "Máximo de 10 MB.", variant: "destructive" })
      return
    }
    const setUploading = target === "logo" ? setUploadingLogo : setUploadingBanner
    setUploading(true)
    try {
      const safeName = file.name.replace(/[^a-zA-Z0-9._-]/g, "_")
      const path = `partner_images/${Date.now()}_${target}_${safeName}`
      const snap = await uploadBytes(ref(storage, path), file, { contentType: file.type })
      const url = await getDownloadURL(snap.ref)
      set(target === "logo" ? { logoUrl: url } : { bannerUrl: url })
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
      toast({ title: "Nome obrigatório", description: "Informe o nome do parceiro.", variant: "destructive" })
      return
    }
    if (!form.logoUrl.trim()) {
      toast({ title: "Logo obrigatório", description: "Faça upload ou cole a URL do logo.", variant: "destructive" })
      return
    }
    if (form.benefitType === "coupon" && !form.couponCode.trim()) {
      toast({
        title: "Cupom obrigatório",
        description: "Para benefício do tipo Cupom, informe o código.",
        variant: "destructive",
      })
      return
    }
    setSaving(true)
    try {
      const res = await adminFetch("/api/partners", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(form),
      })
      const data = await res.json()
      if (!data.success) throw new Error(data.error || "Falha ao salvar")
      toast({ title: "Parceiro salvo!", description: "Já aparece no app sem precisar de novo APK." })
      cancelForm()
      await load()
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : String(e)
      toast({ title: "Erro ao salvar", description: message, variant: "destructive" })
    } finally {
      setSaving(false)
    }
  }

  const toggleActive = async (p: Partner) => {
    try {
      const res = await adminFetch("/api/partners", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...p, active: !p.active }),
      })
      const data = await res.json()
      if (!data.success) throw new Error(data.error || "Falha")
      await load()
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : String(e)
      toast({ title: "Erro ao atualizar", description: message, variant: "destructive" })
    }
  }

  const remove = async (p: Partner) => {
    if (!confirm(`Remover o parceiro "${p.name || p.id}"?`)) return
    try {
      const res = await adminFetch(`/api/partners?id=${encodeURIComponent(p.id)}`, { method: "DELETE" })
      const data = await res.json()
      if (!data.success) throw new Error(data.error || "Falha")
      toast({ title: "Parceiro removido" })
      await load()
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : String(e)
      toast({ title: "Erro ao remover", description: message, variant: "destructive" })
    }
  }

  const benefitHint = BENEFIT_TYPES.find((b) => b.value === form.benefitType)?.hint ?? ""

  return (
    <div className="space-y-6 p-4 md:p-6 max-w-4xl">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="rounded-lg bg-emerald-100 p-2 text-emerald-700">
            <Handshake className="h-6 w-6" />
          </div>
          <div>
            <h1 className="text-2xl font-bold">Parceiros AquiResolve</h1>
            <p className="text-sm text-muted-foreground">
              Patrocinadores com desconto/cashback/cupom na Home do app. Editar aqui reflete no app sem novo APK.
            </p>
          </div>
        </div>
        {!showForm && (
          <Button onClick={startNew} className="gap-2">
            <Plus className="h-4 w-4" /> Novo parceiro
          </Button>
        )}
      </div>

      {showForm && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center justify-between text-base">
              <span>{form.id ? "Editar parceiro" : "Novo parceiro"}</span>
              <Button variant="ghost" size="sm" onClick={cancelForm} className="gap-1">
                <X className="h-4 w-4" /> Cancelar
              </Button>
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {/* Nome */}
            <div>
              <label className="text-sm font-medium">Nome do parceiro</label>
              <Input value={form.name} onChange={(e) => set({ name: e.target.value })} placeholder="Leroy Merlin" />
            </div>

            {/* Logo */}
            <div>
              <label className="text-sm font-medium">Logo (fundo branco, ~300×300)</label>
              <div className="mt-2 flex flex-col gap-3 sm:flex-row sm:items-start">
                <div className="relative flex h-24 w-24 shrink-0 items-center justify-center overflow-hidden rounded-lg border bg-white">
                  {form.logoUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={form.logoUrl} alt="Prévia do logo" className="h-full w-full object-contain p-2" />
                  ) : (
                    <span className="text-xs text-muted-foreground">Sem logo</span>
                  )}
                </div>
                <div className="flex-1 space-y-2">
                  <input
                    ref={logoInputRef}
                    type="file"
                    accept="image/*"
                    className="hidden"
                    onChange={(e) => {
                      const file = e.target.files?.[0]
                      if (file) handleUpload(file, "logo")
                      e.target.value = ""
                    }}
                  />
                  <Button
                    type="button"
                    variant="outline"
                    className="gap-2"
                    disabled={uploadingLogo}
                    onClick={() => logoInputRef.current?.click()}
                  >
                    {uploadingLogo ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
                    {uploadingLogo ? "Enviando…" : "Enviar logo"}
                  </Button>
                  <Input
                    placeholder="ou cole a URL do logo (https://...)"
                    value={form.logoUrl}
                    onChange={(e) => set({ logoUrl: e.target.value })}
                  />
                </div>
              </div>
            </div>

            {/* Banner opcional */}
            <div>
              <label className="text-sm font-medium">Banner do detalhe (opcional, ~1200×500)</label>
              <div className="mt-2 flex flex-col gap-3 sm:flex-row sm:items-start">
                <div className="relative h-24 w-full overflow-hidden rounded-lg border bg-muted sm:w-44">
                  {form.bannerUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={form.bannerUrl} alt="Prévia do banner" className="h-full w-full object-cover" />
                  ) : (
                    <div className="flex h-full w-full items-center justify-center text-xs text-muted-foreground">
                      Sem banner
                    </div>
                  )}
                </div>
                <div className="flex-1 space-y-2">
                  <input
                    ref={bannerInputRef}
                    type="file"
                    accept="image/*"
                    className="hidden"
                    onChange={(e) => {
                      const file = e.target.files?.[0]
                      if (file) handleUpload(file, "banner")
                      e.target.value = ""
                    }}
                  />
                  <Button
                    type="button"
                    variant="outline"
                    className="gap-2"
                    disabled={uploadingBanner}
                    onClick={() => bannerInputRef.current?.click()}
                  >
                    {uploadingBanner ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
                    {uploadingBanner ? "Enviando…" : "Enviar banner"}
                  </Button>
                  <Input
                    placeholder="ou cole a URL do banner (https://...)"
                    value={form.bannerUrl}
                    onChange={(e) => set({ bannerUrl: e.target.value })}
                  />
                </div>
              </div>
            </div>

            {/* Descrição */}
            <div>
              <label className="text-sm font-medium">Descrição</label>
              <Input
                value={form.description}
                onChange={(e) => set({ description: e.target.value })}
                placeholder="Materiais de construção e reforma com desconto para clientes AquiResolve."
              />
            </div>

            {/* Tipo de benefício / rótulo */}
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div>
                <label className="text-sm font-medium">Tipo de benefício</label>
                <Select value={form.benefitType} onValueChange={(v) => set({ benefitType: v })}>
                  <SelectTrigger className="mt-1">
                    <SelectValue placeholder="Selecione o tipo" />
                  </SelectTrigger>
                  <SelectContent>
                    {BENEFIT_TYPES.map((b) => (
                      <SelectItem key={b.value} value={b.value}>
                        {b.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div>
                <label className="text-sm font-medium">Rótulo do benefício</label>
                <Input
                  value={form.benefitLabel}
                  onChange={(e) => set({ benefitLabel: e.target.value })}
                  placeholder={benefitHint}
                />
                {benefitHint && <p className="mt-1 text-xs text-muted-foreground">{benefitHint}</p>}
              </div>
            </div>

            {/* Cupom (condicional) / URL */}
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              {form.benefitType === "coupon" && (
                <div>
                  <label className="text-sm font-medium">Código do cupom</label>
                  <Input
                    value={form.couponCode}
                    onChange={(e) => set({ couponCode: e.target.value.toUpperCase() })}
                    placeholder="AQUI10"
                  />
                </div>
              )}
              <div>
                <label className="text-sm font-medium">Site do parceiro (opcional)</label>
                <Input
                  value={form.url}
                  onChange={(e) => set({ url: e.target.value })}
                  placeholder="https://www.leroymerlin.com.br"
                />
              </div>
            </div>

            {/* Ordem */}
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div>
                <label className="text-sm font-medium">Ordem de exibição</label>
                <Input
                  type="number"
                  value={form.displayOrder}
                  onChange={(e) => set({ displayOrder: Number(e.target.value) })}
                />
              </div>
            </div>

            <div className="flex items-center justify-between rounded-lg border p-3">
              <span className="text-sm font-medium">Parceiro ativo</span>
              <Switch checked={form.active} onCheckedChange={(v) => set({ active: v })} />
            </div>

            <div className="flex justify-end">
              <Button onClick={save} disabled={saving || uploadingLogo || uploadingBanner} className="gap-2">
                {saving ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                Salvar parceiro
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
      ) : partners.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center gap-2 py-10 text-center text-muted-foreground">
            <Handshake className="h-8 w-8" />
            <p>Nenhum parceiro cadastrado. Crie o primeiro para ele aparecer no app.</p>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-3">
          {partners.map((p) => (
            <Card key={p.id} className={p.active ? "" : "opacity-60"}>
              <CardContent className="flex items-center gap-4 p-3">
                <GripVertical className="hidden h-5 w-5 shrink-0 text-muted-foreground sm:block" />
                <div className="flex h-14 w-14 shrink-0 items-center justify-center overflow-hidden rounded-md border bg-white">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={p.logoUrl} alt={p.name || "parceiro"} className="h-full w-full object-contain p-1" />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium">{p.name}</p>
                  <p className="truncate text-sm text-muted-foreground">
                    {p.benefitLabel || <span className="italic">(sem benefício)</span>}
                  </p>
                  <p className="mt-1 text-xs text-muted-foreground">
                    Ordem {p.displayOrder} ·{" "}
                    {BENEFIT_TYPES.find((b) => b.value === p.benefitType)?.label ?? p.benefitType}
                    {p.benefitType === "coupon" && p.couponCode ? ` → ${p.couponCode}` : ""}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-1">
                  <Switch checked={p.active} onCheckedChange={() => toggleActive(p)} />
                  <Button variant="ghost" size="icon" onClick={() => startEdit(p)}>
                    <Pencil className="h-4 w-4" />
                  </Button>
                  <Button variant="ghost" size="icon" onClick={() => remove(p)}>
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
