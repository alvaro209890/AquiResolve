"use client"

import { useEffect, useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Skeleton } from "@/components/ui/skeleton"
import { Separator } from "@/components/ui/separator"
import { subscribeServiceChecklists } from "@/lib/services/firebase-checklists"
import { deriveStatusFechamento, STATUS_FECHAMENTO_CONFIG } from "@/lib/orders/checklist-closure"
import type {
  ServiceChecklist,
  FotoFase,
  ChecklistItemResposta,
  AvariaPreExistente,
} from "@/types/checklist"
import { format } from "date-fns"
import { ptBR } from "date-fns/locale"
import {
  Camera,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  ClipboardList,
  Clock,
  FileSignature,
  KeyRound,
  MapPin,
  PenLine,
  TriangleAlert,
  User,
  XCircle,
} from "lucide-react"

function toDate(v: unknown): Date | null {
  if (!v) return null
  if (v instanceof Date) return v
  if (typeof (v as { toDate?: () => Date }).toDate === "function")
    return (v as { toDate: () => Date }).toDate()
  return null
}

function fmt(v: unknown): string {
  const d = toDate(v)
  return d ? format(d, "dd/MM/yyyy HH:mm", { locale: ptBR }) : "—"
}

const STATUS_CONFIG: Record<
  ServiceChecklist["status"],
  { label: string; badge: string; icon: React.ReactNode }
> = {
  nao_iniciado: {
    label: "Não iniciado",
    badge: "bg-muted text-muted-foreground",
    icon: <Clock className="h-4 w-4" />,
  },
  em_progresso: {
    label: "Em progresso",
    badge: "bg-blue-100 text-blue-700",
    icon: <PenLine className="h-4 w-4" />,
  },
  aguardando_assinatura_cliente: {
    label: "Aguardando validação do cliente",
    badge: "bg-amber-100 text-amber-800",
    icon: <FileSignature className="h-4 w-4" />,
  },
  aguardando_assinatura_prestador: {
    label: "Aguardando validação do prestador",
    badge: "bg-amber-100 text-amber-800",
    icon: <FileSignature className="h-4 w-4" />,
  },
  concluido: {
    label: "Concluído",
    badge: "bg-green-100 text-green-700",
    icon: <CheckCircle2 className="h-4 w-4" />,
  },
  rejeitado: {
    label: "Rejeitado",
    badge: "bg-red-100 text-red-700",
    icon: <XCircle className="h-4 w-4" />,
  },
}

const FASE_LABELS: Record<FotoFase, string> = {
  antes: "Antes",
  durante: "Durante",
  depois: "Depois",
  avaria: "Avaria",
}

const FASE_COLORS: Record<FotoFase, string> = {
  antes: "bg-blue-100 text-blue-700",
  durante: "bg-orange-100 text-orange-700",
  depois: "bg-green-100 text-green-700",
  avaria: "bg-red-100 text-red-700",
}

interface Props {
  orderId: string
}

