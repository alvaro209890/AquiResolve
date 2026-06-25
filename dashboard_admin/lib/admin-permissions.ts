export const ADMIN_PERMISSION_KEYS = [
  "dashboard",
  "controle",
  "gestaoUsuarios",
  "gestaoPedidos",
  "financeiro",
  "relatorios",
  "configuracoes",
  "visualizarBanners",
  "criarBanners",
  "editarBanners",
  "publicarBanners",
  "excluirBanners",
  "gerenciarCatalogo",
  "gerenciarCombos",
  "gerenciarParceiros",
  "gerenciarAquicash",
  "gerenciarGuincho",
  "enviarNotificacoes",
  "aprovarPrestadores",
  "administrarUsuarios",
  "operarPedidos",
  "operarFinanceiro",
  "gerenciarAdministradores",
] as const

export type AdminPermission = (typeof ADMIN_PERMISSION_KEYS)[number]
export type AdminPermissions = Record<AdminPermission, boolean>

export interface PermissionDefinition {
  key: AdminPermission
  label: string
  description: string
}

export interface PermissionGroup {
  id: string
  label: string
  permissions: PermissionDefinition[]
}

export const PERMISSION_GROUPS: PermissionGroup[] = [
  {
    id: "modulos",
    label: "Acesso aos módulos",
    permissions: [
      { key: "dashboard", label: "Dashboard", description: "Consultar indicadores gerais." },
      { key: "controle", label: "Operação e chats", description: "Acessar centrais operacionais e conversas." },
      { key: "gestaoUsuarios", label: "Consultar usuários", description: "Visualizar clientes e prestadores." },
      { key: "gestaoPedidos", label: "Consultar pedidos", description: "Visualizar pedidos e ordens de serviço." },
      { key: "financeiro", label: "Consultar financeiro", description: "Visualizar dados financeiros e pagamentos." },
      { key: "relatorios", label: "Relatórios", description: "Acessar relatórios e análises." },
      { key: "configuracoes", label: "Configurações", description: "Acessar a área geral de configurações." },
    ],
  },
  {
    id: "banners",
    label: "Banners da Home",
    permissions: [
      { key: "visualizarBanners", label: "Visualizar banners", description: "Consultar banners e seus destinos." },
      { key: "criarBanners", label: "Criar banners", description: "Criar novos banners como rascunho." },
      { key: "editarBanners", label: "Editar banners", description: "Alterar imagem, texto, destino e ordem." },
      { key: "publicarBanners", label: "Publicar banners", description: "Ativar ou desativar banners no aplicativo." },
      { key: "excluirBanners", label: "Excluir banners", description: "Remover banners definitivamente." },
    ],
  },
  {
    id: "conteudo",
    label: "Conteúdo e ofertas",
    permissions: [
      { key: "gerenciarCatalogo", label: "Gerenciar catálogo", description: "Alterar nichos, serviços, preços e repasses." },
      { key: "gerenciarCombos", label: "Gerenciar combos", description: "Criar e publicar combos promocionais." },
      { key: "gerenciarParceiros", label: "Gerenciar parceiros", description: "Criar e publicar parceiros da Home." },
      { key: "gerenciarAquicash", label: "Gerenciar AquiCash", description: "Alterar cashback e campanhas." },
      { key: "gerenciarGuincho", label: "Configurar guincho", description: "Alterar precificação e repasse do guincho." },
    ],
  },
  {
    id: "acoes",
    label: "Ações sensíveis",
    permissions: [
      { key: "enviarNotificacoes", label: "Enviar notificações", description: "Enviar comunicados para clientes e prestadores." },
      { key: "aprovarPrestadores", label: "Aprovar prestadores", description: "Analisar cadastros, documentos e especialidades." },
      { key: "administrarUsuarios", label: "Alterar usuários", description: "Bloquear, editar ou excluir usuários." },
      { key: "operarPedidos", label: "Alterar pedidos", description: "Atribuir, redirecionar ou cancelar pedidos." },
      { key: "operarFinanceiro", label: "Operar financeiro", description: "Criar lançamentos, pagar, liquidar e reembolsar valores." },
      { key: "gerenciarAdministradores", label: "Acessar Área Master", description: "Exibir a entrada; alterações ainda exigem uma sessão Master." },
    ],
  },
]

const EMPTY_PERMISSIONS = Object.fromEntries(
  ADMIN_PERMISSION_KEYS.map((key) => [key, false])
) as AdminPermissions

export const FULL_ADMIN_PERMISSIONS = Object.fromEntries(
  ADMIN_PERMISSION_KEYS.map((key) => [key, true])
) as AdminPermissions

const LEGACY_INHERITANCE: Partial<Record<AdminPermission, AdminPermission[]>> = {
  visualizarBanners: ["configuracoes"],
  criarBanners: ["configuracoes"],
  editarBanners: ["configuracoes"],
  publicarBanners: ["configuracoes"],
  excluirBanners: ["configuracoes"],
  gerenciarCatalogo: ["configuracoes", "dashboard"],
  gerenciarCombos: ["configuracoes", "dashboard"],
  gerenciarParceiros: ["configuracoes"],
  gerenciarAquicash: ["configuracoes", "financeiro"],
  gerenciarGuincho: ["configuracoes"],
  enviarNotificacoes: ["controle"],
  aprovarPrestadores: ["controle"],
  administrarUsuarios: ["gestaoUsuarios"],
  operarPedidos: ["gestaoPedidos"],
  operarFinanceiro: ["financeiro"],
  // Não é herdada de gestaoUsuarios: administrar outros admins exige concessão explícita.
  gerenciarAdministradores: [],
}

