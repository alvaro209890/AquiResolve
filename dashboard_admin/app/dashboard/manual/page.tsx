"use client"

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  BookOpen,
  LayoutDashboard,
  ClipboardList,
  Layers,
  Flame,
  MousePointer,
  Users,
  ShoppingCart,
  DollarSign,
  BarChart3,
  Settings,
  ImageIcon,
  Handshake,
  Shield,
  Database,
  Server,
  Cloud,
  Lightbulb,
} from "lucide-react"

// Manual completo do painel admin AquiResolve.
// Conteúdo estático (documentação): cada seção descreve uma área do painel, como usar e o efeito
// no app/cliente. Atualize aqui quando uma área nova for adicionada à navegação.

interface ManualItem {
  title: string
  body: string
  steps?: string[]
}

interface ManualSection {
  id: string
  icon: React.ElementType
  title: string
  intro?: string
  items: ManualItem[]
}

const SECTIONS: ManualSection[] = [
  {
    id: "visao-geral",
    icon: LayoutDashboard,
    title: "Painel (Visão Geral)",
    intro:
      "Tela inicial após o login. Resume a operação em tempo real: pedidos, faturamento, usuários e atalhos rápidos.",
    items: [
      {
        title: "O que você vê",
        body:
          "Cartões com os números-chave do dia (pedidos em aberto, concluídos, faturamento, novos cadastros) e gráficos de evolução. É o ponto de partida para acompanhar a saúde do negócio.",
      },
      {
        title: "Permissões",
        body:
          "Cada administrador só vê os menus para os quais tem permissão. As permissões são definidas na Área Master. Se um item não aparece para você, é porque seu perfil não tem acesso.",
      },
    ],
  },
  {
    id: "servicos",
    icon: ClipboardList,
    title: "Serviços",
    intro: "Tudo que define o catálogo de serviços e as promoções que o cliente vê no app.",
    items: [
      {
        title: "Visão Geral",
        body:
          "Resumo dos nichos e serviços disponíveis. Ponto de entrada para o catálogo.",
      },
      {
        title: "Catálogo do App",
        body:
          "Coleção catalog_services no Firestore: cada serviço tem nicho (categoria), nome, valor estimado e % do prestador. É a FONTE DE VERDADE do preço cobrado. Alterar aqui reflete no app sem novo APK (os serviços novos só aparecem em novos pedidos após o app recarregar o catálogo).",
        steps: [
          "Abra Serviços → Catálogo do App.",
          "Edite nicho, nome, valor estimado e comissão do prestador.",
          "Salve — o backend e o app passam a usar o novo valor.",
        ],
      },
      {
        title: "Combos Promocionais",
        body:
          "Coleção home_combos: combos 'vitrine' que agrupam serviços com foto, preço cheio riscado e economia. O desconto NÃO é uma engine nova — ao adicionar o combo ao carrinho, o app recalcula o desconto pelas categorias dos itens (PromotionManager). Por isso o painel mostra um aviso de coerência (verde = o % anunciado bate com o que o carrinho aplicará; âmbar = revisar).",
        steps: [
          "Abra Serviços → Combos Promocionais → Novo combo.",
          "Selecione 2+ serviços do catálogo (busca), defina o % e a ordem.",
          "Observe o aviso de coerência (verde/âmbar) e a foto.",
          "Salve — o combo aparece na Home do app sem novo APK.",
        ],
      },
    ],
  },
  {
    id: "controle",
    icon: MousePointer,
    title: "Controle",
    intro: "Operação ao vivo: chats, aceitação de prestadores e especialidades.",
    items: [
      {
        title: "Monitoramento de Chat",
        body: "Acompanhe as conversas entre cliente e prestador para mediar e auditar.",
      },
      {
        title: "Central Operacional",
        body: "Canal operacional para coordenar atendimentos em andamento.",
      },
      {
        title: "Chat com Clientes",
        body:
          "Mensagens diretas ao cliente (suporte/comunicados). Alimenta a coleção chatConversations que o app exibe.",
      },
      {
        title: "Aceitação de Prestadores",
        body:
          "Fila de aprovação de novos prestadores: revise documentos e libere/recuse o cadastro.",
      },
      {
        title: "Especialidades",
        body: "Gerencie as especialidades/solicitações que os prestadores podem ter.",
      },
    ],
  },
  {
    id: "usuarios",
    icon: Users,
    title: "Gestão de Usuários",
    items: [
      {
        title: "Pessoas cadastradas (clientes)",
        body: "Lista de clientes, com busca e detalhes de cada conta.",
      },
      {
        title: "Prestadores",
        body: "Lista de prestadores, status, documentos e histórico.",
      },
      {
        title: "Classificação",
        body: "Classificação/ranqueamento de prestadores por desempenho.",
      },
    ],
  },
  {
    id: "pedidos",
    icon: ShoppingCart,
    title: "Gestão de Pedidos",
    items: [
      {
        title: "Todos os Pedidos",
        body:
          "Todos os pedidos com status (criado, pago, em distribuição, em andamento, concluído, cancelado). Permite acompanhar, cancelar e disparar reembolso quando aplicável.",
      },
    ],
  },
  {
    id: "financeiro",
    icon: DollarSign,
    title: "Financeiro",
    items: [
      {
        title: "Painel Financeiro",
        body: "Visão consolidada de receitas, comissões e repasses.",
      },
      {
        title: "Pagamentos (Faturamento)",
        body:
          "Detalhe das transações Pagar.me (pagamentos LIVE), conciliação e reembolsos.",
      },
    ],
  },
  {
    id: "relatorios",
    icon: BarChart3,
    title: "Relatórios",
    items: [
      {
        title: "Central de Relatórios",
        body: "Relatórios exportáveis de pedidos, usuários e financeiro.",
      },
    ],
  },
  {
    id: "configuracoes",
    icon: Settings,
    title: "Configurações",
    intro: "Conteúdo dinâmico da Home do app e ajustes gerais.",
    items: [
      {
        title: "Geral",
        body: "Configurações gerais do app (ex.: parâmetros de cashback, guincho, etc.).",
      },
      {
        title: "Banners da Home",
        body:
          "Coleção home_banners: carrossel rotativo no topo do app. Cada banner tem imagem, título/subtítulo e ação ao tocar (abrir nicho, buscar serviço, cashback, link externo). Reflete no app sem novo APK.",
        steps: [
          "Configurações → Banners da Home → Novo banner.",
          "Envie a imagem (~1200×500), defina a ação e a ordem.",
          "Salve e ative — aparece no carrossel do app.",
        ],
      },
      {
        title: "Parceiros AquiResolve",
        body:
          "Coleção partners: patrocinadores exibidos na Home com logo e benefício (desconto, cashback, cupom ou link). Cupom mostra código copiável no app; link abre o site do parceiro. Reflete no app sem novo APK.",
        steps: [
          "Configurações → Parceiros AquiResolve → Novo parceiro.",
          "Envie o logo (fundo branco), descreva o benefício e escolha o tipo.",
          "Para tipo Cupom, informe o código; para link, a URL do site.",
          "Salve e ative — aparece na seção 'Parceiros AquiResolve' do app.",
        ],
      },
      {
        title: "Equipes",
        body: "Gestão de equipes/filiais e seus membros.",
      },
    ],
  },
  {
    id: "master",
    icon: Shield,
    title: "Área Master",
    items: [
      {
        title: "Administradores e permissões",
        body:
          "Crie administradores e defina quais menus cada um acessa (dashboard, controle, financeiro, etc.). É aqui que se controla quem vê o quê no painel.",
      },
    ],
  },
]

