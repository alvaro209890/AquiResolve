import "server-only"

import { createHash, createHmac, timingSafeEqual } from "node:crypto"
import { NextRequest, NextResponse } from "next/server"
import { getAdminFirestore } from "@/lib/firebase-admin"

export const MASTER_SESSION_COOKIE = "aq_master_session"
const SESSION_SECONDS = 8 * 60 * 60

interface MasterSessionPayload {
  sub: "master"
  exp: number
}

function sessionSecret(passwordHash: string): string {
  const configured = process.env.MASTER_SESSION_SECRET?.trim()
  if (configured) return configured

  const serverCredential = process.env.FIREBASE_SERVICE_ACCOUNT?.trim()
  if (!serverCredential) {
    throw new Error("MASTER_SESSION_SECRET não configurado")
  }

  return createHash("sha256")
    .update(`${serverCredential}:${passwordHash}`)
    .digest("hex")
}

function signature(payload: string, passwordHash: string): string {
  return createHmac("sha256", sessionSecret(passwordHash))
    .update(payload)
    .digest("base64url")
}

export function createMasterSessionToken(passwordHash: string): string {
  const payload: MasterSessionPayload = {
    sub: "master",
    exp: Math.floor(Date.now() / 1000) + SESSION_SECONDS,
  }
  const encoded = Buffer.from(JSON.stringify(payload)).toString("base64url")
  return `${encoded}.${signature(encoded, passwordHash)}`
}

export async function validateMasterSession(request: NextRequest): Promise<boolean> {
  const token = request.cookies.get(MASTER_SESSION_COOKIE)?.value
  if (!token) return false

  const [encoded, suppliedSignature, extra] = token.split(".")
  if (!encoded || !suppliedSignature || extra) return false

  const masterSnap = await getAdminFirestore().collection("adminmaster").doc("master").get()
  const masterData = masterSnap.data()
  const passwordHash = typeof masterData?.senhaHash === "string" ? masterData.senhaHash : ""
  if (!masterSnap.exists || !passwordHash || masterData?.ativo === false) return false

  const expectedSignature = signature(encoded, passwordHash)
  const suppliedBuffer = Buffer.from(suppliedSignature)
  const expectedBuffer = Buffer.from(expectedSignature)
  if (
    suppliedBuffer.length !== expectedBuffer.length ||
    !timingSafeEqual(suppliedBuffer, expectedBuffer)
  ) {
    return false
  }

  try {
    const payload = JSON.parse(Buffer.from(encoded, "base64url").toString("utf8")) as MasterSessionPayload
    return payload.sub === "master" && payload.exp > Math.floor(Date.now() / 1000)
  } catch {
    return false
  }
}

export async function requireMasterSession(request: NextRequest): Promise<NextResponse | null> {
  const valid = await validateMasterSession(request).catch(() => false)
  return valid
    ? null
    : NextResponse.json(
        { success: false, error: "Sessão Master inválida ou expirada" },
        { status: 401 }
      )
}

export const masterSessionCookieOptions = {
  httpOnly: true,
  secure: process.env.NODE_ENV === "production",
  sameSite: "strict" as const,
  path: "/",
  maxAge: SESSION_SECONDS,
}
