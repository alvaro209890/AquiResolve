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
import { storage } from "@/lib/firebase"
import { getDownloadURL, ref, uploadBytes } from "firebase/storage"
import { adminFetch } from "@/lib/admin-api"
import { usePermissions } from "@/hooks/use-permissions"
import {
  ImageIcon,
  Plus,
  Save,
  Trash2,
  RefreshCw,
  Upload,
  Pencil,
  X,
  GripVertical,
} from "lucide-react"

interface Banner {
  id: string
  title: string
  subtitle: string
  imageUrl: string
  actionType: string
  actionValue: string
  backgroundColor: string
  active: boolean
  displayOrder: number
}

const EMPTY: Banner = {
  id: "",
  title: "",
  subtitle: "",
  imageUrl: "",
  actionType: "none",
  actionValue: "",
  backgroundColor: "#FF7A00",
  active: true,
  displayOrder: 0,
}

// Rótulos amigáveis dos tipos de ação (precisam casar com o app: BannerRepository/HomeBanner).
const ACTION_TYPES: { value: string; label: string; hint: string }[] = [
  { value: "none", label: "Sem ação (apenas visual)", hint: "" },
  { value: "niche", label: "Abrir nicho (criar pedido)", hint: "Ex.: Elétrica" },
  { value: "service", label: "Buscar serviço", hint: "Texto da busca, ex.: Limpeza de sofá" },
  { value: "cashback", label: "Tela de Cashback", hint: "" },
  { value: "url", label: "Abrir link externo", hint: "https://..." },
  { value: "combos", label: "Ir para Combos na Home", hint: "" },
  { value: "partners", label: "Ir para Parceiros na Home", hint: "" },
]

function needsActionValue(actionType: string): boolean {
  return actionType === "niche" || actionType === "service" || actionType === "url"
}

