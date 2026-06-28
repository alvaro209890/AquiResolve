import {
  LayoutDashboard,
  ClipboardList,
  MousePointer,
  Users,
  ShoppingCart,
  DollarSign,
  BarChart3,
  Settings,
  Shield,
  Database,
  Server,
  Cloud,
} from "lucide-react"
import type { ElementType } from "react"

// Fonte única de verdade do conteúdo do Manual do Painel AquiResolve.
//
// É consumido por DOIS lugares:
//   1. app/dashboard/manual/page.tsx — renderiza a documentação navegável.
//   2. app/api/assistant/route.ts — injeta `manualAsPromptContext()` como contexto
//      do Copiloto IA (Groq), garantindo que a IA cite nomes reais de menus/botões
//      e nunca alucine telas inexistentes.
// Atualize aqui ao adicionar uma área nova ao painel.

export interface ManualItem {
  title: string
  body: string
  steps?: string[]
}

export interface ManualSection {
  id: string
  icon: ElementType
  title: string
  intro?: string
  items: ManualItem[]
}

export const SECTIONS: ManualSection[] = [
  {
    id: "visao-geral",
    icon: LayoutDashboard,
    title: "Painel (Visão Geral)",
    intro:
      "Tela inicial após o login. Resume a operação em tempo real: pedidos, faturamento, usuários e atalhos rápidos.",
    items: [
      {
        title: "KPIs principais",
        body:
          "Exibe cartões com: pedidos em aberto, pedidos concluídos no dia, faturamento do dia e dos últimos 30 dias, novos usuários cadastrados, avaliação média e total de avaliações recebidas. Esses números se atualizam ao recarregar a página.",
      },
      {
        title: "Avaliações e distribuição por estrelas",
        body:
          "Abaixo dos KPIs há o widget 'Avaliações': média geral (ex.: 4,7 ★) e barras horizontais para cada nota de 1 a 5 estrelas. As notas vêm do campo rating dos pedidos concluídos.",
      },
      {
        title: "Mapa de rastreamento ao vivo",
        body:
          "Mapa com os pinos dos prestadores ativos (localização atualizada pelo app do prestador). Clique em um pino para ver o nome, status e último update de posição.",
      },
      {
        title: "Permissões",
        body:
          "Cada administrador só vê os menus para os quais tem permissão. As permissões são definidas na Área Master (menu /master). Se um item não aparece, o perfil não tem acesso — solicite ao Administrador Master.",
      },
    ],
  },
  {
    id: "servicos",
    icon: ClipboardList,
    title: "Serviços",
    intro: "Tudo que define o catálogo de serviços, os preços e as promoções que o cliente vê no app.",
    items: [
      {
        title: "Visualizar Serviços (todos os pedidos)",
        body:
          "Lista paginada de todos os pedidos com status, prestador atribuído, valor e se foi pago. Filtros por status, prestador e período. Permite ver o detalhe da OS (checklist, fotos, assinaturas), cancelar pedido e redirecionar para outro prestador.",
        steps: [
          "Abra Serviços → Visualizar Serviços.",
          "Use os filtros para encontrar o pedido.",
          "Clique no ícone de olho para abrir o detalhe ou no ícone de cancelamento para cancelar.",
        ],
      },
      {
        title: "Catálogo do App — Nichos e Serviços",
        body:
          "Coleção catalog_services no Firestore: cada serviço tem nicho (categoria), nome, valor estimado (preço ao cliente), % de comissão e valor absoluto do prestador. É a FONTE DE VERDADE do preço cobrado. Alterar aqui reflete no app sem novo APK (mudanças de preço de serviços existentes valem na próxima vez que o app consultar o backend; novos serviços só aparecem após novo APK).",
        steps: [
          "Abra Serviços → Catálogo do App.",
          "Na aba Nichos: crie ou remova uma categoria (ex.: 'Elétrica', 'Hidráulica').",
          "Na aba Serviços: clique em Novo Serviço, selecione o nicho, defina o nome, valor e % do prestador.",
          "O painel calcula e exibe: 'cliente paga / prestador recebe / plataforma fica'.",
          "Salve — o backend passa a usar o novo valor.",
        ],
      },
      {
        title: "Combos Promocionais",
        body:
          "Coleção home_combos: combos 'vitrine' que agrupam 2+ serviços com foto, preço cheio riscado e economia estimada. Ao adicionar ao carrinho, o app recalcula o desconto pelas categorias (PromotionManager). O painel exibe aviso de coerência: verde = o desconto anunciado bate com o carrinho; âmbar = revisar. Reflete no app sem novo APK.",
        steps: [
          "Abra Serviços → Combos Promocionais → Novo combo.",
          "Selecione 2+ serviços do catálogo, defina título, foto, % promocional e ordem.",
          "Observe o aviso de coerência (verde/âmbar) antes de salvar.",
          "Ative o combo — aparece na seção 'Combos' da Home do app.",
        ],
      },
      {
        title: "Checklists de OS",
        body:
          "Consulta das Ordens de Serviço: para cada pedido em andamento/concluído você vê o checklist do prestador — fotos de antes/durante/depois, materiais usados, GPS, assinaturas e a finalização por código do cliente. Útil para auditar a execução e resolver disputas. Somente leitura (o prestador preenche pelo app).",
        steps: [
          "Abra Serviços → Checklists de OS.",
          "Busque por pedido, cliente, prestador ou serviço.",
          "Clique em 'Abrir checklist' para ver fotos, materiais e status.",
        ],
      },
    ],
  },
  {
    id: "controle",
    icon: MousePointer,
    title: "Controle",
    intro: "Operação ao vivo: monitoramento de pedidos, chats, aprovação de prestadores e logs.",
    items: [
      {
        title: "Monitoramento de Pedidos",
        body:
          "Rota: /dashboard/controle/monitoramento. Acompanha em tempo real todos os pedidos ativos via onSnapshot. Exibe KPIs (em andamento, aguardando prestador, a caminho, em atendimento, com alerta), localização ao vivo do prestador (coordenadas + link Google Maps + distância até o cliente), e detecta ociosidade: prestador parado >15 min, sem prestador >20 min em distribuição, pedido aguardando pagamento >30 min. Alerta visual (borda vermelha) e sonoro (bipe) quando um pedido entra em estado de alerta. Ações disponíveis: Reatribuir (remove o prestador atual e redistribui ou atribui a outro) e Cancelar (com motivo obrigatório).",
        steps: [
          "Abra Controle → Monitoramento de Pedidos.",
          "Pedidos com borda vermelha estão em alerta — veja o chip de diagnóstico no card.",
          "Para reatribuir: clique em 'Reatribuir', selecione o novo prestador ou 'Devolver à fila', informe o motivo e confirme.",
          "Para cancelar: clique em 'Cancelar', informe a justificativa e confirme.",
          "Para silenciar o alerta sonoro: clique no ícone de sino no topo.",
        ],
      },
      {
        title: "Central Operacional (chat entre cliente e prestador)",
        body:
          "Exibe as conversas entre clientes e prestadores em andamento. Permite ao admin ler as mensagens para mediar e auditar atendimentos.",
      },
      {
        title: "Chat com Clientes",
        body:
          "Canal direto entre a AquiResolve e cada cliente. O admin inicia ou responde conversas; o cliente lê e responde pelo app. Suporta tipos: texto, promoção, aviso e atualização de pedido. Permite também broadcast (mensagem em massa) para todos os clientes, clientes ativos ou uma lista específica.",
        steps: [
          "Abra Controle → Chat com Clientes.",
          "Clique em um cliente na lista para abrir a conversa.",
          "Digite a mensagem, selecione o tipo (Texto / Promoção / Aviso / Atualização de Pedido) e envie.",
          "Para broadcast: clique em 'Enviar em massa', escolha a audiência (Todos / Ativos / Específicos) e confirme.",
          "O badge 'não lido' some quando o cliente abre a mensagem no app.",
        ],
      },
      {
        title: "Chat com Prestadores",
        body:
          "Mesmo fluxo do Chat com Clientes, mas para prestadores. Permite iniciar uma conversa nova (botão 'Nova conversa' → seleciona o prestador na lista). O prestador recebe push FCM. O painel permite broadcast a prestadores.",
        steps: [
          "Abra Controle → Chat com Prestadores.",
          "Para iniciar conversa: clique em 'Nova conversa', busque o prestador e inicie.",
          "Para responder: clique na conversa existente, escreva e envie.",
          "Para broadcast: clique em 'Enviar em massa'.",
        ],
      },
      {
        title: "Aprovação de Prestadores",
        body:
          "Fila de novos prestadores aguardando aprovação de documentos. O admin revisa as fotos de documentos enviadas pelo prestador e aprova ou rejeita (com motivo). Aprovado: o prestador recebe push e pode receber pedidos. Rejeitado: o prestador recebe push com o motivo e pode reenviar.",
        steps: [
          "Abra Controle → Aprovação de Prestadores.",
          "Clique em um prestador pendente para abrir os documentos.",
          "Revise as imagens e clique em 'Aprovar' ou 'Rejeitar'.",
          "Para rejeitar, informe o motivo — ele é enviado ao prestador por push.",
        ],
      },
      {
        title: "Aprovação de Especialidades",
        body:
          "Fila de solicitações de alteração de especialidades dos prestadores. O prestador solicita mudança de nicho pelo app; o admin aprova ou rejeita aqui. Aprovação atualiza o campo services no documento do prestador no Firestore.",
        steps: [
          "Abra Controle → Especialidades.",
          "Veja o diff: verde = especialidade nova, tachado-vermelho = especialidade a remover.",
          "Clique em 'Aprovar' ou 'Rejeitar' (com motivo opcional).",
        ],
      },
      {
        title: "Notificações Push",
        body:
          "Dispara notificações FCM avulsas para: todos os clientes, todos os prestadores, todos os usuários ou um UID específico. Use para comunicados urgentes.",
        steps: [
          "Abra Controle → Notificações.",
          "Selecione o público-alvo, defina título e corpo da mensagem.",
          "Clique em Enviar — a notificação chega ao app em segundos.",
        ],
      },
      {
        title: "Rastreamento de Prestadores",
        body:
          "Mapa ao vivo com pinos de todos os prestadores que estão com localização ativa, mais uma lista com coordenadas e link Google Maps. Útil para dispatch manual.",
      },
      {
        title: "Logs de Auditoria",
        body:
          "Histórico de todas as ações críticas do painel: verificações de prestadores, bloqueios de usuários, cancelamentos de pedidos. Cada log registra quem fez, quando e o que mudou. Filtros por ação e tipo.",
      },
    ],
  },
  {
    id: "usuarios",
    icon: Users,
    title: "Gestão de Usuários",
    intro: "Clientes, prestadores, documentos e classificação de desempenho.",
    items: [
      {
        title: "Pessoas cadastradas (clientes)",
        body:
          "Lista de clientes com busca por nome/email/telefone, filtro por status (ativo/bloqueado). Clique em um cliente para ver o perfil completo, histórico de pedidos e ações: bloquear/desbloquear conta.",
        steps: [
          "Abra Gestão de Usuários → Pessoas.",
          "Use a busca ou os filtros para localizar o cliente.",
          "Clique no cliente para abrir o perfil detalhado.",
          "Para bloquear: clique em 'Bloquear conta' — o cliente não consegue mais fazer login.",
        ],
      },
      {
        title: "Prestadores",
        body:
          "Lista de prestadores com status de verificação (pendente, aprovado, rejeitado), especialidades, avaliação média e saldo. Permite ver documentos, ver histórico de verificações e iniciar ação de aprovação.",
      },
      {
        title: "Classificação de Prestadores",
        body:
          "Ranqueamento dos prestadores por desempenho: nota média, total de serviços, taxa de conclusão. Útil para identificar os melhores e os que precisam de suporte.",
      },
    ],
  },
  {
    id: "pedidos",
    icon: ShoppingCart,
    title: "Gestão de Pedidos",
    intro: "Todos os pedidos do marketplace, com detalhe, cancelamento e reembolso.",
    items: [
      {
        title: "Todos os Pedidos",
        body:
          "Tabela de pedidos com colunas: protocolo, cliente, serviço, valor (com selo Pago/A pagar), prestador atribuído e status. Ao clicar em um pedido, abre o modal de detalhe com: diagnóstico de saúde do pedido (pago? prestador atribuído? travado?), dados do prestador (nome, telefone, nota, saldo), localização ao vivo do prestador com link Google Maps, histórico de status.",
        steps: [
          "Abra Gestão de Pedidos → Todos os Pedidos.",
          "Filtre por status ou período para encontrar o pedido.",
          "Clique no pedido para abrir o modal de detalhe.",
          "Para cancelar: no modal, clique em 'Cancelar pedido', informe o motivo e confirme.",
          "Para reembolsar: no modal, clique em 'Reembolsar', confirme o valor e a razão.",
          "Para reatribuir: clique em 'Reatribuir prestador', escolha o novo e informe o motivo.",
        ],
      },
    ],
  },
  {
    id: "financeiro",
    icon: DollarSign,
    title: "Financeiro",
    intro: "Receitas, comissões, repasses e transações Pagar.me.",
    items: [
      {
        title: "Painel Financeiro",
        body:
          "Visão consolidada: receita total, receita dos últimos 30 dias, comissões pagas a prestadores e saldo da plataforma. KPIs atualizados com os pedidos concluídos.",
      },
      {
        title: "Faturamento (transações Pagar.me)",
        body:
          "Detalhe de cada transação: cartão ou PIX, valor, status (paid/captured/pending/refunded), ID da cobrança Pagar.me. Permite iniciar reembolso de cobranças pagas.",
        steps: [
          "Abra Financeiro → Faturamento.",
          "Localize a transação pelo ID do pedido ou período.",
          "Para reembolsar: clique em 'Reembolsar', informe valor parcial ou total e o motivo.",
          "O reembolso é executado na API do Pagar.me — o crédito retorna ao cartão/chave PIX do cliente em 1 a 15 dias úteis.",
        ],
      },
      {
        title: "Saldos de Prestadores",
        body:
          "Lista com o saldo acumulado de cada prestador (campo providerBalance). Ao concluir um pedido, o painel credita automaticamente a comissão do prestador. O repasse manual (transferência) é feito fora do painel.",
      },
      {
        title: "AquiCash (Cashback)",
        body:
          "Configuração do programa de cashback em Configurações → AquiCash. Duas fases: Launch (desconto direto no carrinho por número de serviços) e Growth (cashback por tier Bronze/Silver/Gold). Combos de categoria também concedem desconto em ambas as fases.",
        steps: [
          "Abra Configurações → AquiCash.",
          "Selecione a fase ativa (Launch ou Growth).",
          "Configure os percentuais de desconto por faixa de serviços (fase Launch) ou por tier de gasto (fase Growth).",
          "Ative combos de categorias se quiser desconto por combinação de nichos.",
          "Salve — o app aplica o novo cashback no próximo carrinho do cliente.",
        ],
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
        body:
          "Relatórios exportáveis de pedidos (por período, status, prestador), usuários (novos cadastros, ativos) e financeiro (receita, comissões). Filtros de data e exportação em planilha.",
      },
    ],
  },
  {
    id: "configuracoes",
    icon: Settings,
    title: "Configurações",
    intro: "Conteúdo dinâmico da Home do app, cashback, parceiros, banners e ajustes gerais.",
    items: [
      {
        title: "Geral",
        body:
          "Parâmetros gerais: configurações do serviço de Guincho (taxa de saída, valor por km, % do motorista), app_config e outros parâmetros globais.",
      },
      {
        title: "Banners da Home",
        body:
          "Coleção home_banners: carrossel rotativo no topo do app. Cada banner tem imagem (~1200×500), título, subtítulo, cor de fundo e ação ao tocar: abrir nicho, buscar serviço, ir ao cashback, abrir combos/parceiros ou link externo. A ordem (displayOrder) define a sequência do carrossel. Reflete no app sem novo APK.",
        steps: [
          "Abra Configurações → Banners da Home → Novo banner.",
          "Envie a imagem ou informe uma URL.",
          "Defina título, subtítulo, ação e cor de fundo.",
          "Defina a ordem (número menor = aparece primeiro).",
          "Ative e salve — aparece no carrossel do app.",
        ],
      },
      {
        title: "Banners do Prestador",
        body:
          "Coleção provider_banners: carrossel 'Aumente seus ganhos' na Home do PRESTADOR (separado dos banners do cliente). Mesma estrutura — imagem (~1200×500), título, subtítulo, cor e ação (abrir nicho, buscar serviço, link ou nenhuma; prestador NÃO tem cashback). A seção some no app quando não há banners ativos. Reflete no app sem novo APK.",
        steps: [
          "Abra Configurações → Banners do Prestador → Novo banner.",
          "Envie a imagem ou informe uma URL e preencha título/subtítulo.",
          "Escolha a ação (niche/service/url/none) e a ordem.",
          "Ative e salve — aparece no carrossel da Home do prestador.",
        ],
      },
      {
        title: "Parceiros AquiResolve",
        body:
          "Coleção partners: patrocinadores exibidos na Home com logo e benefício. Tipos de benefício: desconto (ex.: 10% off), cashback, cupom (código copiável no app) ou link (abre o site do parceiro). Reflete no app sem novo APK.",
        steps: [
          "Abra Configurações → Parceiros AquiResolve → Novo parceiro.",
          "Envie o logo (fundo branco recomendado) e opcionalmente o banner.",
          "Descreva o benefício, selecione o tipo e preencha o cupom ou URL conforme o tipo.",
          "Defina a ordem e ative — aparece na seção 'Parceiros' do app.",
        ],
      },
      {
        title: "AquiCash (Cashback)",
        body:
          "Configura as regras do programa de fidelidade. Veja a seção Financeiro → AquiCash para detalhes completos.",
      },
    ],
  },
  {
    id: "master",
    icon: Shield,
    title: "Área Master",
    intro: "Acesso via /master (separado do painel principal). Controle total de administradores.",
    items: [
      {
        title: "Login Master",
        body:
          "A Área Master usa login próprio (email e senha armazenados no Firestore em adminmaster/master). É separada do login do painel normal. Acesse pelo link 'Área Master' na sidebar ou diretamente em /master.",
      },
      {
        title: "Criar Administradores",
        body:
          "Na Área Master, crie sub-administradores com email, senha e conjunto de permissões. Cada permissão libera um grupo de menus: dashboard, controle, gestão de usuários, gestão de pedidos, financeiro, relatórios, configurações.",
        steps: [
          "Acesse /master e faça login com as credenciais Master.",
          "Clique em 'Novo Administrador'.",
          "Informe nome, email, senha e marque as permissões desejadas.",
          "Salve — o administrador já consegue logar no painel com as permissões definidas.",
        ],
      },
      {
        title: "Editar Permissões",
        body:
          "Na lista de administradores da Área Master, clique no ícone de edição para alterar as permissões de um usuário. Permissões são atualizadas em tempo real (sem novo login necessário pelo admin).",
      },
      {
        title: "Desativar / Remover Administradores",
        body:
          "Desativar suspende o acesso sem excluir o usuário. Remover exclui permanentemente a conta do Firebase Auth e o perfil do Firestore.",
      },
    ],
  },
]

