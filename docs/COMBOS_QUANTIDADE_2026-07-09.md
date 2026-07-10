# Combos com quantidade por item (ex.: "1 chuveiro + 3 tomadas")

**Data:** 2026-07-09 · **Escopo:** painel admin (UI + API) + app mobile · **Regras Firestore:** sem mudança

## Pedido (áudio do Álvaro, 2026-07-09)

> "No combo eu preciso colocar troca de um chuveiro e **três** tomadas — só que eu só
> consigo colocar **uma** tomada. É igual ao carrinho: quando preciso de três tomadas,
> vou adicionando três tomadas."

O construtor de combos (`/dashboard/servicos/combos`) só permitia cada serviço do
catálogo **uma vez** (toggle selecionado/não-selecionado). Faltava **quantidade por item**.

## O que mudou

### Modelo de dados (`home_combos.items[]`)
Cada item ganha `quantity` (inteiro **1..20**; combos antigos sem o campo valem 1 —
retrocompatível nos dois sentidos: APK antigo ignora o campo e adiciona 1 de cada).

### Painel — `/dashboard/servicos/combos` (`page.tsx`)
- Chip de cada serviço selecionado ganhou **stepper − N× +** (1..20).
- `computedFull` = Σ preço do catálogo × quantidade; label mostra **unidades** somadas.
- Validação: **2+ unidades somando quantidades** (agora vale "2× do mesmo serviço").
- Aviso de coerência (`expectedCartCombo`) recebe os nichos **repetidos pela
  quantidade** — importa para o combo "2+ automotivos".
- Lista de combos mostra `N serviços` (unidades) e nomes com prefixo `3× `.
- Ao editar combo antigo, `quantity` é normalizada para 1.

### API — `POST/GET /api/combos` (`route.ts`)
- `normalizeItems` normaliza `quantity` (`clampQuantity`: inteiro 1..20, default 1).
- Validação do POST: `totalUnits ≥ 2` (era `items.length ≥ 2`).
- GET devolve `quantity` em cada item.

### App mobile
- `HomeComboItem` (`models/HomeCombo.kt`): campo `quantity: Int = 1`.
- `ComboRepository.readItems`: lê `quantity` defensivamente (`coerceIn(1, 20)`).
- `HomeComboAdapter` (card da Home): preço cheio ao vivo = Σ preço × quantidade.
- `ComboServiceItemAdapter` (detalhe): linha exibe **"3× Instalação de tomada"** e o
  preço **total da linha** (unitário × quantidade).
- `ComboDetailActivity`: resumo soma por linha com quantidade; **"Adicionar combo ao
  carrinho"** cria **um item de carrinho por unidade** (`repeat(quantity)`) — o mesmo
  fluxo de quem adiciona o serviço N vezes à mão, então o desconto do carrinho
  (`PromotionManager`) continua funcionando sozinho; toast parcial conta unidades.

### Regras Firestore
Nenhuma mudança: `home_combos` já é `read: isSignedIn()` / `write: false`
(escrita exclusiva via Admin SDK pela rota do painel). Campo novo em payload gravado
pelo Admin SDK não passa por validação de rules.

## Validação (E2E ao vivo, Waydroid Android 11)

1. Combo de teste semeado via Admin SDK exatamente como o painel grava:
   1× "Instalação de chuveiro" (R$ 150) + **3×** "Instalação de tomada" (R$ 110),
   15% anunciado.
2. App (APK novo): card na Home; detalhe mostrou **"3× Instalação de tomada — R$ 330,00"**,
   valor cheio **R$ 480,00**, total **R$ 408,00 (−15%)**.
3. "Adicionar combo ao carrinho" → carrinho com **4 itens** (1 chuveiro + 3 tomadas,
   todos "Combo: Combo Chuveiro + 3 Tomadas"), total R$ 480,00. ✔
4. Dados de QA removidos ao final (combo + itens de carrinho; o endereço de teste
   `saved_addresses/qa-endereco-teste` do cliente de teste ficou para futuros QAs —
   gotcha: `userType` é `"CLIENT"` maiúsculo).
5. Painel: `npx tsc --noEmit` sem erros nos arquivos de combos (16 erros pré-existentes
   em outras páginas, ignorados pelo build config).

## Observações

- **Exige novo APK** para o app respeitar quantidade (modelo/adapters/detalhe são código).
  Com APK antigo, o combo aparece mas adiciona só 1 unidade de cada serviço.
- Preço continua sendo **exibição**: a cobrança real vem do carrinho→backend. O aviso
  de coerência do painel é quem previne anunciar desconto que o carrinho não aplicará
  (no E2E acima, itens só de Elétrica não ativam combo por categoria — o painel avisa).
