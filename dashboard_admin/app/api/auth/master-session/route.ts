import { NextRequest, NextResponse } from "next/server"
import { getAdminFirestore } from "@/lib/firebase-admin"
import { normalizeAdminPermissions } from "@/lib/admin-permissions"
import { requireMasterSession } from "@/lib/server/master-session"

export async function GET(request: NextRequest) {
  const denied = await requireMasterSession(request)
  if (denied) return denied

  const snap = await getAdminFirestore().collection("adminmaster").doc("master").get()
  const data = snap.data() ?? {}
  return NextResponse.json({
    success: true,
    user: {
      id: snap.id,
      email: String(data.email ?? ""),
      nome: String(data.nome ?? "Administrador Master"),
      permissoes: normalizeAdminPermissions(data.permissoes, { master: true }),
    },
  })
}
