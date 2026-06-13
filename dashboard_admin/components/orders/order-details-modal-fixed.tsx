"use client"

import { useEffect, useState, type MouseEvent } from "react"
import { createPortal } from "react-dom"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { updateDoc, doc } from "firebase/firestore"
import { db } from "@/lib/firebase"
import { toast } from "sonner"
import { ServiceOperationalPanel } from "@/components/orders/service-operational-panel"
import { ServiceChecklistPanel } from "@/components/orders/service-checklist-panel"
import { ServiceValidationPanel } from "@/components/orders/service-validation-panel"
import { resolveOperationalStatus, OPERATIONAL_STATUS_LABELS, OPERATIONAL_STATUS_BADGE_CLASS } from "@/lib/orders/operational"
import {
  X,
  User,
  Mail,
  MapPin,
  Calendar,
  Package,
  CheckCircle,
  Clock,
  Truck,
  XCircle,
  Activity,
  ClipboardList,
  KeyRound,
} from "lucide-react"

interface OrderDetailsModalProps {
  order: any
  isOpen: boolean
  onClose: () => void
  onOrderUpdated?: () => void
}

const statusOptions = [
  {
    value: "pending",
    label: "Pendente",
    icon: <Clock className="h-4 w-4" />
  },
  {
    value: "in_progress",
    label: "Em Andamento",
    icon: <Truck className="h-4 w-4" />
  },
  {
    value: "completed",
    label: "Concluído",
    icon: <CheckCircle className="h-4 w-4" />
  },
  {
    value: "cancelled",
    label: "Cancelado",
    icon: <XCircle className="h-4 w-4" />
  }
]

