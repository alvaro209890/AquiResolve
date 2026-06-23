import { AdminAccessGuard } from "@/components/auth/admin-access-guard"

export default function ReportsLayout({ children }: { children: React.ReactNode }) {
  return <AdminAccessGuard>{children}</AdminAccessGuard>
}
