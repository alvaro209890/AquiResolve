"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select"
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { PageWithBack } from "@/components/layout/page-with-back"
import { Download, RefreshCw, FileText, FileSpreadsheet, FileDown, Database } from "lucide-react"
import { getCollection } from "@/lib/firestore"

type Row = Record<string, any>

interface Column {
  key: string
  label: string
  /** Extrai o valor textual da linha (já formatado para exibição/exportação). */
  get: (row: Row) => string
}

function tsToDate(value: any): Date | null {
  const d = value?.toDate?.() ?? (value ? new Date(value) : null)
  return d && Number.isFinite(d.getTime?.() ?? NaN) ? d : null
}

function fmtDate(value: any): string {
  const d = tsToDate(value)
  return d ? d.toLocaleDateString("pt-BR") : ""
}

function fmtMoney(value: any): string {
  const n = Number(value)
  if (!Number.isFinite(n) || n === 0) return ""
  return n.toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const STATUS_LABEL: Record<string, string> = {
  completed: "Concluído", in_progress: "Em atendimento", assigned: "Atribuído",
  distributing: "Em distribuição", cancelled: "Cancelado", pending: "Pendente",
  awaiting_payment: "Aguardando pagamento",
}

interface Dataset {
  key: string
  label: string
  collection: string
  columns: Column[]
  /** Filtro opcional aplicado após buscar a coleção. */
  filter?: (row: Row) => boolean
}

const DATASETS: Dataset[] = [
  {
    key: "orders",
    label: "Pedidos",
    collection: "orders",
    columns: [
      { key: "protocol", label: "Protocolo", get: (r) => String(r.protocol ?? r.id ?? "") },
      { key: "client", label: "Cliente", get: (r) => String(r.clientName ?? "") },
      { key: "service", label: "Serviço", get: (r) => String(r.serviceName ?? r.serviceType ?? "") },
      { key: "provider", label: "Prestador", get: (r) => String(r.assignedProviderName ?? r.providerName ?? "") },
      { key: "status", label: "Status", get: (r) => STATUS_LABEL[String(r.status)] ?? String(r.status ?? "") },
      { key: "value", label: "Valor (R$)", get: (r) => fmtMoney(r.finalPrice ?? r.estimatedPrice) },
      { key: "commission", label: "Comissão (R$)", get: (r) => fmtMoney(r.providerCommission) },
      { key: "createdAt", label: "Criado em", get: (r) => fmtDate(r.createdAt) },
    ],
  },
  {
    key: "clients",
    label: "Clientes",
    collection: "users",
    filter: (r) => {
      const t = String(r.userType ?? r.role ?? "").toLowerCase()
      return t === "client" || t === "cliente" || t === ""
    },
    columns: [
      { key: "name", label: "Nome", get: (r) => String(r.name ?? r.nome ?? r.displayName ?? "") },
      { key: "email", label: "E-mail", get: (r) => String(r.email ?? "") },
      { key: "phone", label: "Telefone", get: (r) => String(r.phone ?? r.telefone ?? r.phoneNumber ?? "") },
      { key: "blocked", label: "Bloqueado", get: (r) => (r.blocked === true ? "Sim" : "Não") },
      { key: "createdAt", label: "Cadastro", get: (r) => fmtDate(r.createdAt) },
    ],
  },
  {
    key: "providers",
    label: "Prestadores",
    collection: "providers",
    columns: [
      { key: "name", label: "Nome", get: (r) => String(r.nome ?? r.name ?? r.fullName ?? "") },
      { key: "email", label: "E-mail", get: (r) => String(r.email ?? "") },
      { key: "phone", label: "Telefone", get: (r) => String(r.telefone ?? r.phone ?? "") },
      { key: "status", label: "Status", get: (r) => String(r.status ?? "") },
      { key: "verification", label: "Verificação", get: (r) => String(r.verificationStatus ?? "") },
      { key: "orders", label: "Serviços", get: (r) => String(r.totalServicos ?? r.totalOrders ?? 0) },
      { key: "rating", label: "Avaliação", get: (r) => String(r.avaliacao ?? r.rating ?? 0) },
      { key: "blocked", label: "Bloqueado", get: (r) => (r.blocked === true ? "Sim" : "Não") },
    ],
  },
]

export default function ExportadorPage() {
  const [datasetKey, setDatasetKey] = useState<string>("orders")
  const [rows, setRows] = useState<Row[]>([])
  const [loading, setLoading] = useState(false)

  const dataset = useMemo(() => DATASETS.find((d) => d.key === datasetKey)!, [datasetKey])

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const raw = await getCollection(dataset.collection)
      setRows(dataset.filter ? raw.filter(dataset.filter) : raw)
    } finally {
      setLoading(false)
    }
  }, [dataset])

  useEffect(() => { load() }, [load])

  const fileName = (ext: string) =>
    `${dataset.key}-aquiresolve-${new Date().toISOString().slice(0, 10)}.${ext}`

  const triggerDownload = (blob: Blob, name: string) => {
    const url = URL.createObjectURL(blob)
    const link = document.createElement("a")
    link.href = url
    link.setAttribute("download", name)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  }

  const matrix = useMemo(
    () => rows.map((row) => dataset.columns.map((col) => col.get(row))),
    [rows, dataset]
  )

  const exportCsv = () => {
    const header = dataset.columns.map((c) => c.label).join(",")
    const body = matrix
      .map((cells) => cells.map((c) => `"${c.replace(/"/g, '""')}"`).join(","))
      .join("\n")
    triggerDownload(new Blob(["﻿" + header + "\n" + body], { type: "text/csv;charset=utf-8;" }), fileName("csv"))
  }

  const exportExcel = () => {
    const head = dataset.columns.map((c) => `<th>${c.label}</th>`).join("")
    const bodyRows = matrix
      .map((cells) => `<tr>${cells.map((c) => `<td>${c}</td>`).join("")}</tr>`)
      .join("")
    const html = `<html xmlns:x="urn:schemas-microsoft-com:office:excel"><head><meta charset="utf-8"/></head><body><table border="1"><thead><tr>${head}</tr></thead><tbody>${bodyRows}</tbody></table></body></html>`
    triggerDownload(new Blob(["﻿" + html], { type: "application/vnd.ms-excel;charset=utf-8;" }), fileName("xls"))
  }

  const exportPdf = async () => {
    const { jsPDF } = await import("jspdf")
    const doc = new jsPDF({ orientation: "landscape" })
    const pageWidth = doc.internal.pageSize.getWidth()
    doc.setFontSize(15)
    doc.setTextColor(27, 94, 32)
    doc.text(`AquiResolve — ${dataset.label}`, 14, 16)
    doc.setFontSize(9)
    doc.setTextColor(120)
    doc.text(`Gerado em ${new Date().toLocaleString("pt-BR")} · ${rows.length} registro(s)`, 14, 22)

    const cols = dataset.columns
    const colWidth = (pageWidth - 28) / cols.length
    let y = 32
    doc.setFontSize(8)
    doc.setTextColor(13, 71, 161)
    cols.forEach((c, i) => doc.text(c.label, 14 + i * colWidth, y))
    doc.setDrawColor(220)
    doc.line(14, y + 2, pageWidth - 14, y + 2)
    y += 7
    doc.setTextColor(0)
    matrix.forEach((cells) => {
      if (y > 195) { doc.addPage(); y = 20 }
      cells.forEach((cell, i) => {
        const text = cell.length > 22 ? cell.slice(0, 21) + "…" : cell
        doc.text(text, 14 + i * colWidth, y)
      })
      y += 6
    })
    doc.save(fileName("pdf"))
  }

  return (
    <PageWithBack backButtonLabel="Voltar">
      <div className="space-y-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-3">
            <div className="h-9 w-9 rounded-lg bg-primary/10 flex items-center justify-center">
              <Database className="h-5 w-5 text-primary" />
            </div>
            <div>
              <h1 className="text-2xl font-bold tracking-tight text-foreground">Central de Exportação</h1>
              <p className="text-sm text-muted-foreground">Exporte dados reais do Firestore em PDF, Excel ou CSV</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <Select value={datasetKey} onValueChange={setDatasetKey}>
              <SelectTrigger className="w-44">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {DATASETS.map((d) => (
                  <SelectItem key={d.key} value={d.key}>{d.label}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button variant="outline" size="sm" onClick={load} disabled={loading}>
              <RefreshCw className={`mr-1.5 h-4 w-4 ${loading ? "animate-spin" : ""}`} />
              Atualizar
            </Button>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button disabled={loading || rows.length === 0}>
                  <Download className="mr-2 h-4 w-4" />
                  Exportar
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem onClick={exportPdf}>
                  <FileText className="mr-2 h-4 w-4 text-red-600" /> PDF
                </DropdownMenuItem>
                <DropdownMenuItem onClick={exportExcel}>
                  <FileSpreadsheet className="mr-2 h-4 w-4 text-emerald-600" /> Excel (.xls)
                </DropdownMenuItem>
                <DropdownMenuItem onClick={exportCsv}>
                  <FileDown className="mr-2 h-4 w-4 text-blue-600" /> CSV
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>

        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle>{dataset.label}</CardTitle>
                <CardDescription>{rows.length} registro(s) na coleção <code className="text-xs bg-muted px-1 py-0.5 rounded">{dataset.collection}</code></CardDescription>
              </div>
              <Badge variant="outline">{dataset.columns.length} colunas</Badge>
            </div>
          </CardHeader>
          <CardContent>
            {loading ? (
              <div className="py-12 text-center text-sm text-muted-foreground">Carregando dados...</div>
            ) : rows.length === 0 ? (
              <div className="py-12 text-center text-sm text-muted-foreground">Nenhum registro encontrado nesta coleção.</div>
            ) : (
              <div className="rounded-md border overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      {dataset.columns.map((c) => (
                        <TableHead key={c.key}>{c.label}</TableHead>
                      ))}
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {matrix.slice(0, 100).map((cells, i) => (
                      <TableRow key={i}>
                        {cells.map((cell, j) => (
                          <TableCell key={j} className="whitespace-nowrap text-sm">{cell || "—"}</TableCell>
                        ))}
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
                {rows.length > 100 ? (
                  <p className="p-3 text-xs text-muted-foreground border-t">
                    Exibindo 100 de {rows.length} registros — a exportação inclui todos.
                  </p>
                ) : null}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </PageWithBack>
  )
}