export function ServiceChecklistPanel({ orderId }: Props) {
  const [checklists, setChecklists] = useState<ServiceChecklist[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [expandedSections, setExpandedSections] = useState<Set<string>>(
    new Set(["respostas", "avarias", "fotos", "finalizacao"])
  )

  useEffect(() => {
    setLoading(true)
    const unsub = subscribeServiceChecklists(
      orderId,
      (data) => {
        setChecklists(data)
        setLoading(false)
        if (data.length > 0 && !selectedId) {
          setSelectedId(data[0].id)
        }
      },
      () => setLoading(false)
    )
    return unsub
  }, [orderId])

  const selected = checklists.find((c) => c.id === selectedId) ?? null

  const toggleSection = (s: string) =>
    setExpandedSections((prev) => {
      const next = new Set(prev)
      if (next.has(s)) next.delete(s)
      else next.add(s)
      return next
    })

  if (loading) {
    return (
      <div className="space-y-3 p-4">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-16 w-full" />
        ))}
      </div>
    )
  }

  if (checklists.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-center gap-3">
        <ClipboardList className="h-10 w-10 text-muted-foreground/40" />
        <p className="text-muted-foreground text-sm">
          Nenhum checklist preenchido para este serviço.
        </p>
        <p className="text-xs text-muted-foreground max-w-xs">
          O prestador preenche o checklist após iniciar o atendimento no aplicativo mobile.
        </p>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      {/* Seletor de checklist (caso haja mais de um) */}
      {checklists.length > 1 && (
        <div className="flex flex-wrap gap-2">
          {checklists.map((c) => {
            const cfg = STATUS_CONFIG[c.status]
            return (
              <button
                key={c.id}
                onClick={() => setSelectedId(c.id)}
                className={`rounded-lg border px-3 py-2 text-sm text-left transition-colors ${
                  selectedId === c.id
                    ? "border-blue-500 bg-blue-50"
                    : "border-border hover:bg-muted/40"
                }`}
              >
                <div className="font-medium">{c.templateNome}</div>
                <div className="text-xs text-muted-foreground">{fmt(c.createdAt)}</div>
              </button>
            )
          })}
        </div>
      )}

      {selected && (
        <div className="space-y-4">
          {/* Header do checklist */}
          <Card>
            <CardContent className="p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h3 className="font-semibold text-base">{selected.templateNome}</h3>
                  <div className="flex items-center gap-2 mt-1 text-sm text-muted-foreground">
                    <User className="h-3.5 w-3.5" />
                    <span>{selected.providerNome}</span>
                  </div>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Badge className={STATUS_CONFIG[selected.status].badge}>
                    <span className="flex items-center gap-1">
                      {STATUS_CONFIG[selected.status].icon}
                      {STATUS_CONFIG[selected.status].label}
                    </span>
                  </Badge>
                  {(() => {
                    const sf = deriveStatusFechamento(selected)
                    return sf ? (
                      <Badge className={STATUS_FECHAMENTO_CONFIG[sf].badge}>
                        {STATUS_FECHAMENTO_CONFIG[sf].label}
                      </Badge>
                    ) : null
                  })()}
                </div>
              </div>

              {selected.servicosRealizados && selected.servicosRealizados.length > 0 && (
                <div className="mt-3">
                  <p className="text-xs font-medium text-muted-foreground mb-1.5">Serviços realizados</p>
                  <div className="flex flex-wrap gap-1.5">
                    {selected.servicosRealizados.map((s) => (
                      <Badge key={s} variant="outline" className="text-xs">
                        {s}
                      </Badge>
                    ))}
                  </div>
                </div>
              )}

              {selected.avariasPreExistentes && (
                <div className="mt-3 rounded-lg bg-amber-50 border border-amber-200 px-3 py-2">
                  <p className="text-xs font-medium text-amber-700">Avarias pré-existentes (proteção jurídica)</p>
                  <p className="text-sm text-amber-800 mt-0.5">{selected.avariasPreExistentes}</p>
                </div>
              )}

              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-4 text-xs text-muted-foreground">
                <div>
                  <p className="font-medium text-foreground">Iniciado em</p>
                  <p>{fmt(selected.iniciadoEm)}</p>
                </div>
                <div>
                  <p className="font-medium text-foreground">Concluído em</p>
                  <p>{fmt(selected.concluidoEm)}</p>
                </div>
                <div>
                  <p className="font-medium text-foreground">Respostas</p>
                  <p>
                    {selected.respostas?.length ?? 0} item(s)
                  </p>
                </div>
                <div>
                  <p className="font-medium text-foreground">Fotos</p>
                  <p>{selected.fotos?.length ?? 0} foto(s)</p>
                </div>
              </div>

              {selected.motivoNaoConclusao && (
                <div className="mt-3 rounded-lg bg-red-50 border border-red-200 px-3 py-2">
                  <p className="text-xs font-medium text-red-700">Motivo da não conclusão</p>
                  <p className="text-sm text-red-800 mt-0.5">{selected.motivoNaoConclusao}</p>
                </div>
              )}

              {selected.observacoesTecnicas && (
                <div className="mt-3 rounded-lg bg-blue-50 border border-blue-200 px-3 py-2">
                  <p className="text-xs font-medium text-blue-700">Observações do desfecho</p>
                  <p className="text-sm text-blue-800 mt-0.5">{selected.observacoesTecnicas}</p>
                </div>
              )}

              {selected.termoAceite?.aceito && (
                <div className="mt-3 rounded-lg bg-green-50 border border-green-200 px-3 py-2 flex items-start gap-2">
                  <CheckCircle2 className="h-4 w-4 text-green-600 mt-0.5 shrink-0" />
                  <div>
                    <p className="text-xs font-medium text-green-700">Termo de aceite assinado</p>
                    <p className="text-xs text-green-800 mt-0.5 italic">“{selected.termoAceite.texto}”</p>
                    {selected.termoAceite.aceitoPor && (
                      <p className="text-xs text-muted-foreground mt-1">
                        Por: {selected.termoAceite.aceitoPor} · {fmt(selected.termoAceite.aceitoEm)}
                      </p>
                    )}
                  </div>
                </div>
              )}
            </CardContent>
          </Card>

          {/* Avarias pré-existentes */}
          {selected.avariasPre && selected.avariasPre.length > 0 && (
            <CollapsibleSection
              id="avarias"
              title="Avarias pré-existentes"
              subtitle={`${selected.avariasPre.length} registro(s) — Proteção jurídica do prestador`}
              icon={<TriangleAlert className="h-4 w-4 text-amber-600" />}
              expanded={expandedSections.has("avarias")}
              onToggle={() => toggleSection("avarias")}
              urgent
            >
              <div className="space-y-3">
                {selected.avariasPre.map((avaria) => (
                  <AvariaCard key={avaria.id} avaria={avaria} />
                ))}
              </div>
            </CollapsibleSection>
          )}

          {/* Respostas do checklist */}
          {selected.respostas && selected.respostas.length > 0 && (
            <CollapsibleSection
              id="respostas"
              title="Respostas do checklist"
              subtitle={`${selected.respostas.length} item(s) respondido(s)`}
              icon={<ClipboardList className="h-4 w-4 text-blue-600" />}
              expanded={expandedSections.has("respostas")}
              onToggle={() => toggleSection("respostas")}
            >
              <RespostasSection respostas={selected.respostas} />
            </CollapsibleSection>
          )}

          {/* Galeria de fotos */}
          {selected.fotos && selected.fotos.length > 0 && (
            <CollapsibleSection
              id="fotos"
              title="Registro fotográfico"
              subtitle={`${selected.fotos.length} foto(s)`}
              icon={<Camera className="h-4 w-4 text-green-600" />}
              expanded={expandedSections.has("fotos")}
              onToggle={() => toggleSection("fotos")}
            >
              <FotosSection fotos={selected.fotos} />
            </CollapsibleSection>
          )}

          {/* Finalização */}
          <CollapsibleSection
            id="finalizacao"
            title="Finalização por código do cliente"
            subtitle="O checklist não usa mais assinatura desenhada na tela"
            icon={<KeyRound className="h-4 w-4 text-purple-600" />}
            expanded={expandedSections.has("finalizacao")}
            onToggle={() => toggleSection("finalizacao")}
          >
            <div className="rounded-lg border p-4 text-sm text-muted-foreground">
              <p className="font-medium text-foreground">Contrato atual de encerramento</p>
              <p className="mt-1">
                Após anexar as fotos de chegada e pós-serviço, o prestador informa o código único
                exibido apenas para o cliente no aplicativo.
              </p>
              <p className="mt-2">
                Checklist concluído em: <span className="font-medium text-foreground">{fmt(selected.concluidoEm)}</span>
              </p>
            </div>
          </CollapsibleSection>
        </div>
      )}
    </div>
  )
}

