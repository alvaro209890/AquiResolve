import {
  addDoc,
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  limit,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  Timestamp,
  updateDoc,
  where,
  type Unsubscribe,
} from "firebase/firestore"
import { db } from "@/lib/firebase"
import type {
  ChecklistTemplate,
  ChecklistTemplateFilters,
  ChecklistTemplateItem,
  ServiceChecklist,
  ServiceChecklistFilters,
  ServiceChecklistStatus,
  ChecklistStats,
  StatusFechamento,
} from "@/types/checklist"

const TEMPLATES_COLLECTION = "checklist_templates"
const MOBILE_CHECKLISTS_COLLECTION = "checklists"
const SERVICE_CHECKLISTS_SUBCOLLECTION = "checklists"

// ─── Helpers ────────────────────────────────────────────────────────────────

function asDb() {
  if (!db) throw new Error("Firestore não inicializado")
  return db
}

function newItemId(): string {
  return typeof crypto !== "undefined" && crypto.randomUUID
    ? crypto.randomUUID()
    : `item_${Date.now()}_${Math.random().toString(16).slice(2)}`
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value)
}

function readString(data: Record<string, unknown>, key: string): string | undefined {
  const value = data[key]
  return typeof value === "string" && value.trim() ? value : undefined
}

function readBoolean(data: Record<string, unknown>, key: string): boolean | null {
  const value = data[key]
  return typeof value === "boolean" ? value : null
}

function readStringArray(data: Record<string, unknown>, key: string): string[] {
  const value = data[key]
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : []
}

function readTimestamp(data: Record<string, unknown>, key: string): Timestamp | undefined {
  const value = data[key]
  if (value instanceof Timestamp) {
    return value
  }
  if (isRecord(value) && typeof value.seconds === "number") {
    return new Timestamp(
      value.seconds,
      typeof value.nanoseconds === "number" ? value.nanoseconds : 0
    )
  }
  return undefined
}

function readTimestampArray(data: Record<string, unknown>, key: string): Timestamp[] {
  const value = data[key]
  if (!Array.isArray(value)) return []
  return value.filter((item): item is Timestamp => item instanceof Timestamp)
}

function timestampMillis(value: unknown): number {
  if (value instanceof Timestamp) return value.toMillis()
  if (value instanceof Date) return value.getTime()
  if (isRecord(value) && typeof value.seconds === "number") {
    return value.seconds * 1000
  }
  return 0
}

function mobileStatusToAdminStatus(status: unknown): ServiceChecklistStatus {
  switch (status) {
    case "completed":
    case "ready_for_completion_code":
      return "concluido"
    case "signatures_pending":
      return "em_progresso"
    case "photos_pending":
    case "checklist_pending":
      return "em_progresso"
    default:
      return "nao_iniciado"
  }
}

function mobileProblemResolutionLabel(value: unknown): string | null {
  switch (value) {
    case "resolved":
      return "Concluído com sucesso"
    case "return_needed":
      return "Não concluído, haverá retorno"
    case "not_resolved":
      return "Não concluído, sem retorno"
    default:
      return null
  }
}

function mobileProblemResolutionToClosure(value: unknown): StatusFechamento | undefined {
  switch (value) {
    case "resolved":
      return "concluido_sucesso"
    case "return_needed":
      return "retorno_pendente"
    case "not_resolved":
      return "nao_concluido_sem_retorno"
    default:
      return undefined
  }
}

const MOBILE_BOOLEAN_RESPONSES: Array<{
  key: string
  title: string
  fase: "pre_servico" | "execucao" | "conclusao"
}> = [
  { key: "clientPresent", title: "Cliente presente?", fase: "pre_servico" },
  { key: "serviceMatches", title: "Serviço corresponde ao pedido?", fase: "pre_servico" },
  { key: "visibleDamage", title: "Havia danos visíveis no local?", fase: "pre_servico" },
  { key: "materialAvailable", title: "Material disponível para execução?", fase: "pre_servico" },
  { key: "materialsUsed", title: "Usou material/suprimento específico?", fase: "execucao" },
  { key: "clientObservations", title: "Observações do cliente registradas?", fase: "pre_servico" },
  { key: "executedAsRequested", title: "Executado conforme solicitado?", fase: "execucao" },
  { key: "additionalService", title: "Serviço adicional necessário?", fase: "execucao" },
  { key: "partsReplaced", title: "Peças substituídas?", fase: "execucao" },
  { key: "valueChanged", title: "Valor sofreu alteração?", fase: "execucao" },
  { key: "serviceCompleted", title: "Serviço concluído?", fase: "conclusao" },
  { key: "cleanAfterService", title: "Local limpo após execução?", fase: "conclusao" },
  { key: "declarationAccepted", title: "Declaração de veracidade aceita?", fase: "conclusao" },
]