const CONCEPTS: ManualItem[] = [
  {
    title: "Conteúdo é dado, não código",
    body:
      "Banners, Combos e Parceiros vivem no Firestore e são editados no painel — não exigem novo APK. O app lê essas coleções e atualiza a Home sozinho. Já a estrutura visual (telas novas) exige atualizar o app.",
  },
  {
    title: "Segurança das coleções dinâmicas",
    body:
      "home_banners, home_combos e partners têm regra 'leitura se autenticado / escrita = false'. Ninguém escreve pelo app: só o painel grava, via Admin SDK (rotas /api/*). Isso impede manipulação pelo cliente.",
  },
  {
    title: "Preço é sempre do catálogo",
    body:
      "O valor cobrado vem de catalog_services + backend. Os preços que aparecem em combos são de exibição. Por isso monte combos cujos serviços, pelas categorias, disparem o desconto anunciado.",
  },
]

const INFRA: { icon: React.ElementType; title: string; body: string }[] = [
  {
    icon: Database,
    title: "Firebase (aplicativoservico-143c2)",
    body:
      "Auth, Firestore (dados), Storage (imagens) e Analytics. As regras (firestore.rules / storage.rules) ficam na raiz do projeto e são publicadas via Firebase CLI com a service account. O app e o painel compartilham o mesmo projeto.",
  },
  {
    icon: Server,
    title: "Render (backend)",
    body:
      "Backend Node em servidor Render (serviço AquiResolve, branch main, deploy manual). Cuida de pagamentos (Pagar.me), distribuição de pedidos, proxy de rotas (mapa) e regras de negócio. As variáveis ficam no painel do Render.",
  },
  {
    icon: Cloud,
    title: "Vercel (este painel)",
    body:
      "O painel admin (Next.js) é publicado na Vercel em produção (deploy manual). As variáveis de ambiente (Firebase Admin, web config, Pagar.me) ficam nas Environment Variables do projeto Vercel.",
  },
]