export function normalizeAdminPermissions(
  value: unknown,
  options: { master?: boolean; inheritLegacy?: boolean } = {}
): AdminPermissions {
  if (options.master) return { ...FULL_ADMIN_PERMISSIONS }

  const source = value && typeof value === "object"
    ? value as Record<string, unknown>
    : {}
  const result = { ...EMPTY_PERMISSIONS }

  for (const key of ADMIN_PERMISSION_KEYS) {
    if (typeof source[key] === "boolean") {
      result[key] = source[key] as boolean
      continue
    }

    if (options.inheritLegacy !== false) {
      const inheritedFrom = LEGACY_INHERITANCE[key] ?? []
      result[key] = inheritedFrom.some((legacyKey) => source[legacyKey] === true)
    }
  }

  // Qualquer ação sobre banners precisa permitir a consulta da lista.
  if (result.criarBanners || result.editarBanners || result.publicarBanners || result.excluirBanners) {
    result.visualizarBanners = true
  }

  return result
}

export function hasAdminPermission(
  permissions: AdminPermissions,
  permission: AdminPermission
): boolean {
  return permissions[permission] === true
}

export function hasAnyAdminPermission(
  permissions: AdminPermissions,
  required: readonly AdminPermission[]
): boolean {
  return required.some((permission) => hasAdminPermission(permissions, permission))
}

const PATH_PERMISSIONS: Array<{ prefix: string; permission: AdminPermission }> = [
  { prefix: "/master", permission: "gerenciarAdministradores" },
  { prefix: "/dashboard/configuracoes/banners", permission: "visualizarBanners" },
  { prefix: "/dashboard/configuracoes/parceiros", permission: "gerenciarParceiros" },
  { prefix: "/dashboard/configuracoes/aquicash", permission: "gerenciarAquicash" },
  { prefix: "/dashboard/configuracoes/guincho", permission: "gerenciarGuincho" },
  { prefix: "/dashboard/servicos/catalogo-app", permission: "gerenciarCatalogo" },
  { prefix: "/dashboard/servicos/combos", permission: "gerenciarCombos" },
  { prefix: "/dashboard/servicos/checklists", permission: "gestaoPedidos" },
  { prefix: "/dashboard/servicos/visualizar", permission: "gestaoPedidos" },
  { prefix: "/dashboard/servicos/os", permission: "gestaoPedidos" },
  { prefix: "/dashboard/controle/monitoramento", permission: "gestaoPedidos" },
  { prefix: "/dashboard/controle/notificacoes", permission: "enviarNotificacoes" },
  { prefix: "/controle/notificacoes", permission: "enviarNotificacoes" },
  { prefix: "/dashboard/controle/aceitacao-prestadores", permission: "aprovarPrestadores" },
  { prefix: "/dashboard/controle/especialidades", permission: "aprovarPrestadores" },
  { prefix: "/users", permission: "gestaoUsuarios" },
  { prefix: "/orders", permission: "gestaoPedidos" },
  { prefix: "/reports", permission: "relatorios" },
  { prefix: "/financial", permission: "financeiro" },
  { prefix: "/dashboard/financeiro", permission: "financeiro" },
  { prefix: "/dashboard/controle", permission: "controle" },
  { prefix: "/dashboard/configuracoes", permission: "configuracoes" },
  { prefix: "/dashboard/servicos", permission: "dashboard" },
  { prefix: "/configuracoes", permission: "configuracoes" },
  { prefix: "/settings", permission: "configuracoes" },
  { prefix: "/controle", permission: "controle" },
  { prefix: "/servicos", permission: "dashboard" },
  { prefix: "/dashboard", permission: "dashboard" },
]

export function permissionForPath(pathname: string): AdminPermission | null {
  return PATH_PERMISSIONS.find(({ prefix }) =>
    pathname === prefix || pathname.startsWith(`${prefix}/`)
  )?.permission ?? null
}

const LANDING_PATHS: Array<{ permission: AdminPermission; path: string }> = [
  { permission: "dashboard", path: "/dashboard" },
  { permission: "controle", path: "/dashboard/controle/chat-operacional" },
  { permission: "gestaoPedidos", path: "/orders" },
  { permission: "gestaoUsuarios", path: "/users/clients" },
  { permission: "financeiro", path: "/dashboard/financeiro" },
  { permission: "relatorios", path: "/reports" },
  { permission: "visualizarBanners", path: "/dashboard/configuracoes/banners" },
  { permission: "gerenciarCatalogo", path: "/dashboard/servicos/catalogo-app" },
  { permission: "gerenciarCombos", path: "/dashboard/servicos/combos" },
  { permission: "gerenciarParceiros", path: "/dashboard/configuracoes/parceiros" },
  { permission: "gerenciarAquicash", path: "/dashboard/configuracoes/aquicash" },
]

export function firstAllowedPath(permissions: AdminPermissions): string | null {
  return LANDING_PATHS.find(({ permission }) => permissions[permission])?.path ?? null
}