function buildMobileResponses(data: Record<string, unknown>): ServiceChecklist["respostas"] {
  const updatedAt = readTimestamp(data, "updatedAt") ?? Timestamp.now()
  const respostas: ServiceChecklist["respostas"] = []

  for (const item of MOBILE_BOOLEAN_RESPONSES) {
    const value = readBoolean(data, item.key)
    if (value === null) continue
    respostas.push({
      itemId: item.key,
      titulo: item.title,
      tipo: "checkbox",
      fase: item.fase,
      valor: value,
      respondidoEm: updatedAt,
    })
  }

  const services = readStringArray(data, "serviceDescription")
  if (services.length > 0) {
    respostas.push({
      itemId: "serviceDescription",
      titulo: "Serviços realizados",
      tipo: "multi_select",
      fase: "execucao",
      valor: services,
      respondidoEm: updatedAt,
    })
  }

  const resolution = mobileProblemResolutionLabel(data.problemResolution)
  if (resolution) {
    respostas.push({
      itemId: "problemResolution",
      titulo: "Resolução do problema",
      tipo: "select",
      fase: "conclusao",
      valor: resolution,
      respondidoEm: updatedAt,
    })
  }

  const executionDescription = readString(data, "executionDescription")
  if (executionDescription) {
    respostas.push({
      itemId: "executionDescription",
      titulo: "Descrição detalhada da execução",
      tipo: "textarea",
      fase: "conclusao",
      valor: executionDescription,
      respondidoEm: updatedAt,
    })
  }

  const materialsDescription = readString(data, "materialsDescription")
  if (materialsDescription) {
    respostas.push({
      itemId: "materialsDescription",
      titulo: "Materiais/suprimentos usados",
      tipo: "textarea",
      fase: "execucao",
      valor: materialsDescription,
      respondidoEm: updatedAt,
    })
  }

  const observations = readString(data, "observations")
  if (observations) {
    respostas.push({
      itemId: "observations",
      titulo: "Observações gerais",
      tipo: "textarea",
      fase: "conclusao",
      valor: observations,
      respondidoEm: updatedAt,
    })
  }

  return respostas
}

function buildMobilePhotos(data: Record<string, unknown>, orderId: string): ServiceChecklist["fotos"] {
  const providerId = readString(data, "providerId") ?? "mobile"
  const fallbackTimestamp = readTimestamp(data, "updatedAt") ?? Timestamp.now()
  const phases = [
    { key: "photosBefore", timestamps: "photoTimestampsBefore", fase: "antes" as const },
    { key: "photosDuring", timestamps: "photoTimestampsDuring", fase: "durante" as const },
    { key: "photosAfter", timestamps: "photoTimestampsAfter", fase: "depois" as const },
  ]

  return phases.flatMap(({ key, timestamps, fase }) => {
    const urls = readStringArray(data, key)
    const times = readTimestampArray(data, timestamps)
    return urls.map((url, index) => ({
      id: `${orderId}-${fase}-${index}`,
      url,
      fase,
      timestamp: times[index] ?? fallbackTimestamp,
      uploadedBy: providerId,
      storagePath: "",
    }))
  })
}

function buildMobileSignature(
  data: Record<string, unknown>,
  orderId: string,
  type: "cliente" | "prestador"
): ServiceChecklist["assinaturaCliente"] | undefined {
  const prefix = type === "cliente" ? "client" : "provider"
  const url = readString(data, `${prefix}SignatureUrl`)
  if (!url) return undefined

  const signatoryName =
    readString(data, `${prefix}SignatureName`) ??
    (type === "prestador" ? readString(data, "providerName") : readString(data, "clientName")) ??
    (type === "prestador" ? "Prestador" : "Cliente")

  return {
    dataUrl: url,
    hash: `${orderId}-${type}-${timestampMillis(data[`${prefix}SignedAt`]) || "mobile"}`,
    signatoryName,
    signatoryId: type === "prestador" ? readString(data, "providerId") : undefined,
    signatoryType: type,
    signedAt:
      readTimestamp(data, `${prefix}SignedAt`) ??
      readTimestamp(data, "updatedAt") ??
      Timestamp.now(),
  }
}

