import { auth } from "@/lib/firebase"

export async function adminFetch(
  input: RequestInfo | URL,
  init: RequestInit = {}
): Promise<Response> {
  const user = auth.currentUser
  if (!user) {
    throw new Error("Sessão administrativa expirada")
  }

  const token = await user.getIdToken()
  const headers = new Headers(init.headers)
  headers.set("Authorization", `Bearer ${token}`)

  return fetch(input, { ...init, headers })
}
