import { NextResponse } from "next/server"
import { MASTER_SESSION_COOKIE } from "@/lib/server/master-session"

export async function POST() {
  const response = NextResponse.json({ success: true })
  response.cookies.set(MASTER_SESSION_COOKIE, "", {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "strict",
    path: "/",
    maxAge: 0,
  })
  return response
}
