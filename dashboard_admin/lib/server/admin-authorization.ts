import "server-only"

import { NextRequest, NextResponse } from "next/server"
import {
  AdminPermission,
  AdminPermissions,
  hasAdminPermission,
  hasAnyAdminPermission,
  normalizeAdminPermissions,
} from "@/lib/admin-permissions"
import { getAdminAuth, getAdminFirestore } from "@/lib/firebase-admin"
import * as admin from "firebase-admin"

export interface AdminActor {
  uid: string
  email: string
  name: string
  isMaster: boolean
  permissions: AdminPermissions
}

export class AdminAuthorizationError extends Error {
  constructor(
    message: string,
    public readonly status: 401 | 403 = 403
  ) {
    super(message)
    this.name = "AdminAuthorizationError"
  }
}

function bearerToken(request: NextRequest): string {
  const header = request.headers.get("authorization") ?? ""
  const match = header.match(/^Bearer\s+(.+)$/i)
  if (!match?.[1]) {
    throw new AdminAuthorizationError("Autenticação administrativa obrigatória", 401)
  }
  return match[1]
}

export async function authorizeAdminRequest(request: NextRequest): Promise<AdminActor> {
  const decoded = await getAdminAuth()
    .verifyIdToken(bearerToken(request), true)
    .catch(() => {
      throw new AdminAuthorizationError("Sessão administrativa inválida ou expirada", 401)
    })

  const db = getAdminFirestore()
  const [masterSnap, userSnap] = await Promise.all([
    db.collection("adminmaster").doc("master").get(),
    db.collection("adminmaster").doc("master").collection("usuarios").doc(decoded.uid).get(),
  ])

  const tokenEmail = String(decoded.email ?? "").trim().toLowerCase()
  const masterData = masterSnap.data() ?? {}
  const masterEmail = String(masterData.email ?? "").trim().toLowerCase()
  const isMaster = Boolean(
    userSnap.exists &&
    tokenEmail &&
    masterEmail &&
    tokenEmail === masterEmail &&
    masterData.ativo !== false &&
    userSnap.data()?.ativo !== false &&
    userSnap.data()?.active !== false
  )

  if (isMaster) {
    return {
      uid: decoded.uid,
      email: tokenEmail,
      name: String(decoded.name ?? masterData.nome ?? "Administrador Master"),
      isMaster: true,
      permissions: normalizeAdminPermissions(masterData.permissoes, { master: true }),
    }
  }

  if (!userSnap.exists) {
    throw new AdminAuthorizationError("Usuário autenticado não possui perfil administrativo")
  }

  const userData = userSnap.data() ?? {}
  if (userData.ativo === false || userData.active === false) {
    throw new AdminAuthorizationError("Acesso administrativo desativado")
  }

  const profileEmail = String(userData.email ?? "").trim().toLowerCase()
  if (profileEmail && tokenEmail && profileEmail !== tokenEmail) {
    throw new AdminAuthorizationError("Perfil administrativo incompatível com a conta autenticada")
  }

  return {
    uid: decoded.uid,
    email: tokenEmail || profileEmail,
    name: String(userData.nome ?? decoded.name ?? tokenEmail),
    isMaster: false,
    permissions: normalizeAdminPermissions(userData.permissoes, { inheritLegacy: true }),
  }
}

export function assertAdminPermission(actor: AdminActor, permission: AdminPermission): void {
  if (!hasAdminPermission(actor.permissions, permission)) {
    throw new AdminAuthorizationError(`Permissão necessária: ${permission}`)
  }
}

export function assertAnyAdminPermission(
  actor: AdminActor,
  permissions: readonly AdminPermission[]
): void {
  if (!hasAnyAdminPermission(actor.permissions, permissions)) {
    throw new AdminAuthorizationError(`Uma destas permissões é necessária: ${permissions.join(", ")}`)
  }
}

export async function requireAdminPermission(
  request: NextRequest,
  permission: AdminPermission
): Promise<AdminActor> {
  const actor = await authorizeAdminRequest(request)
  assertAdminPermission(actor, permission)
  return actor
}

export async function requireAnyAdminPermission(
  request: NextRequest,
  permissions: readonly AdminPermission[]
): Promise<AdminActor> {
  const actor = await authorizeAdminRequest(request)
  assertAnyAdminPermission(actor, permissions)
  return actor
}

export function adminAuthorizationResponse(error: unknown): NextResponse | null {
  if (!(error instanceof AdminAuthorizationError)) return null
  return NextResponse.json(
    { success: false, error: error.message },
    { status: error.status }
  )
}

export async function writeAdminAuditLog(
  actor: AdminActor,
  event: {
    action: string
    resource: string
    resourceId?: string
    details?: Record<string, unknown>
  }
): Promise<void> {
  const db = getAdminFirestore()
  await db.collection("admin_audit_logs").add({
    actorUid: actor.uid,
    actorEmail: actor.email,
    actorName: actor.name,
    actorMaster: actor.isMaster,
    action: event.action,
    resource: event.resource,
    resourceId: event.resourceId ?? null,
    details: event.details ?? {},
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  })
}
