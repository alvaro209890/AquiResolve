"use client"

import { useState } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Search, Download, RefreshCw, AlertCircle, Loader2 } from "lucide-react"
import { usePagarmeCharges } from "@/hooks/use-pagarme"
import { PagarmeService } from "@/lib/services/pagarme-service"
import { useToast } from "@/hooks/use-toast"
import { usePermissions } from "@/hooks/use-permissions"

export function TransactionsTable() {
  const [searchTerm, setSearchTerm] = useState("")
  const [statusFilter, setStatusFilter] = useState<string>("all")
  const [paymentMethodFilter, setPaymentMethodFilter] = useState<string>("all")
  const { toast } = useToast()
  const { hasPermission } = usePermissions()
  const canOperateFinance = hasPermission("operarFinanceiro")

  const {
    charges,
    loading,
    error,
    warning,
    refetch,
    refundCharge,
    cancelCharge,
  } = usePagarmeCharges({ autoRefresh: true })

  const filteredCharges = charges.filter((charge) => {
    const searchLower = searchTerm.toLowerCase()
    const matchesSearch =
      charge.id.toLowerCase().includes(searchLower) ||
      (charge.code && charge.code.toLowerCase().includes(searchLower)) ||
      charge.customer.name.toLowerCase().includes(searchLower)
    const matchesStatus = statusFilter === "all" || charge.status === statusFilter
    const matchesPaymentMethod = paymentMethodFilter === "all" || charge.payment_method === paymentMethodFilter

    return matchesSearch && matchesStatus && matchesPaymentMethod
  })

  const getStatusBadge = (status: string) => {
    switch (status) {
      case "pending":
        return <Badge className="bg-orange-100 text-orange-800">Pendente</Badge>
      case "paid":
        return <Badge className="bg-green-100 text-green-800">Pago</Badge>
      case "failed":
        return <Badge className="bg-red-100 text-red-800">Falhou</Badge>
      case "refunded":
        return <Badge className="bg-purple-100 text-purple-800">Reembolsado</Badge>
      case "processing":
        return <Badge className="bg-blue-100 text-blue-800">Processando</Badge>
      case "canceled":
        return <Badge className="bg-muted text-muted-foreground">Cancelado</Badge>
      default:
        return <Badge className="bg-muted text-muted-foreground">{status}</Badge>
    }
  }

  const getPaymentMethodBadge = (method: string) => {
    const colors = {
      pix: "bg-blue-100 text-blue-800",
      credit_card: "bg-green-100 text-green-800",
      debit_card: "bg-orange-100 text-orange-800",
      boleto: "bg-purple-100 text-purple-800",
      voucher: "bg-indigo-100 text-indigo-800",
    }

    const labels = {
      pix: "PIX",
      credit_card: "Cartao de Credito",
      debit_card: "Cartao de Debito",
      boleto: "Boleto",
      voucher: "Voucher",
    }

    return <Badge className={colors[method as keyof typeof colors] || "bg-muted text-muted-foreground"}>{labels[method as keyof typeof labels] || method}</Badge>
  }

  const handleRefund = async (chargeId: string) => {
    const result = await refundCharge(chargeId)
    if (result.success) {
      toast({
        title: "Reembolso solicitado",
        description: "A cobranca foi atualizada com sucesso.",
      })
      return
    }

    toast({
      title: "Erro ao processar reembolso",
      description: result.error || "Falha ao atualizar a cobranca.",
      variant: "destructive",
    })
  }

  const handleCancel = async (chargeId: string) => {
    const result = await cancelCharge(chargeId)
    if (result.success) {
      toast({
        title: "Cobranca cancelada",
        description: "A cobranca foi cancelada com sucesso.",
      })
      return
    }

    toast({
      title: "Erro ao cancelar cobranca",
      description: result.error || "Falha ao cancelar a cobranca.",
      variant: "destructive",
    })
  }

  const totalAmount = filteredCharges.reduce((sum, charge) => sum + charge.amount, 0)
  const totalPaid = filteredCharges
    .filter((charge) => charge.status === "paid")
    .reduce((sum, charge) => sum + (charge.paid_amount || charge.amount), 0)

  if (loading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Transacoes</CardTitle>
          <CardDescription>Carregando transacoes...</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-center py-8">
            <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
          </div>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <div>
            <CardTitle>Transacoes do Pagar.me</CardTitle>
            <CardDescription>
              {filteredCharges.length} de {charges.length} cobrancas - Total: {PagarmeService.formatCurrency(PagarmeService.fromCents(totalAmount))} - Pago: {PagarmeService.formatCurrency(PagarmeService.fromCents(totalPaid))}
            </CardDescription>
          </div>
          <Button onClick={() => void refetch()} variant="outline" size="sm">
            <RefreshCw className="h-4 w-4 mr-2" />
            Atualizar
          </Button>
        </div>
      </CardHeader>
      <CardContent>
        {error ? (
          <div className="mb-6 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>
        ) : null}

        {warning ? (
          <div className="mb-6 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">{warning}</div>
        ) : null}

        <div className="flex flex-col lg:flex-row gap-4 mb-6">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="Buscar por ID, codigo ou cliente..."
              value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
              className="pl-20"
            />
          </div>
          <Select value={statusFilter} onValueChange={setStatusFilter}>
            <SelectTrigger className="w-full lg:w-48">
              <SelectValue placeholder="Status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">Todos os Status</SelectItem>
              <SelectItem value="pending">Pendente</SelectItem>
              <SelectItem value="paid">Pago</SelectItem>
              <SelectItem value="failed">Falhou</SelectItem>
              <SelectItem value="refunded">Reembolsado</SelectItem>
              <SelectItem value="processing">Processando</SelectItem>
              <SelectItem value="canceled">Cancelado</SelectItem>
            </SelectContent>
          </Select>
          <Select value={paymentMethodFilter} onValueChange={setPaymentMethodFilter}>
            <SelectTrigger className="w-full lg:w-48">
              <SelectValue placeholder="Metodo" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">Todos os Metodos</SelectItem>
              <SelectItem value="pix">PIX</SelectItem>
              <SelectItem value="credit_card">Cartao de Credito</SelectItem>
              <SelectItem value="debit_card">Cartao de Debito</SelectItem>
              <SelectItem value="boleto">Boleto</SelectItem>
            </SelectContent>
          </Select>
          <Button variant="outline" className="flex items-center gap-2 bg-transparent" disabled>
            <Download className="h-4 w-4" />
            Exportar
          </Button>
        </div>

        <div className="rounded-md border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>ID Cobranca</TableHead>
                <TableHead>Cliente</TableHead>
                <TableHead>Valor</TableHead>
                <TableHead>Pago</TableHead>
                <TableHead>Metodo</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Data</TableHead>
                <TableHead className="text-right">Acoes</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filteredCharges.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={8} className="text-center py-8 text-muted-foreground">
                    {warning || "Nenhuma transacao real encontrada"}
                  </TableCell>
                </TableRow>
              ) : (
                filteredCharges.map((charge) => (
                  <TableRow key={charge.id}>
                    <TableCell>
                      <div>
                        <div className="font-medium font-mono text-xs">{charge.id.substring(0, 12)}...</div>
                        {charge.code ? <div className="text-sm text-muted-foreground">{charge.code}</div> : null}
                      </div>
                    </TableCell>
                    <TableCell>
                      <div className="font-medium">{charge.customer.name}</div>
                      <div className="text-xs text-muted-foreground">{charge.customer.email}</div>
                    </TableCell>
                    <TableCell>
                      <div className="font-medium">{PagarmeService.formatCurrency(PagarmeService.fromCents(charge.amount))}</div>
                    </TableCell>
                    <TableCell>
                      <div className="text-sm text-green-600 font-medium">
                        {charge.paid_amount ? PagarmeService.formatCurrency(PagarmeService.fromCents(charge.paid_amount)) : "-"}
                      </div>
                    </TableCell>
                    <TableCell>{getPaymentMethodBadge(charge.payment_method)}</TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        {getStatusBadge(charge.status)}
                        {charge.status === "failed" ? <AlertCircle className="h-4 w-4 text-red-500" /> : null}
                      </div>
                    </TableCell>
                    <TableCell>
                      <div className="text-sm">{new Date(charge.created_at).toLocaleDateString("pt-BR")}</div>
                      <div className="text-xs text-muted-foreground">{new Date(charge.created_at).toLocaleTimeString("pt-BR")}</div>
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-2">
                        {canOperateFinance && charge.status === "paid" ? (
                          <Button variant="ghost" size="sm" onClick={() => void handleRefund(charge.id)} title="Reembolsar">
                            <RefreshCw className="h-4 w-4" />
                          </Button>
                        ) : null}
                        {canOperateFinance && (charge.status === "pending" || charge.status === "processing") ? (
                          <Button variant="ghost" size="sm" onClick={() => void handleCancel(charge.id)} title="Cancelar">
                            <AlertCircle className="h-4 w-4" />
                          </Button>
                        ) : null}
                      </div>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>
      </CardContent>
    </Card>
  )
}