export function OrderDetailsModalFixed({ order, isOpen, onClose, onOrderUpdated }: OrderDetailsModalProps) {
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!isOpen || !order) return

    const previousBodyOverflow = document.body.style.overflow
    const previousHtmlOverflow = document.documentElement.style.overflow

    document.body.style.overflow = "hidden"
    document.documentElement.style.overflow = "hidden"

    return () => {
      document.body.style.overflow = previousBodyOverflow
      document.documentElement.style.overflow = previousHtmlOverflow
    }
  }, [isOpen, order])

  useEffect(() => {
    if (!isOpen || !order) return

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onClose()
      }
    }

    window.addEventListener("keydown", handleKeyDown)
    return () => window.removeEventListener("keydown", handleKeyDown)
  }, [isOpen, order, onClose])

  if (!isOpen || !order) return null

  const formattedCreatedAt = (() => {
    if (!order?.createdAt) return "N/A"

    try {
      const parsedDate = order.createdAt.toDate ? order.createdAt.toDate() : new Date(order.createdAt)
      if (Number.isNaN(parsedDate.getTime())) return "N/A"
      return parsedDate.toLocaleString("pt-BR")
    } catch {
      return "N/A"
    }
  })()

  const selectedStatus = statusOptions.some((status) => status.value === order.status) ? order.status : "pending"
  const statusOption = statusOptions.find((status) => status.value === selectedStatus)
  const shortOrderId = order?.id ? String(order.id).slice(-8) : "N/A"

  const handleBackdropClick = (event: MouseEvent<HTMLDivElement>) => {
    if (event.target === event.currentTarget) {
      onClose()
    }
  }

  const handleStatusChange = async (newStatus: string) => {
    if (!order?.id || newStatus === order.status) return

    setLoading(true)
    try {
      const orderRef = doc(db, 'orders', order.id)
      const updateData: any = {
        status: newStatus,
        updatedAt: new Date()
      }

      // Adicionar campos específicos baseado no status
      if (newStatus === "cancelled") {
        updateData.cancelledAt = new Date()
        updateData.cancelledBy = "admin"
      } else if (newStatus === "in_progress") {
        updateData.distributionStartedAt = new Date()
      } else if (newStatus === "completed") {
        updateData.completedAt = new Date()
        updateData.completedBy = "admin"
      }

      await updateDoc(orderRef, updateData)

      toast.success("Status atualizado com sucesso!")
      if (onOrderUpdated) {
        onOrderUpdated()
      }
    } catch (error) {
      console.error("Erro ao atualizar status:", error)
      toast.error("Erro ao atualizar status do pedido")
    } finally {
      setLoading(false)
    }
  }

  const modal = (
    <div
      className="fixed inset-0 z-[9999] bg-black/70 backdrop-blur-sm flex items-center justify-center p-4"
      onClick={handleBackdropClick}
    >
      <div className="w-full max-w-4xl max-h-[90vh] flex flex-col rounded-xl border border-border bg-card shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-border bg-muted/50 p-6">
          <div className="space-y-1">
            <h2 className="text-xl font-bold text-foreground">Detalhes do Serviço</h2>
            <p className="text-sm text-muted-foreground">Cliente: {order.clientName || "N/A"}</p>
          </div>
          <Button variant="ghost" size="sm" onClick={onClose} className="hover:bg-muted">
            <X className="h-4 w-4" />
          </Button>
        </div>

        {/* Content com abas */}
        <div className="flex-1 min-h-0 overflow-y-auto">
          <Tabs defaultValue="detalhes" className="w-full">
            <div className="border-b px-6 pt-2">
              <TabsList className="h-auto bg-transparent p-0 gap-1">
                <TabsTrigger
                  value="detalhes"
                  className="flex items-center gap-1.5 rounded-none border-b-2 border-transparent px-3 py-2.5 text-sm font-medium data-[state=active]:border-blue-600 data-[state=active]:text-blue-700 data-[state=active]:bg-transparent"
                >
                  <Package className="h-4 w-4" />
                  Detalhes
                </TabsTrigger>
                <TabsTrigger
                  value="operacional"
                  className="flex items-center gap-1.5 rounded-none border-b-2 border-transparent px-3 py-2.5 text-sm font-medium data-[state=active]:border-blue-600 data-[state=active]:text-blue-700 data-[state=active]:bg-transparent"
                >
                  <Activity className="h-4 w-4" />
                  Operacional
                </TabsTrigger>
                <TabsTrigger
                  value="validacao"
                  className="flex items-center gap-1.5 rounded-none border-b-2 border-transparent px-3 py-2.5 text-sm font-medium data-[state=active]:border-blue-600 data-[state=active]:text-blue-700 data-[state=active]:bg-transparent"
                >
                  <KeyRound className="h-4 w-4" />
                  Validação
                </TabsTrigger>
                <TabsTrigger
                  value="checklist"
                  className="flex items-center gap-1.5 rounded-none border-b-2 border-transparent px-3 py-2.5 text-sm font-medium data-[state=active]:border-blue-600 data-[state=active]:text-blue-700 data-[state=active]:bg-transparent"
                >
                  <ClipboardList className="h-4 w-4" />
                  Checklist
                </TabsTrigger>
              </TabsList>
            </div>

            {/* Aba Detalhes */}
            <TabsContent value="detalhes" className="p-6 mt-0">
              <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
                <Card className="border-border shadow-card">
                  <CardHeader className="bg-primary/5 border-b border-border pb-4">
                    <CardTitle className="flex items-center gap-2 text-lg text-foreground">
                      <Package className="h-5 w-5 text-primary" />
                      Detalhes do Serviço
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-5">
                    <div className="grid grid-cols-2 gap-3">
                      <div className="rounded-lg border border-border bg-muted/50 p-3">
                        <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">Protocolo</p>
                        <p className="mt-1 font-mono text-sm text-foreground">{shortOrderId}</p>
                      </div>
                      <div className="rounded-lg border border-border bg-muted/50 p-3">
                        <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">Status Operacional</p>
                        <div className="mt-1">
                          {(() => {
                            const opStatus = resolveOperationalStatus(order)
                            return (
                              <Badge variant="outline" className={OPERATIONAL_STATUS_BADGE_CLASS[opStatus]}>
                                {OPERATIONAL_STATUS_LABELS[opStatus]}
                              </Badge>
                            )
                          })()}
                        </div>
                      </div>
                    </div>

                    <div className="space-y-2">
                      <p className="text-sm font-semibold text-foreground">Status legado</p>
                      <Select
                        value={selectedStatus}
                        onValueChange={handleStatusChange}
                        disabled={loading}
                      >
                        <SelectTrigger className="w-full">
                          <SelectValue>
                            <div className="flex items-center gap-2">
                              {statusOption?.icon}
                              <span>{statusOption?.label || "Pendente"}</span>
                            </div>
                          </SelectValue>
                        </SelectTrigger>
                        <SelectContent className="z-[9999]" style={{ zIndex: 9999 }}>
                          {statusOptions.map((status) => (
                            <SelectItem key={status.value} value={status.value}>
                              <div className="flex items-center gap-2">
                                {status.icon}
                                <span>{status.label}</span>
                              </div>
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>

                    <div className="space-y-2">
                      <p className="text-sm font-semibold text-foreground">Descrição</p>
                      <p className="min-h-24 rounded-lg border border-border bg-muted/50 p-3 text-sm leading-relaxed text-foreground">
                        {order.description || "Descrição não disponível"}
                      </p>
                    </div>
                  </CardContent>
                </Card>

                <Card className="border-border shadow-card">
                  <CardHeader className="bg-muted/50 border-b border-border pb-4">
                    <CardTitle className="flex items-center gap-2 text-lg text-foreground">
                      <User className="h-5 w-5 text-primary" />
                      Cliente e Local
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-5">
                    <div className="flex items-start gap-3 rounded-lg border border-border bg-muted/50 p-3">
                      <User className="h-4 w-4 text-muted-foreground mt-1" />
                      <div>
                        <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">Nome</p>
                        <p className="font-medium text-foreground">{order.clientName || order.cliente?.nome || "N/A"}</p>
                      </div>
                    </div>

                    <div className="flex items-start gap-3 rounded-lg border border-border bg-muted/50 p-3">
                      <Mail className="h-4 w-4 text-muted-foreground mt-1" />
                      <div>
                        <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">Email</p>
                        <p className="font-medium text-foreground">{order.clientEmail || order.cliente?.email || "N/A"}</p>
                      </div>
                    </div>

                    <div className="flex items-start gap-3 rounded-lg border border-border bg-muted/50 p-3">
                      <MapPin className="h-4 w-4 text-muted-foreground mt-1" />
                      <div>
                        <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">Endereço</p>
                        <p className="font-medium text-foreground">{order.address || order.endereco?.rua || "N/A"}</p>
                        {(order.complement || order.endereco?.complemento) && (
                          <p className="text-sm text-muted-foreground">{order.complement || order.endereco?.complemento}</p>
                        )}
                      </div>
                    </div>

                    <div className="flex items-start gap-3 rounded-lg border border-border bg-muted/50 p-3">
                      <Calendar className="h-4 w-4 text-muted-foreground mt-1" />
                      <div>
                        <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">Data de criação</p>
                        <p className="font-medium text-foreground">{formattedCreatedAt}</p>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              </div>
            </TabsContent>

            {/* Aba Operacional */}
            <TabsContent value="operacional" className="p-6 mt-0">
              <ServiceOperationalPanel order={order} enabled />
            </TabsContent>

            {/* Aba Validação */}
            <TabsContent value="validacao" className="p-6 mt-0">
              <ServiceValidationPanel
                orderId={String(order.id)}
                operationalStatus={String(order.serviceOperationalStatus || order.status || "")}
              />
            </TabsContent>

            {/* Aba Checklist */}
            <TabsContent value="checklist" className="p-6 mt-0">
              <ServiceChecklistPanel orderId={String(order.id)} />
            </TabsContent>
          </Tabs>
        </div>
      </div>
    </div>
  )

  return createPortal(modal, document.body)
}
