"use client"

import { ChevronRight, Home } from "lucide-react"
import Link from "next/link"
import { usePathname } from "next/navigation"
import { cn } from "@/lib/utils"

interface BreadcrumbItem {
  label: string
  href?: string
  icon?: React.ComponentType<{ className?: string }>
}

interface BreadcrumbsProps {
  items?: BreadcrumbItem[]
  className?: string
}

export function Breadcrumbs({ items, className }: BreadcrumbsProps) {
  const pathname = usePathname()
  
  // Gerar breadcrumbs automaticamente baseado na rota
  const generateBreadcrumbs = (): BreadcrumbItem[] => {
    if (items) return items
    
    const pathSegments = pathname.split('/').filter(Boolean)
    const breadcrumbs: BreadcrumbItem[] = [
      { label: 'Dashboard', href: '/dashboard', icon: Home }
    ]
    
    let currentPath = ''
    
    pathSegments.forEach((segment, index) => {
      currentPath += `/${segment}`
      const isLast = index === pathSegments.length - 1
      
      // Mapear segmentos para labels mais amigáveis
      const labelMap: Record<string, string> = {
        'servicos': 'Serviços',
        'catalogo-app': 'Catálogo do App',
        'controle': 'Controle',
        'estoque': 'Estoque',
        'frota': 'Frota',
        'users': 'Usuários',
        'clients': 'Clientes',
        'providers': 'Prestadores',
        'classificacao-prestadores': 'Classificação de Prestadores',
        'verifications': 'Verificações',
        'orders': 'Pedidos',
        'pending': 'Pendentes',
        'active': 'Ativos',
        'completed': 'Concluídos',
        'chat': 'Monitoramento de Chat',
        'aceitacao-prestadores': 'Aceitação de Prestadores',
        'financial': 'Financeiro',
        'financeiro': 'Financeiro',
        'contas': 'Contas',
        'faturamento': 'Faturamento',
        'movimento-caixa': 'Movimento de Caixa',
        'folha-pagamento': 'Folha de Pagamento',
        'fechamento': 'Fechamento',
        'relatorios': 'Relatórios',
        'reports': 'Relatórios',
        'analytics': 'Analytics',
        'performance': 'Performance',
        'satisfaction': 'Satisfação',
        'geographic': 'Geográfico',
        'response-time': 'Tempo de Atendimento',
        'settings': 'Configurações',
        'configuracoes': 'Configurações',
        'checklists': 'Checklists',
        'checklist-viatura': 'Checklist Viatura',
        'clientes-fornecedores': 'Clientes e Fornecedores',
        'exportador': 'Exportador',
        'filiais': 'Filiais',
        'manutencao': 'Manutenção',
        'sistema': 'Sistema'
      }
      
      const label = labelMap[segment] || segment.charAt(0).toUpperCase() + segment.slice(1)
      
      breadcrumbs.push({
        label,
        href: isLast ? undefined : currentPath
      })
    })
    
    return breadcrumbs
  }
  
  const breadcrumbs = generateBreadcrumbs()
  
  return (
    <nav className={cn("flex items-center space-x-1 text-sm text-muted-foreground", className)} aria-label="Breadcrumb">
      {breadcrumbs.map((item, index) => {
        const isLast = index === breadcrumbs.length - 1
        const Icon = item.icon
        
        return (
          <div key={index} className="flex items-center">
            {index > 0 && (
              <ChevronRight className="h-4 w-4 mx-1 text-muted-foreground/60" />
            )}
            
            {isLast ? (
              <span className="flex items-center gap-1 font-medium text-foreground">
                {Icon && <Icon className="h-4 w-4" />}
                {item.label}
              </span>
            ) : (
              <Link
                href={item.href!}
                className="flex items-center gap-1 hover:text-foreground transition-colors"
              >
                {Icon && <Icon className="h-4 w-4" />}
                {item.label}
              </Link>
            )}
          </div>
        )
      })}
    </nav>
  )
}

