import { NextRequest, NextResponse } from "next/server"
import {
  adminAuthorizationResponse,
  authorizeAdminRequest,
} from "@/lib/server/admin-authorization"

export async function GET(request: NextRequest) {
  try {
    const actor = await authorizeAdminRequest(request)
    return NextResponse.json({ success: true, actor })
  } catch (error: unknown) {
    return adminAuthorizationResponse(error) ?? NextResponse.json(
      { success: false, error: "Não foi possível carregar as permissões" },
      { status: 500 }
    )
  }
}