// ─── Collapsible Section ──────────────────────────────────────────────────────

function CollapsibleSection({
  id,
  title,
  subtitle,
  icon,
  expanded,
  onToggle,
  children,
  urgent,
}: {
  id: string
  title: string
  subtitle?: string
  icon: React.ReactNode
  expanded: boolean
  onToggle: () => void
  children: React.ReactNode
  urgent?: boolean
}) {
  return (
    <Card className={urgent ? "border-amber-200" : undefined}>
      <CardHeader
        className="pb-3 cursor-pointer hover:bg-muted/30 transition-colors rounded-t-lg"
        onClick={onToggle}
      >
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            {icon}
            <div>
              <CardTitle className="text-sm font-semibold">{title}</CardTitle>
              {subtitle && (
                <CardDescription className="text-xs mt-0.5">{subtitle}</CardDescription>
              )}
            </div>
          </div>
          {expanded ? (
            <ChevronUp className="h-4 w-4 text-muted-foreground" />
          ) : (
            <ChevronDown className="h-4 w-4 text-muted-foreground" />
          )}
        </div>
      </CardHeader>
      {expanded && (
        <CardContent className="pt-0">
          <Separator className="mb-4" />
          {children}
        </CardContent>
      )}
    </Card>
  )
}

// ─── Avaria card ──────────────────────────────────────────────────────────────

function AvariaCard({ avaria }: { avaria: AvariaPreExistente }) {
  return (
    <div className="rounded-lg border border-amber-200 bg-amber-50/60 p-3">
      <div className="flex items-start gap-3">
        <TriangleAlert className="h-4 w-4 text-amber-600 mt-0.5 shrink-0" />
        <div className="flex-1">
          <p className="text-sm font-medium text-amber-900">{avaria.descricao}</p>
          {avaria.localização && (
            <div className="flex items-center gap-1 text-xs text-amber-700 mt-1">
              <MapPin className="h-3 w-3" />
              {avaria.localização}
            </div>
          )}
          <p className="text-xs text-muted-foreground mt-1">
            Registrado em: {fmt(avaria.registradoEm)}
          </p>
        </div>
        {avaria.fotoUrl && (
          <a
            href={avaria.fotoUrl}
            target="_blank"
            rel="noreferrer"
            className="shrink-0"
          >
            <img
              src={avaria.fotoUrl}
              alt="Avaria"
              className="h-16 w-16 rounded object-cover border border-amber-200 hover:opacity-80 transition-opacity"
            />
          </a>
        )}
      </div>
    </div>
  )
}

