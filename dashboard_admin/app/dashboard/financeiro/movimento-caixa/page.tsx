"use client"

import { AlertCircle, DollarSign } from "lucide-react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"

export default function MovimentoCaixaPage() {
  return (
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Movimento de Caixa</h1>
          <p className="text-muted-foreground">
            Esta area nao exibe mais movimentacoes locais de exemplo.
          </p>
        </div>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <DollarSign className="h-5 w-5" />
              Sem dados reais conectados
            </CardTitle>
            <CardDescription>
              A colecao `transactions` esta vazia ou ainda nao foi ligada a esta tela.
            </CardDescription>
          </CardHeader>
          <CardContent className="text-sm text-muted-foreground">
            As entradas, saidas, totais e acoes de cadastro foram removidos porque eram puramente ficticios.
          </CardContent>
        </Card>

        <Card className="border-amber-200 bg-amber-50">
          <CardContent className="flex items-start gap-3 p-6 text-sm text-amber-900">
            <AlertCircle className="mt-0.5 h-5 w-5 text-amber-700" />
            <p>
              Quando houver transacoes reais persistidas, esta pagina deve voltar a listar apenas o fluxo de caixa real.
            </p>
          </CardContent>
        </Card>
      </div>
  )
}