function normalizeMobileChecklist(
  orderId: string,
  rawData: Record<string, unknown>
): ServiceChecklist {
  const createdAt =
    readTimestamp(rawData, "createdAt") ??
    readTimestamp(rawData, "startedAt") ??
    readTimestamp(rawData, "updatedAt") ??
    Timestamp.now()
  const updatedAt = readTimestamp(rawData, "updatedAt") ?? createdAt
  const providerId = readString(rawData, "providerId") ?? ""
  const providerNome =
    readString(rawData, "providerName") ??
    readString(rawData, "providerSignatureName") ??
    (providerId || "Prestador mobile")
  const clienteNome =
    readString(rawData, "clientName") ??
    readString(rawData, "clientSignatureName")
  const services = readStringArray(rawData, "serviceDescription")
  const closure = mobileProblemResolutionToClosure(rawData.problemResolution)
  const declarationAccepted = readBoolean(rawData, "declarationAccepted") === true

  return {
    id: orderId,
    orderId,
    templateId: "mobile-os-checklist",
    templateNome: "Checklist OS mobile",
    providerId,
    providerNome,
    clienteNome,
    status: mobileStatusToAdminStatus(rawData.status),
    respostas: buildMobileResponses(rawData),
    avariasPre: [],
    fotos: buildMobilePhotos(rawData, orderId),
    assinaturaCliente: buildMobileSignature(rawData, orderId, "cliente"),
    assinaturaPrestador: buildMobileSignature(rawData, orderId, "prestador"),
    servicosRealizados: services,
    avariasPreExistentes: readString(rawData, "preExistingDamages"),
    statusFechamento: closure,
    termoAceite: declarationAccepted
      ? {
          aceito: true,
          texto: "Declaração de veracidade aceita no aplicativo mobile.",
          aceitoEm: updatedAt,
          aceitoPor: providerNome,
        }
      : undefined,
    motivoNaoConclusao:
      rawData.problemResolution === "not_resolved"
        ? readString(rawData, "observations") ?? "Prestador informou que não haverá retorno."
        : undefined,
    observacoesTecnicas:
      readString(rawData, "observations") ?? readString(rawData, "executionDescription"),
    iniciadoEm: readTimestamp(rawData, "startedAt"),
    concluidoEm: readTimestamp(rawData, "completedAt"),
    createdAt,
    updatedAt,
  }
}

function normalizeLegacyChecklist(id: string, data: Record<string, unknown>): ServiceChecklist {
  return { id, ...data } as ServiceChecklist
}

function mergeServiceChecklists(
  mobile: ServiceChecklist[],
  legacy: ServiceChecklist[]
): ServiceChecklist[] {
  const seen = new Set<string>()
  return [...mobile, ...legacy]
    .filter((item) => {
      if (seen.has(item.id)) return false
      seen.add(item.id)
      return true
    })
    .sort((a, b) => timestampMillis(b.createdAt) - timestampMillis(a.createdAt))
}

// ─── Templates ──────────────────────────────────────────────────────────────

export async function getChecklistTemplates(
  filters?: ChecklistTemplateFilters
): Promise<ChecklistTemplate[]> {
  const firestore = asDb()
  const ref = collection(firestore, TEMPLATES_COLLECTION)

  const constraints: Parameters<typeof query>[1][] = [orderBy("createdAt", "desc")]

  if (filters?.ativo !== undefined) {
    constraints.push(where("ativo", "==", filters.ativo))
  }
  if (filters?.cliente && filters.cliente !== "TODOS") {
    constraints.push(where("cliente", "==", filters.cliente))
  }

  const q = query(ref, ...constraints)
  const snap = await getDocs(q)

  let results = snap.docs.map((d) => ({ id: d.id, ...d.data() } as ChecklistTemplate))

  if (filters?.search) {
    const term = filters.search.toLowerCase()
    results = results.filter(
      (t) =>
        t.nome.toLowerCase().includes(term) ||
        t.descricao.toLowerCase().includes(term) ||
        t.tiposServico.some((s) => s.toLowerCase().includes(term))
    )
  }

  if (filters?.tipoServico) {
    results = results.filter(
      (t) =>
        t.tiposServico.includes("*") ||
        t.tiposServico.some((s) =>
          s.toLowerCase().includes(filters.tipoServico!.toLowerCase())
        )
    )
  }

  return results
}

