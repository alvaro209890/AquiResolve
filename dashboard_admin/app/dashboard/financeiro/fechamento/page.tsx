"use client"

import { AlertCircle, Calendar } from "lucide-react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"

export default function FechamentoPage() {
  return (
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Fechamento</h1>
          <p className="text-muted-foreground">
            Os periodos de fechamento locais foram removidos desta tela.
          </p>
        </div>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Calendar className="h-5 w-5" />
              Fechamentos indisponiveis
            </CardTitle>
            <CardDescription>
              Nenhum processo de fechamento real foi conectado a esta rota.
            </CardDescription>
          </CardHeader>
          <CardContent className="text-sm text-muted-foreground">
            Historico mensal, lucro e acao de fechamento foram ocultados para nao simular operacoes financeiras.
          </CardContent>
        </Card>

        <Card className="border-amber-200 bg-amber-50">
          <CardContent className="flex items-start gap-3 p-6 text-sm text-amber-900">
            <AlertCircle className="mt-0.5 h-5 w-5 text-amber-700" />
            <p>
              Esta pagina deve ser reativada apenas quando houver fonte real de receitas, despesas e fechamento contábil.
            </p>
          </CardContent>
        </Card>
      </div>
  )
}
