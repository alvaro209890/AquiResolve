import assert from "node:assert/strict"
import {
  firstAllowedPath,
  normalizeAdminPermissions,
  permissionForPath,
} from "../lib/admin-permissions"

const empty = normalizeAdminPermissions({}, { inheritLegacy: false })
assert.equal(Object.values(empty).some(Boolean), false, "perfil vazio deve negar tudo")

const legacyConfigAdmin = normalizeAdminPermissions({ configuracoes: true })
assert.equal(legacyConfigAdmin.criarBanners, true, "configuração antiga deve herdar banners")
assert.equal(legacyConfigAdmin.gerenciarParceiros, true, "configuração antiga deve herdar parceiros")
assert.equal(
  legacyConfigAdmin.gerenciarAdministradores,
  false,
  "gestão de administradores deve exigir concessão explícita"
)

const explicitDeny = normalizeAdminPermissions({ configuracoes: true, excluirBanners: false })
assert.equal(explicitDeny.excluirBanners, false, "negação granular deve prevalecer sobre legado")

const bannerCreator = normalizeAdminPermissions(
  { criarBanners: true, visualizarBanners: false },
  { inheritLegacy: false }
)
assert.equal(bannerCreator.visualizarBanners, true, "ação de banner deve habilitar sua consulta")
assert.equal(firstAllowedPath(bannerCreator), "/dashboard/configuracoes/banners")

assert.equal(
  permissionForPath("/dashboard/configuracoes/banners"),
  "visualizarBanners"
)
assert.equal(
  permissionForPath("/dashboard/servicos/catalogo-app/novo"),
  "gerenciarCatalogo"
)

console.log("admin-permissions: testes concluídos")