export const CONCEPTS: ManualItem[] = [
  {
    title: "Conteúdo é dado, não código",
    body:
      "Banners, Combos e Parceiros vivem no Firestore e são editados no painel — não exigem novo APK. O app lê essas coleções e atualiza a Home sozinho. Já telas novas ou novos serviços no catálogo exigem atualizar o app (novo APK).",
  },
  {
    title: "Preço é sempre do catálogo",
    body:
      "O valor cobrado vem de catalog_services + backend (Render). Os preços que aparecem em combos são de exibição/curadoria. Mudar o preço de um serviço existente no Catálogo do App reflete na cobrança na hora (via backend), sem novo APK.",
  },
  {
    title: "Segurança das coleções dinâmicas",
    body:
      "home_banners, home_combos e partners têm regra 'leitura se autenticado / escrita = false'. Só o painel grava, via Admin SDK (rotas /api/*). Isso impede manipulação pelo app do cliente.",
  },
  {
    title: "Reembolso só de cobranças pagas",
    body:
      "Só cobranças com status paid ou captured no Pagar.me podem ser reembolsadas. Pedidos cancelados antes do pagamento não geram reembolso. O crédito volta ao cartão/PIX do cliente em 1 a 15 dias úteis (depende do banco).",
  },
  {
    title: "Push FCM é best-effort",
    body:
      "Notificações push funcionam quando o token FCM do destinatário está válido e o celular está online. Se o token expirou, a mensagem fica no Firestore e aparece quando o app abrir. O painel não garante entrega instantânea.",
  },
  {
    title: "Dois logins diferentes",
    body:
      "O painel admin usa Firebase Auth (signInWithEmailAndPassword). A Área Master (/master) usa login próprio (Firestore adminmaster/master). São sistemas separados — as credenciais Master não funcionam no painel normal e vice-versa.",
  },
]