// ─── Respostas ────────────────────────────────────────────────────────────────

function RespostasSection({ respostas }: { respostas: ChecklistItemResposta[] }) {
  const byFase = {
    pre_servico: respostas.filter((r) => r.fase === "pre_servico"),
    execucao: respostas.filter((r) => r.fase === "execucao"),
    conclusao: respostas.filter((r) => r.fase === "conclusao"),
  }

  const FASE_LABELS_PT: Record<string, string> = {
    pre_servico: "Pré-serviço",
    execucao: "Durante execução",
    conclusao: "Conclusão",
  }

  return (
    <div className="space-y-5">
      {(Object.entries(byFase) as [string, ChecklistItemResposta[]][]).map(
        ([fase, items]) =>
          items.length > 0 && (
            <div key={fase}>
              <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-2">
                {FASE_LABELS_PT[fase]}
              </p>
              <div className="space-y-2">
                {items.map((r) => (
                  <RespostaItem key={r.itemId} resposta={r} />
                ))}
              </div>
            </div>
          )
      )}
    </div>
  )
}

function RespostaItem({ resposta }: { resposta: ChecklistItemResposta }) {
  const valorStr =
    resposta.valor === null || resposta.valor === undefined
      ? "—"
      : typeof resposta.valor === "boolean"
      ? resposta.valor
        ? "Sim"
        : "Não"
      : Array.isArray(resposta.valor)
      ? resposta.valor.join(", ")
      : String(resposta.valor)

  const isEmpty = valorStr === "—"

  return (
    <div className="flex items-start gap-3 rounded-lg bg-muted/30 px-3 py-2">
      <div className="flex-1">
        <p className="text-sm font-medium">{resposta.titulo}</p>
        {resposta.observacao && (
          <p className="text-xs text-muted-foreground mt-0.5">Obs: {resposta.observacao}</p>
        )}
      </div>
      <div className="text-right shrink-0">
        <p className={`text-sm font-semibold ${isEmpty ? "text-muted-foreground" : ""}`}>
          {valorStr}
        </p>
        {resposta.fotoUrls && resposta.fotoUrls.length > 0 && (
          <div className="flex gap-1 mt-1 justify-end">
            {resposta.fotoUrls.map((url, i) => (
              <a key={i} href={url} target="_blank" rel="noreferrer">
                <img
                  src={url}
                  alt=""
                  className="h-8 w-8 rounded object-cover border hover:opacity-80 transition-opacity"
                />
              </a>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

// ─── Fotos ────────────────────────────────────────────────────────────────────

function FotosSection({ fotos }: { fotos: ServiceChecklist["fotos"] }) {
  const byFase: Record<FotoFase, typeof fotos> = {
    antes: fotos.filter((f) => f.fase === "antes"),
    durante: fotos.filter((f) => f.fase === "durante"),
    depois: fotos.filter((f) => f.fase === "depois"),
    avaria: fotos.filter((f) => f.fase === "avaria"),
  }

  return (
    <div className="space-y-4">
      {(Object.entries(byFase) as [FotoFase, typeof fotos][]).map(
        ([fase, items]) =>
          items.length > 0 && (
            <div key={fase}>
              <Badge className={`${FASE_COLORS[fase]} mb-2 text-xs`}>
                {FASE_LABELS[fase]} ({items.length})
              </Badge>
              <div className="grid grid-cols-3 sm:grid-cols-4 gap-2">
                {items.map((foto) => (
                  <a
                    key={foto.id}
                    href={foto.url}
                    target="_blank"
                    rel="noreferrer"
                    className="group relative"
                  >
                    <img
                      src={foto.thumbnailUrl ?? foto.url}
                      alt={foto.descricao ?? "Foto do serviço"}
                      className="w-full aspect-square object-cover rounded-lg border hover:opacity-90 transition-opacity"
                    />
                    {foto.lat && foto.lng && (
                      <div className="absolute bottom-1 left-1 bg-black/60 rounded px-1 py-0.5">
                        <MapPin className="h-2.5 w-2.5 text-white" />
                      </div>
                    )}
                    {foto.descricao && (
                      <p className="text-xs text-muted-foreground mt-1 truncate">
                        {foto.descricao}
                      </p>
                    )}
                  </a>
                ))}
              </div>
            </div>
          )
      )}
    </div>
  )
}
