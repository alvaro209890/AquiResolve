import { AdminAccessGuard } from "@/components/auth/admin-access-guard"

export default function ControleLayout({ children }: { children: React.ReactNode }) {
  return <AdminAccessGuard>{children}</AdminAccessGuard>
}
