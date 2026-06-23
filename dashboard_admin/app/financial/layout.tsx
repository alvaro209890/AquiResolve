import { AdminAccessGuard } from "@/components/auth/admin-access-guard"

export default function FinancialLayout({ children }: { children: React.ReactNode }) {
  return <AdminAccessGuard>{children}</AdminAccessGuard>
}