export const INFRA: { icon: ElementType; title: string; body: string }[] = [
  {
    icon: Database,
    title: "Firebase (aplicativoservico-143c2)",
    body:
      "Auth (login de clientes, prestadores e admins), Firestore (dados em tempo real), Storage (imagens de documentos, banners, combos, parceiros) e Analytics. As regras de segurança ficam nos arquivos firestore.rules e storage.rules na raiz do projeto. O app e o painel compartilham o mesmo projeto Firebase.",
  },
  {
    icon: Server,
    title: "Render (backend de pagamentos)",
    body:
      "Backend Node.js no Render (serviço 'AquiResolve', branch main, autoDeploy OFF — deploy sempre manual). Cuida de: pagamentos Pagar.me (cartão e PIX), cálculo de preço do catálogo, proxy de rota para o mapa do prestador (OSRM) e IA do assistente do app (Groq). Variáveis de ambiente ficam no painel do Render.",
  },
  {
    icon: Cloud,
    title: "Vercel (este painel admin)",
    body:
      "O painel (Next.js 15) é publicado na Vercel no projeto aquiresolve-dashboard. Deploy sempre manual via CLI (npx vercel deploy --prod). NÃO há integração automática com GitHub — push no repositório não dispara deploy do painel. Variáveis de ambiente (Firebase Admin SDK, chaves Pagar.me, GROQ_API_KEY) ficam nas Environment Variables do projeto Vercel.",
  },
]

/**
 * Serializa todo o Manual em texto enxuto para o Copiloto IA.
 * Mantém seções, itens, passos, conceitos e infra — o suficiente para a IA
 * responder "onde clicar" sem inventar telas.
 */
export function manualAsPromptContext(): string {
  const lines: string[] = []

  lines.push("# MANUAL DO PAINEL ADMINISTRATIVO AQUIRESOLVE")
  lines.push("")

  for (const section of SECTIONS) {
    lines.push(`## ${section.title}`)
    if (section.intro) lines.push(section.intro)
    for (const item of section.items) {
      lines.push(`### ${item.title}`)
      lines.push(item.body)
      if (item.steps && item.steps.length > 0) {
        lines.push("Passo a passo:")
        item.steps.forEach((step, i) => lines.push(`  ${i + 1}. ${step}`))
      }
    }
    lines.push("")
  }

  lines.push("## CONCEITOS IMPORTANTES")
  for (const c of CONCEPTS) {
    lines.push(`### ${c.title}`)
    lines.push(c.body)
  }
  lines.push("")

  lines.push("## INFRAESTRUTURA")
  for (const i of INFRA) {
    lines.push(`### ${i.title}`)
    lines.push(i.body)
  }

  return lines.join("\n")
}