export function subscribeChecklistTemplates(
  onData: (templates: ChecklistTemplate[]) => void,
  onError?: (err: Error) => void,
  filters?: ChecklistTemplateFilters
): Unsubscribe {
  const firestore = asDb()
  const constraints: Parameters<typeof query>[1][] = [orderBy("createdAt", "desc")]

  if (filters?.ativo !== undefined) {
    constraints.push(where("ativo", "==", filters.ativo))
  }

  const q = query(collection(firestore, TEMPLATES_COLLECTION), ...constraints)

  return onSnapshot(
    q,
    (snap) => {
      let templates = snap.docs.map((d) => ({ id: d.id, ...d.data() } as ChecklistTemplate))
      if (filters?.search) {
        const term = filters.search.toLowerCase()
        templates = templates.filter(
          (t) =>
            t.nome.toLowerCase().includes(term) ||
            t.descricao.toLowerCase().includes(term)
        )
      }
      onData(templates)
    },
    (err) => onError?.(err)
  )
}

export async function getChecklistTemplate(id: string): Promise<ChecklistTemplate | null> {
  const snap = await getDoc(doc(asDb(), TEMPLATES_COLLECTION, id))
  if (!snap.exists()) return null
  return { id: snap.id, ...snap.data() } as ChecklistTemplate
}

export interface CreateTemplateInput {
  nome: string
  descricao: string
  tiposServico: string[]
  cliente: string
  itens: Omit<ChecklistTemplateItem, "id">[]
  ativo: boolean
  obrigatorio: boolean
  actorId: string
  actorName?: string
}

export async function createChecklistTemplate(input: CreateTemplateInput): Promise<string> {
  const firestore = asDb()

  const itensComId: ChecklistTemplateItem[] = input.itens.map((item, idx) => ({
    ...item,
    id: newItemId(),
    ordem: item.ordem ?? idx,
  }))

  const data: Omit<ChecklistTemplate, "id"> = {
    nome: input.nome.trim(),
    descricao: input.descricao.trim(),
    tiposServico: input.tiposServico,
    cliente: input.cliente || "TODOS",
    itens: itensComId,
    ativo: input.ativo,
    obrigatorio: input.obrigatorio,
    versao: 1,
    createdAt: serverTimestamp() as Timestamp,
    updatedAt: serverTimestamp() as Timestamp,
    createdBy: input.actorId,
  }

  const ref = await addDoc(collection(firestore, TEMPLATES_COLLECTION), data)
  return ref.id
}

export interface UpdateTemplateInput extends Partial<CreateTemplateInput> {
  id: string
}

export async function updateChecklistTemplate(input: UpdateTemplateInput): Promise<void> {
  const firestore = asDb()
  const ref = doc(firestore, TEMPLATES_COLLECTION, input.id)

  const updates: Record<string, unknown> = {
    updatedAt: serverTimestamp(),
    updatedBy: input.actorId,
  }

  if (input.nome !== undefined) updates.nome = input.nome.trim()
  if (input.descricao !== undefined) updates.descricao = input.descricao.trim()
  if (input.tiposServico !== undefined) updates.tiposServico = input.tiposServico
  if (input.cliente !== undefined) updates.cliente = input.cliente
  if (input.ativo !== undefined) updates.ativo = input.ativo
  if (input.obrigatorio !== undefined) updates.obrigatorio = input.obrigatorio

  if (input.itens !== undefined) {
    const existing = (await getDoc(ref)).data()
    const currentVersion = (existing?.versao as number) || 1
    updates.versao = currentVersion + 1
    updates.itens = input.itens.map((item, idx) => ({
      ...item,
      id: (item as ChecklistTemplateItem).id || newItemId(),
      ordem: (item as ChecklistTemplateItem).ordem ?? idx,
    }))
  }

  await updateDoc(ref, updates)
}

export async function toggleChecklistTemplateStatus(
  id: string,
  ativo: boolean
): Promise<void> {
  await updateDoc(doc(asDb(), TEMPLATES_COLLECTION, id), {
    ativo,
    updatedAt: serverTimestamp(),
  })
}

export async function deleteChecklistTemplate(id: string): Promise<void> {
  await deleteDoc(doc(asDb(), TEMPLATES_COLLECTION, id))
}

// ─── Checklists de Serviço ───────────────────────────────────────────────────

export async function getServiceChecklists(
  orderId: string
): Promise<ServiceChecklist[]> {
  const firestore = asDb()
  const legacyRef = collection(
    firestore,
    "orders",
    orderId,
    SERVICE_CHECKLISTS_SUBCOLLECTION
  )
  const [mobileSnap, legacySnap] = await Promise.all([
    getDoc(doc(firestore, MOBILE_CHECKLISTS_COLLECTION, orderId)),
    getDocs(query(legacyRef, orderBy("createdAt", "desc"))),
  ])

  const mobile = mobileSnap.exists()
    ? [normalizeMobileChecklist(orderId, mobileSnap.data() as Record<string, unknown>)]
    : []
  const legacy = legacySnap.docs.map((d) =>
    normalizeLegacyChecklist(d.id, d.data() as Record<string, unknown>)
  )

  return mergeServiceChecklists(mobile, legacy)
}

