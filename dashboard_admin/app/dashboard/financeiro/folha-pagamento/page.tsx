"use client"

import { PageWithBack } from "@/components/layout/page-with-back"
import { Card, CardContent } from "@/components/ui/card"
import { AlertCircle, Users } from "lucide-react"

export default function FolhaPagamentoPage() {
  return (
      <PageWithBack backButtonLabel="Voltar para Financeiro">
        <div className="space-y-6">
          <div>
            <h1 className="text-2xl font-bold text-foreground">Folha de Pagamento</h1>
            <p className="text-muted-foreground">
              A tela foi mantida sem dados ficticios. Ela so sera habilitada quando existir fonte real de folha no backend.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <Card>
              <CardContent className="p-6">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-muted-foreground">Total da Folha</p>
                    <p className="text-2xl font-bold">R$ 0,00</p>
                  </div>
                  <Users className="h-8 w-8 text-blue-600" />
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardContent className="p-6">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-muted-foreground">Total Descontos</p>
                    <p className="text-2xl font-bold text-red-600">R$ 0,00</p>
                  </div>
                  <Users className="h-8 w-8 text-red-600" />
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardContent className="p-6">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-muted-foreground">Funcionarios</p>
                    <p className="text-2xl font-bold">0</p>
                  </div>
                  <Users className="h-8 w-8 text-green-600" />
                </div>
              </CardContent>
            </Card>
          </div>

          <Card className="border-amber-200 bg-amber-50">
            <CardContent className="p-8 text-center">
              <AlertCircle className="mx-auto mb-4 h-12 w-12 text-amber-600" />
              <p className="text-lg font-semibold text-amber-900">Nenhum dado real de folha encontrado</p>
              <p className="mt-2 text-sm text-amber-800">
                Esta area estava usando dados fixos de demonstracao. Agora ela permanece vazia ate existir uma colecao ou integracao real de funcionarios e folha.
              </p>
            </CardContent>
          </Card>
        </div>
      </PageWithBack>
  )
}
