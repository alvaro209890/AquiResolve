"use client"

import { useEffect } from "react"
import { usePathname, useRouter } from "next/navigation"
import { AlertTriangle, Loader2, LogOut, ShieldX } from "lucide-react"
import { Button } from "@/components/ui/button"
import { useAuth } from "@/components/auth-provider"
import { usePermissions } from "@/hooks/use-permissions"
import { permissionForPath } from "@/lib/admin-permissions"

export function AdminAccessGuard({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()
  const router = useRouter()
  const { user, loading: authLoading, logout } = useAuth()
  const {
    loading: permissionsLoading,
    error,
    hasPermission,
    firstAllowedPath,
    refreshPermissions,
  } = usePermissions()

  const requiredPermission = permissionForPath(pathname)
  const allowed = !requiredPermission || hasPermission(requiredPermission)

  useEffect(() => {
    if (!authLoading && !user) router.replace("/")
  }, [authLoading, router, user])

  useEffect(() => {
    if (
      !authLoading &&
      !permissionsLoading &&
      user &&
      pathname === "/dashboard" &&
      !allowed &&
      firstAllowedPath &&
      firstAllowedPath !== pathname
    ) {
      router.replace(firstAllowedPath)
    }
  }, [allowed, authLoading, firstAllowedPath, pathname, permissionsLoading, router, user])

  if (authLoading || permissionsLoading) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center gap-3 text-muted-foreground">
        <Loader2 className="h-5 w-5 animate-spin" />
        Validando acesso…
      </div>
    )
  }

  if (!user) return null

  if (error || !allowed) {
    return (
      <div className="mx-auto flex min-h-[55vh] max-w-xl items-center justify-center p-6">
        <div className="w-full rounded-xl border bg-card p-8 text-center shadow-sm">
          {error ? (
            <AlertTriangle className="mx-auto mb-4 h-10 w-10 text-destructive" />
          ) : (
            <ShieldX className="mx-auto mb-4 h-10 w-10 text-amber-600" />
          )}
          <h1 className="text-xl font-semibold">
            {error ? "Acesso administrativo não validado" : "Você não tem permissão para esta área"}
          </h1>
          <p className="mt-2 text-sm text-muted-foreground">
            {error || "Solicite ao Administrador Master a autorização necessária."}
          </p>
          <div className="mt-6 flex flex-wrap justify-center gap-2">
            {error && (
              <Button variant="outline" onClick={() => void refreshPermissions()}>
                Tentar novamente
              </Button>
            )}
            {firstAllowedPath && firstAllowedPath !== pathname && (
              <Button onClick={() => router.push(firstAllowedPath)}>Ir para minha área</Button>
            )}
            <Button
              variant="ghost"
              onClick={async () => {
                await logout()
                router.replace("/")
              }}
            >
              <LogOut className="mr-2 h-4 w-4" /> Sair
            </Button>
          </div>
        </div>
      </div>
    )
  }

  return <>{children}</>
}