export default function BannersConfigPage() {
  const { toast } = useToast()
  const { hasPermission } = usePermissions()
  const canCreate = hasPermission("criarBanners")
  const canEdit = hasPermission("editarBanners")
  const canPublish = hasPermission("publicarBanners")
  const canDelete = hasPermission("excluirBanners")
  const [banners, setBanners] = useState<Banner[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [form, setForm] = useState<Banner>(EMPTY)
  const [showForm, setShowForm] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const res = await adminFetch("/api/banners")
      const data = await res.json()
      if (data.success) setBanners(data.banners as Banner[])
      else throw new Error(data.error || "Falha ao carregar")
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : String(e)
      toast({ title: "Erro ao carregar banners", description: message, variant: "destructive" })
    } finally {
      setLoading(false)
    }
  }, [toast])

  useEffect(() => {
    load()
  }, [load])

  const set = (patch: Partial<Banner>) => setForm((f) => ({ ...f, ...patch }))

  const startNew = () => {
    setForm({ ...EMPTY, active: canPublish, displayOrder: banners.length })
    setShowForm(true)
  }

  const startEdit = (b: Banner) => {
    setForm({ ...b })
    setShowForm(true)
  }

  const cancelForm = () => {
    setShowForm(false)
    setForm(EMPTY)
  }

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
      const path = `banner_images/${Date.now()}_${safeName}`
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
    if (!form.imageUrl.trim()) {
      toast({ title: "Imagem obrigatória", description: "Faça upload ou cole a URL da imagem.", variant: "destructive" })
      return
    }
    if (needsActionValue(form.actionType) && !form.actionValue.trim()) {
      toast({
        title: "Destino obrigatório",
        description: "Preencha o valor da ação (nicho, busca ou URL).",
        variant: "destructive",
      })
      return
    }
    setSaving(true)
    try {
      const res = await adminFetch("/api/banners", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(form),
      })
      const data = await res.json()
      if (!data.success) throw new Error(data.error || "Falha ao salvar")
      toast({ title: "Banner salvo!", description: "Já aparece no app sem precisar de novo APK." })
      cancelForm()
      await load()
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : String(e)
      toast({ title: "Erro ao salvar", description: message, variant: "destructive" })
    } finally {
      setSaving(false)
    }
  }

  const toggleActive = async (b: Banner) => {
    try {
      const res = await adminFetch("/api/banners", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...b, active: !b.active }),
      })
      const data = await res.json()
      if (!data.success) throw new Error(data.error || "Falha")
      await load()
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : String(e)
      toast({ title: "Erro ao atualizar", description: message, variant: "destructive" })
    }
  }

  const remove = async (b: Banner) => {
    if (!confirm(`Remover o banner "${b.title || b.id}"?`)) return
    try {
      const res = await adminFetch(`/api/banners?id=${encodeURIComponent(b.id)}`, { method: "DELETE" })
      const data = await res.json()
      if (!data.success) throw new Error(data.error || "Falha")
      toast({ title: "Banner removido" })
      await load()
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : String(e)
      toast({ title: "Erro ao remover", description: message, variant: "destructive" })
    }
  }

  const activeHint = ACTION_TYPES.find((a) => a.value === form.actionType)?.hint ?? ""

  return (
    <div className="space-y-6 p-4 md:p-6 max-w-4xl">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="rounded-lg bg-orange-100 p-2 text-orange-700">
            <ImageIcon className="h-6 w-6" />
          </div>
          <div>
            <h1 className="text-2xl font-bold">Banners da Home</h1>
            <p className="text-sm text-muted-foreground">
              Carrossel rotativo no topo do app. Editar aqui reflete no app sem novo APK.
            </p>
          </div>
        </div>
        {!showForm && canCreate && (
          <Button onClick={startNew} className="gap-2">
            <Plus className="h-4 w-4" /> Novo banner
          </Button>
        )}
      </div>

      {showForm && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center justify-between text-base">
              <span>{form.id ? "Editar banner" : "Novo banner"}</span>
              <Button variant="ghost" size="sm" onClick={cancelForm} className="gap-1">
                <X className="h-4 w-4" /> Cancelar
              </Button>
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {/* Imagem */}
            <div>
              <label className="text-sm font-medium">Imagem do banner (recomendado ~1200×500)</label>
              <div className="mt-2 flex flex-col gap-3 sm:flex-row sm:items-start">
                <div className="relative h-28 w-full overflow-hidden rounded-lg border bg-muted sm:w-56">
                  {form.imageUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={form.imageUrl} alt="Prévia do banner" className="h-full w-full object-cover" />
                  ) : (
                    <div
                      className="flex h-full w-full items-center justify-center text-xs text-muted-foreground"
                      style={{ backgroundColor: form.backgroundColor || undefined }}
                    >
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

            {/* Título / Subtítulo */}
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div>
                <label className="text-sm font-medium">Título (opcional)</label>
                <Input value={form.title} onChange={(e) => set({ title: e.target.value })} placeholder="Ganhe cashback" />
              </div>
              <div>
                <label className="text-sm font-medium">Subtítulo (opcional)</label>
                <Input
                  value={form.subtitle}
                  onChange={(e) => set({ subtitle: e.target.value })}
                  placeholder="Até 8% de volta"
                />
              </div>
            </div>

            {/* Ação */}
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div>
                <label className="text-sm font-medium">Ao tocar no banner</label>
                <Select value={form.actionType} onValueChange={(v) => set({ actionType: v })}>
                  <SelectTrigger className="mt-1">
                    <SelectValue placeholder="Selecione a ação" />
                  </SelectTrigger>
                  <SelectContent>
                    {ACTION_TYPES.map((a) => (
                      <SelectItem key={a.value} value={a.value}>
                        {a.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              {needsActionValue(form.actionType) && (
                <div>
                  <label className="text-sm font-medium">Destino</label>
                  <Input
                    value={form.actionValue}
                    onChange={(e) => set({ actionValue: e.target.value })}
                    placeholder={activeHint}
                  />
                  {activeHint && <p className="mt-1 text-xs text-muted-foreground">{activeHint}</p>}
                </div>
              )}
            </div>

            {/* Cor de fundo / Ordem */}
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              <div>
                <label className="text-sm font-medium">Cor de fundo (placeholder)</label>
                <div className="mt-1 flex items-center gap-2">
                  <input
                    type="color"
                    value={/^#[0-9a-fA-F]{6}$/.test(form.backgroundColor) ? form.backgroundColor : "#FF7A00"}
                    onChange={(e) => set({ backgroundColor: e.target.value })}
                    className="h-9 w-12 cursor-pointer rounded border"
                  />
                  <Input
                    value={form.backgroundColor}
                    onChange={(e) => set({ backgroundColor: e.target.value })}
                    placeholder="#FF7A00"
                  />
                </div>
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

            <div className="flex items-center justify-between rounded-lg border p-3">
              <span className="text-sm font-medium">Banner ativo</span>
              <Switch
                checked={form.active}
                disabled={!canPublish}
                onCheckedChange={(v) => set({ active: v })}
              />
            </div>

            <div className="flex justify-end">
              <Button onClick={save} disabled={saving || uploading} className="gap-2">
                {saving ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                Salvar banner
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
      ) : banners.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center gap-2 py-10 text-center text-muted-foreground">
            <ImageIcon className="h-8 w-8" />
            <p>Nenhum banner cadastrado. Crie o primeiro para ele aparecer no app.</p>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-3">
          {banners.map((b) => (
            <Card key={b.id} className={b.active ? "" : "opacity-60"}>
              <CardContent className="flex items-center gap-4 p-3">
                <GripVertical className="hidden h-5 w-5 shrink-0 text-muted-foreground sm:block" />
                <div className="relative h-16 w-28 shrink-0 overflow-hidden rounded-md border bg-muted">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={b.imageUrl} alt={b.title || "banner"} className="h-full w-full object-cover" />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium">{b.title || <span className="text-muted-foreground">(sem título)</span>}</p>
                  <p className="truncate text-sm text-muted-foreground">{b.subtitle}</p>
                  <p className="mt-1 text-xs text-muted-foreground">
                    Ordem {b.displayOrder} ·{" "}
                    {ACTION_TYPES.find((a) => a.value === b.actionType)?.label ?? b.actionType}
                    {b.actionValue ? ` → ${b.actionValue}` : ""}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-1">
                  <Switch
                    checked={b.active}
                    disabled={!canPublish}
                    onCheckedChange={() => toggleActive(b)}
                    aria-label={canPublish ? "Alterar publicação do banner" : "Sem permissão para publicar"}
                  />
                  {canEdit && (
                    <Button variant="ghost" size="icon" onClick={() => startEdit(b)}>
                      <Pencil className="h-4 w-4" />
                    </Button>
                  )}
                  {canDelete && (
                    <Button variant="ghost" size="icon" onClick={() => remove(b)}>
                      <Trash2 className="h-4 w-4 text-destructive" />
                    </Button>
                  )}
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