export function subscribeServiceChecklists(
  orderId: string,
  onData: (checklists: ServiceChecklist[]) => void,
  onError?: (err: Error) => void
): Unsubscribe {
  const firestore = asDb()
  const legacyRef = collection(
    firestore,
    "orders",
    orderId,
    SERVICE_CHECKLISTS_SUBCOLLECTION
  )
  const q = query(legacyRef, orderBy("createdAt", "desc"))
  let mobile: ServiceChecklist[] = []
  let legacy: ServiceChecklist[] = []
  const emit = () => onData(mergeServiceChecklists(mobile, legacy))

  const unsubscribeMobile = onSnapshot(
    doc(firestore, MOBILE_CHECKLISTS_COLLECTION, orderId),
    (snap) => {
      mobile = snap.exists()
        ? [normalizeMobileChecklist(orderId, snap.data() as Record<string, unknown>)]
        : []
      emit()
    },
    (err) => onError?.(err)
  )

  const unsubscribeLegacy = onSnapshot(
    q,
    (snap) => {
      legacy = snap.docs.map((d) =>
        normalizeLegacyChecklist(d.id, d.data() as Record<string, unknown>)
      )
      emit()
    },
    (err) => onError?.(err)
  )

  return () => {
    unsubscribeMobile()
    unsubscribeLegacy()
  }
}

export async function getServiceChecklist(
  orderId: string,
  checklistId: string
): Promise<ServiceChecklist | null> {
  const firestore = asDb()
  const legacySnap = await getDoc(
    doc(firestore, "orders", orderId, SERVICE_CHECKLISTS_SUBCOLLECTION, checklistId)
  )
  if (legacySnap.exists()) {
    return normalizeLegacyChecklist(
      legacySnap.id,
      legacySnap.data() as Record<string, unknown>
    )
  }

  const mobileSnap = await getDoc(doc(firestore, MOBILE_CHECKLISTS_COLLECTION, orderId))
  if (!mobileSnap.exists()) return null
  const normalized = normalizeMobileChecklist(
    orderId,
    mobileSnap.data() as Record<string, unknown>
  )
  return checklistId === normalized.id ? normalized : null
}

export async function updateServiceChecklistStatus(
  orderId: string,
  checklistId: string,
  status: ServiceChecklistStatus
): Promise<void> {
  const updates: Record<string, unknown> = {
    status,
    updatedAt: serverTimestamp(),
  }
  if (status === "concluido") {
    updates.concluidoEm = serverTimestamp()
  }
  await updateDoc(
    doc(asDb(), "orders", orderId, SERVICE_CHECKLISTS_SUBCOLLECTION, checklistId),
    updates
  )
}

// ─── Validação de início ──────────────────────────────────────────────────────

export async function getServiceValidation(
  orderId: string
): Promise<Record<string, unknown> | null> {
  const snap = await getDoc(doc(asDb(), "orders", orderId))
  if (!snap.exists()) return null
  const data = snap.data()
  return (data?.serviceValidation as Record<string, unknown>) ?? null
}

export function subscribeOrderValidation(
  orderId: string,
  onData: (validation: Record<string, unknown> | null) => void,
  onError?: (err: Error) => void
): Unsubscribe {
  const ref = doc(asDb(), "orders", orderId)
  return onSnapshot(
    ref,
    (snap) => {
      if (!snap.exists()) {
        onData(null)
        return
      }
      const data = snap.data()
      onData((data?.serviceValidation as Record<string, unknown>) ?? null)
    },
    (err) => onError?.(err)
  )
}

// ─── Estatísticas ─────────────────────────────────────────────────────────────

export async function getChecklistStats(): Promise<ChecklistStats> {
  const firestore = asDb()
  const snap = await getDocs(collection(firestore, TEMPLATES_COLLECTION))

  const templates = snap.docs.map((d) => d.data() as Omit<ChecklistTemplate, "id">)
  const ativos = templates.filter((t) => t.ativo).length

  const porTipoServico: Record<string, number> = {}
  for (const t of templates) {
    for (const tipo of t.tiposServico) {
      porTipoServico[tipo] = (porTipoServico[tipo] || 0) + 1
    }
  }

  return {
    total: templates.length,
    ativos,
    inativos: templates.length - ativos,
    porTipoServico,
    preenchidosHoje: 0,
    preenchidosMes: 0,
    taxaConclusao: 0,
  }
}
