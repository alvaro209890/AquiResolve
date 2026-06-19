"use client"

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Settings, UserCheck, Database, Shield, CheckCircle, AlertTriangle, Award, Truck } from "lucide-react"
import { SyncPanel } from "@/components/financial/sync-panel"

export default function ConfiguracoesPage() {
  return (
    <div className="space-y-6 animate-fade-in">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="h-9 w-9 rounded-lg bg-primary/10 flex items-center justify-center">
            <Settings className="h-5 w-5 text-primary" />
          </div>
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-foreground">
              Configurações do Sistema
            </h1>
            <p className="text-sm text-muted-foreground">
              Gerencie configurações e sincronização de dados
            </p>
          </div>
        </div>
      </div>

      {/* Painel de Sincronização */}
      <SyncPanel />

      {/* Cards de Navegação */}
      <div className="grid gap-6 md:grid-cols-2">
        <Card className="shadow-card hover:shadow-card-hover transition-shadow cursor-pointer">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-foreground">
              <Settings className="h-5 w-5" />
              Configurações Gerais
            </CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground">
              Configurações principais do sistema e parâmetros gerais
            </p>
            <div className="mt-4">
              <Button variant="ghost" size="sm">
                Gerenciar →
              </Button>
            </div>
          </CardContent>
        </Card>

        <Card className="shadow-card hover:shadow-card-hover transition-shadow cursor-pointer">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-foreground">
              <UserCheck className="h-5 w-5" />
              Equipes
            </CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground">
              Gestão de equipes e responsabilidades
            </p>
            <div className="mt-4">
              <Button variant="ghost" size="sm" asChild>
                <a href="/dashboard/configuracoes/equipes">Ver equipes →</a>
              </Button>
            </div>
          </CardContent>
        </Card>

        <Card className="shadow-card hover:shadow-card-hover transition-shadow cursor-pointer border-yellow-200">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-foreground">
              <Award className="h-5 w-5 text-yellow-600" />
              Programa AquiCash
            </CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground">
              Configure taxas de cashback, fases e descontos do app mobile (Bronze/Prata/Ouro)
            </p>
            <div className="mt-4">
              <Button variant="ghost" size="sm" asChild>
                <a href="/dashboard/configuracoes/aquicash">Configurar AquiCash →</a>
              </Button>
            </div>
          </CardContent>
        </Card>

        <Card className="shadow-card hover:shadow-card-hover transition-shadow cursor-pointer border-amber-200">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-foreground">
              <Truck className="h-5 w-5 text-amber-600" />
              Guincho
            </CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground">
              Taxa de saída, valor por km e repasse ao motorista (preço por trajeto origem → destino)
            </p>
            <div className="mt-4">
              <Button variant="ghost" size="sm" asChild>
                <a href="/dashboard/configuracoes/guincho">Configurar Guincho →</a>
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Status do Sistema */}
      <Card>
        <CardHeader>
          <CardTitle className="text-foreground">Status do Sistema</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="flex items-center justify-between p-4 bg-green-50 rounded-lg">
              <div className="flex items-center gap-3">
                <div className="p-2 bg-green-100 rounded-full">
                  <CheckCircle className="h-5 w-5 text-green-600" />
                </div>
                <div>
                  <p className="font-medium text-green-800">Sistema Online</p>
                  <p className="text-sm text-green-600">Funcionando normalmente</p>
                </div>
              </div>
            </div>
            
            <div className="flex items-center justify-between p-4 bg-blue-50 rounded-lg">
              <div className="flex items-center gap-3">
                <div className="p-2 bg-blue-100 rounded-full">
                  <Database className="h-5 w-5 text-blue-600" />
                </div>
                <div>
                  <p className="font-medium text-blue-800">Firebase</p>
                  <p className="text-sm text-blue-600">Conectado</p>
                </div>
              </div>
            </div>
            
            <div className="flex items-center justify-between p-4 bg-green-50 rounded-lg">
              <div className="flex items-center gap-3">
                <div className="p-2 bg-green-100 rounded-full">
                  <Shield className="h-5 w-5 text-green-600" />
                </div>
                <div>
                  <p className="font-medium text-green-800">Pagar.me</p>
                  <p className="text-sm text-green-600">Integrado</p>
                </div>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