export default function ManualPage() {
  return (
    <div className="space-y-6 p-4 md:p-6 max-w-4xl">
      {/* Cabeçalho */}
      <div className="flex items-center gap-3">
        <div className="rounded-lg bg-indigo-100 p-2 text-indigo-700">
          <BookOpen className="h-6 w-6" />
        </div>
        <div>
          <h1 className="text-2xl font-bold">Manual do Painel</h1>
          <p className="text-sm text-muted-foreground">
            Guia completo de cada área do painel AquiResolve, o que faz e como reflete no app.
          </p>
        </div>
      </div>

      {/* Índice */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Índice</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            {SECTIONS.map((s) => (
              <a
                key={s.id}
                href={`#${s.id}`}
                className="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm text-muted-foreground hover:bg-muted hover:text-foreground"
              >
                <s.icon className="h-4 w-4 shrink-0" />
                {s.title}
              </a>
            ))}
            <a
              href="#conceitos"
              className="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm text-muted-foreground hover:bg-muted hover:text-foreground"
            >
              <Lightbulb className="h-4 w-4 shrink-0" />
              Conceitos importantes
            </a>
            <a
              href="#infra"
              className="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm text-muted-foreground hover:bg-muted hover:text-foreground"
            >
              <Cloud className="h-4 w-4 shrink-0" />
              Infraestrutura
            </a>
          </div>
        </CardContent>
      </Card>

      {/* Seções */}
      {SECTIONS.map((section) => (
        <Card key={section.id} id={section.id} className="scroll-mt-20">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-lg">
              <section.icon className="h-5 w-5 text-primary" />
              {section.title}
            </CardTitle>
            {section.intro && <p className="text-sm text-muted-foreground">{section.intro}</p>}
          </CardHeader>
          <CardContent className="space-y-4">
            {section.items.map((item) => (
              <div key={item.title} className="border-l-2 border-muted pl-4">
                <p className="font-medium">{item.title}</p>
                <p className="mt-1 text-sm text-muted-foreground">{item.body}</p>
                {item.steps && (
                  <ol className="mt-2 list-decimal space-y-1 pl-5 text-sm text-muted-foreground">
                    {item.steps.map((step, i) => (
                      <li key={i}>{step}</li>
                    ))}
                  </ol>
                )}
              </div>
            ))}
          </CardContent>
        </Card>
      ))}

      {/* Conceitos */}
      <Card id="conceitos" className="scroll-mt-20">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-lg">
            <Lightbulb className="h-5 w-5 text-amber-500" />
            Conceitos importantes
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {CONCEPTS.map((c) => (
            <div key={c.title} className="border-l-2 border-amber-200 pl-4">
              <p className="font-medium">{c.title}</p>
              <p className="mt-1 text-sm text-muted-foreground">{c.body}</p>
            </div>
          ))}
        </CardContent>
      </Card>

      {/* Infraestrutura */}
      <Card id="infra" className="scroll-mt-20">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-lg">
            <Cloud className="h-5 w-5 text-sky-500" />
            Infraestrutura
          </CardTitle>
          <p className="text-sm text-muted-foreground">
            Onde cada parte roda. As variáveis e chaves completas ficam na pasta local
            <code className="mx-1 rounded bg-muted px-1 py-0.5 text-xs">infra-config/</code>
            (na raiz do projeto, não versionada).
          </p>
        </CardHeader>
        <CardContent className="space-y-4">
          {INFRA.map((i) => (
            <div key={i.title} className="flex gap-3">
              <i.icon className="mt-0.5 h-5 w-5 shrink-0 text-sky-600" />
              <div>
                <p className="font-medium">{i.title}</p>
                <p className="mt-1 text-sm text-muted-foreground">{i.body}</p>
              </div>
            </div>
          ))}
        </CardContent>
      </Card>

      <p className="pb-4 text-center text-xs text-muted-foreground/60">
        AquiResolve Admin · Manual atualizado conforme novas áreas são adicionadas ao painel.
      </p>
    </div>
  )
}
