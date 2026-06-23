# CLAUDE.md — AquiResolve: Guia Completo para Agentes de IA

Este arquivo é lido automaticamente pelo Claude Code. Contém tudo que qualquer agente precisa saber para trabalhar neste repositório com segurança.

> **Skills de infraestrutura (use-as):** este repo traz skills em `.claude/skills/` para operar cada plataforma com os comandos certos e os gotchas já mapeados. **Prefira invocá-las** a improvisar:
> - **`aquiresolve-firebase`** — publicar `firestore.rules`/índices, semear catálogos (`service_categories`, `catalog_services`), rodar scripts Admin SDK.
> - **`aquiresolve-render`** — env vars e deploy do backend de pagamentos (autoDeploy OFF → deploy via API).
> - **`aquiresolve-vercel`** — deploy do painel e env vars (sem auto-deploy do GitHub; Node 20 obrigatório; fix do `FIREBASE_SERVICE_ACCOUNT`).
> - **`aquiresolve-emulador`** — emular e testar o APK neste PC via Waydroid (Android em container): instalar APK, abrir telas, validar UI/serviços, screenshot/extrair texto. Credenciais de teste em `.emulator-test-credentials` (gitignored).

---

## 1. Visão Geral do Projeto

**AquiResolve** é um marketplace de serviços domésticos/profissionais que conecta clientes a prestadores. Composto por três componentes:

| Componente | Tecnologia | Localização | Deploy |
|---|---|---|------|
| App Mobile | Android / Kotlin | `app/` | Google Play Store |
| Painel Admin | Next.js 15 + TypeScript | `dashboard_admin/` | Vercel (`alvaro209890s-projects`) |
| Backend Pagamentos | Node.js / Express | `backend/` | Render.com |

**Firebase Project:** `aplicativoservico-143c2`

---

## 2. Arquitetura

```
[App Android] ──Retrofit──▶ [Backend Pagamentos]  ──Pagar.me v5──▶ [Pagar.me]
       │                             │
       │                    [Firebase Admin SDK]
       │                             │
       └──────Firebase SDK──▶ [Firestore / Auth / Storage]
                                     │
                            [Firebase Admin SDK]
                                     │
                         [Painel Admin (Next.js)]
```

**Regra de ouro:** O Painel Admin **nunca** chama o Backend de Pagamentos diretamente. Toda escrita crítica (status de pedido, configurações de cashback) usa o Firebase Admin SDK no servidor Next.js.

---

## 3. Componente: App Mobile (`app/`)

### Stack
- Kotlin 1.9.22 · Compile/Target SDK 35 · Min SDK 24
- Firebase BOM 32.7.0 (Auth, Firestore, Storage, Messaging, Analytics)
- Retrofit 2.9.0 + OkHttp 4.12.0 (pagamentos)
- Glide 4.16.0 · ZXing 3.5.2 · OSMDroid 6.1.18
- Material Design 3 · Coroutines 1.7.3

### Comandos
```bash
./gradlew assembleDebug        # APK debug
./gradlew installDebug         # Instala no dispositivo
./gradlew bundleRelease        # AAB para Play Store
./gradlew lint
./gradlew test
```

### Geração de APK no GitHub Actions (CI)
Workflow `.github/workflows/build-apk.yml`. Dispara **manual** (Actions → "Build
APK" → Run workflow, com toggle de release) ou por **tag `v*`**. Restaura
`google-services.json` + keystore de **secrets**, builda `assembleDebug` (sempre)
e `assembleRelease` (assinado, quando há keystore) e publica os `.apk` como
**artifacts** (30 dias).

- Secrets (gitignored → guardados no repo): `GOOGLE_SERVICES_JSON_BASE64`,
  `UPLOAD_KEYSTORE_JKS_BASE64`, `UPLOAD_KEYSTORE_CREDENTIALS_BASE64`
  (recriar: `base64 -w0 <arquivo> | gh secret set <NOME> -R alvaro209890/AquiResolve`).
- Baixar: `gh run download <run-id> -n aquiresolve-release-apk` (ou `-debug-apk`).
- **release** ~4–6 MB (R8 + shrinkResources + assinado) = distribuir; **debug**
  ~13–15 MB = só teste. Trocar entre eles exige **desinstalar** o app antes
  (assinatura diferente). Detalhes: `docs/CORRECAO_PERMISSION_DENIED_PEDIDO_2026-06-15.md`.

### Padrão Arquitetural
```
Activity → Manager → Firebase/Retrofit
```
- **Nunca** coloque lógica de negócio em Activities
- Todos os `Manager` classes ficam em `app/src/main/java/com/aquiresolve/app/`
- Models usam `@PropertyName` do Firestore

### Coleções Firestore usadas pelo app
| Coleção | Finalidade |
|---|---|
| `users/{uid}` | Perfil do usuário (cliente ou prestador) |
| `users/{uid}/cashback_transactions` | Extrato de cashback |
| `providers/{uid}` | Perfil do prestador |
| `orders/{id}` | Pedidos de serviço |
| `checklists/{orderId}` | OS (Ordem de Serviço) |
| `chatRooms/{id}` | Salas de chat em tempo real |
| `notifications/{id}` | Notificações FCM |
| `carts/{uid}/items` | Carrinho de compras |
| `app_config/cashback` | Config do programa de cashback (só leitura) |
| `service_categories` / `service_types` | Catálogo de NICHOS (só leitura no app; escrita só Admin SDK) |
| `catalog_services` | Catálogo de SERVIÇOS (nicho + valor + % do prestador); só leitura no app, escrita só Admin SDK |
| `chatConversations/{orderId}` | Conversa consolidada p/ a Central Operacional do painel (upsert pelo app) |
| `provider_specialty_requests/{id}` | Solicitações de alteração de especialidades (prestador cria; admin aprova/rejeita via painel) |
| `client_chats/{clientId}` | Chat Base ↔ Cliente — metadata (lastMessage, unreadByClient, unreadByAdmin). Cliente lê; escrita só Admin SDK |
| `client_chats/{clientId}/messages/{id}` | Mensagens do chat. Cliente cria as próprias (senderType='client'); admin envia via Admin SDK |
| `client_chat_broadcasts/{id}` | Histórico de cada disparo em massa do admin (auditoria). Só Admin SDK |
| `home_banners/{id}` | Banners do carrossel da Home do cliente. App lê (`BannerRepository`); escrita só Admin SDK (painel) |
| `home_combos/{id}` | Combos promocionais (vitrine) da Home. App lê (`ComboRepository`); escrita só Admin SDK (painel). Preço é exibição — desconto real vem do `PromotionManager` no carrinho |
| `partners/{id}` | Parceiros patrocinadores (vitrine) da Home. App lê (`PartnerRepository`); escrita só Admin SDK (painel). Benefício: discount/cashback/coupon/link |

### Catálogo de NICHOS dinâmico (app ↔ painel)
- O app **lê** os nichos de `service_categories` via `CatalogRepository.kt` (pré-carregado no `AppApplication`, com **fallback estático** em `ServiceNicheCatalog` se o Firestore estiver vazio/offline — zero regressão).
- Cliente (`CreateOrderActivity`), prestador (`ProviderSignUpActivity`/`ProviderProfileFragment`) e o matching (`ServiceNicheCatalog.applyDynamicCatalog`/`selectableNiches`) usam esse catálogo.
- O painel gerencia os nichos na aba **Nichos** de `/dashboard/servicos/catalogo-app`, **escrevendo via `POST/DELETE /api/catalog` (Admin SDK)** — o app só lê.
- Semear/ressincronizar: `node dashboard_admin/scripts/seed-catalog.mjs` (rodar de dentro de `dashboard_admin/` com Node 22).

### Catálogo de SERVIÇOS dinâmico (nicho + valor + % do prestador) — `catalog_services`
Fonte única de verdade = painel admin → Firestore `catalog_services`. Um doc por serviço:
`{ niche, nicheSlug, name, slug, description, estimatedTime, estimatedPrice (R$ cliente), providerCommissionPercent (0–100), providerCommission (R$ absoluto = round(price*percent/100,2)), isConsult, active, displayOrder }` — id determinístico `${nicheSlug}__${slug}`.
- **Painel** (aba **Serviços** de `/dashboard/servicos/catalogo-app`): `components/catalog/catalog-services-panel.tsx` com slider de **% do prestador** e prévia ao vivo (cliente paga / prestador recebe / plataforma fica). Escreve via **`GET/POST/DELETE /api/catalog/services` (Admin SDK)** — o servidor calcula `providerCommission` a partir do %.
- **Backend** (`backend/src/services/service-pricing.service.js`): `calculateServicePricing` é **async** e lê `catalog_services` PRIMEIRO (cache 60s, nunca lança); fallback na `pricingTable` hardcoded. Como o app já chama `POST /api/payments/pricing/calculate` no checkout, **mudar o preço no painel muda a cobrança real sem novo APK**.
- **App** (`CatalogServiceRepository.kt` + `models/CatalogService.kt`): `CreateOrderActivity.setupServiceTypesForNiche` usa a lista do Firestore (fallback `hardcodedServiceTypesForNiche` offline); `getClientPriceLabel` prefere o preço do Firestore. Pedido grava `estimatedPrice`/`providerCommission` absolutos inalterados. **Novos serviços só aparecem na lista do app após novo APK** (`./gradlew assembleDebug`); mudanças de preço de serviços existentes valem na hora (via backend).
- **Comissão** continua persistida em **R$ absoluto** em pedidos/pagamento; o % é só a forma de configurar no painel (salva os dois).
- **Match exato exigido:** `catalog_services.niche` == categoria enviada pelo app; `catalog_services.name` == serviceType.
- Semear/migrar (~300 serviços da tabela hardcoded, deriva o % — drift R$0,00): `node dashboard_admin/scripts/seed-catalog-services.mjs` (de `dashboard_admin/`, Node ≥20). Remapeia "Desentupimento com maquinário" → "Desentupimento com maquinário até 2 m".

### Aprovação de especialidades do prestador

Fluxo completo de aprovação opcional (escopo do item 5 — Edição de Perfil):

1. **App** (`ProviderProfileFragment.saveServices()`): ao salvar especialidades, verifica se já há solicitação `pending`. Se não houver e as especialidades mudaram, cria um doc em `provider_specialty_requests` com `status=pending` e desabilita o botão. Não grava diretamente em `providers/{uid}.services`.
2. **Regra Firestore** (`provider_specialty_requests`): prestador pode criar a própria solicitação (providerId == auth.uid, status == 'pending'); update/delete exclusivos via Admin SDK.
3. **Painel** (`/dashboard/controle/especialidades`): fila de solicitações pendentes com diff visual (verde = novo, tachado-vermelho = removido). Aprovar → `providers/{id}.services` é atualizado + notificação FCM ao prestador. Rejeitar → motivo opcional + notificação FCM.
4. **API** (`POST /api/specialty-requests`): Admin SDK — bypassa Firestore rules.

**Gotchas:**
- O botão "Salvar Serviços" fica desabilitado enquanto houver solicitação `pending`. Reabilita somente após reload da tela (quando a solicitação foi aprovada/rejeitada pelo admin).
- `loadPendingSpecialtyRequest()` é chamado em `onViewCreated` além de `saveServices()`.
- Aprovação do admin NÃO requer novo APK — apenas atualiza `services` no Firestore; o app lê o campo a cada abertura do perfil.

### Banner Rotativo (carrossel da Home) — `home_banners`
Carrossel no topo da `ClientHomeActivity`, com banners gerenciados pelo painel (conteúdo é dado, não código → **sem novo APK** para criar/editar/desativar).
- **App:** `models/HomeBanner.kt` + `BannerRepository.kt` (espelha `CatalogRepository`: lê `home_banners`, filtra `active`, ordena `displayOrder`, cacheia, **nunca lança**; pré-aquecido no `AppApplication`). UI: `adapters/BannerAdapter.kt` + `item_home_banner.xml` no `ViewPager2`; `setupBannerCarousel()` faz auto-scroll ~4s (pausa em `onPause`/`onDestroy`, reinicia o timer a cada troca de página/swipe) e monta os dots (`banner_dot_active/inactive`). Seção `sectionBanners` nasce `GONE` → some quando não há banners. Roteamento por `actionType`: `niche`→`CreateOrderActivity` (extra `service_category_name`, valida nicho no catálogo), `service`→`ServicesActivity` (extra `search_query`), `cashback`→`CashbackActivity`, `url`→navegador, `combos`/`partners`→`ServicesActivity` (seções futuras), `none`/inválido→sem ação. Analytics `home_banner_click`.
- **Painel:** `/dashboard/configuracoes/banners` (upload p/ Storage `banner_images/` ou URL, título/subtítulo, ação, cor, ordem, ativo) → `POST/GET/DELETE /api/banners` (Admin SDK). Item na sidebar (grupo Configurações).
- **Firestore/Storage:** `home_banners` = `read: isSignedIn()` / `write: false`; `storage.rules` libera `banner_images/{fileName}` para upload autenticado (imagem ≤10MB). Sem índice composto (filtro/sort em memória).
- **Semear teste:** `node dashboard_admin/scripts/seed-banners.mjs` (3 banners: cashback/niche/service). Recomenda-se imagens ~1200×500.

### Busca Inteligente (sugestões na Home) — sem IA
Busca instantânea no campo `etSearch` da `ClientHomeActivity`: ao digitar (debounce ~250ms) aparece um dropdown (`sectionSearchSuggestions`) com serviços/nichos do catálogo real; matching textual em memória (sem Firestore por tecla, sem IA — a IA é o plano `06`).
- **App:** `models/SearchSuggestion.kt`; `ServiceSearchHelper.suggest(query, niches, services)` (normaliza acento/caixa, ranking exato>começa>contém>palavras, limite 8, complemento estático por sinônimos quando o catálogo dinâmico rende pouco); `adapters/SearchSuggestionAdapter.kt` + `item_search_suggestion.xml`. Catálogo de serviços inteiro em cache via **`CatalogServiceRepository.loadAll()`/`allCachedServices()`** (pré-aquecido no `AppApplication`).
- **Roteamento:** SERVICE → `CreateOrderActivity` com `service_category_name` + **`preselect_service`** (chave nova) + `search_query`; NICHE → só o nicho. A pré-seleção do serviço é aplicada dentro de `CreateOrderActivity.rebuildServiceTypeAdapter` (sobrevive ao rebuild assíncrono do catálogo e seta `selectedPureServiceType`, garantindo o valor no submit). Sem resultado → CTA `tvSearchEmptyCta` abre `ServicesActivity`.
- **Analytics:** `busca_sugestao_click` (label/niche/type), `busca_sem_resultado` (query). Sem coleção/painel/regra nova (100% app).

### Combos Promocionais (vitrine na Home) — `home_combos`
Seção "🔥 Combos Promocionais" na `ClientHomeActivity` (abaixo de Categorias): combos curados (foto, "de R$X por R$Y", badge de economia) gerenciados pelo painel → **sem novo APK** para criar/editar/desativar. É **vitrine + atalho**; o desconto cobrado continua sendo recalculado no carrinho pelo `PromotionManager` (não há engine nova).
- **App:** `models/HomeCombo.kt` (+ `HomeComboItem`) + `ComboRepository.kt` (espelha `BannerRepository`: lê `home_combos`, filtra `active`, ordena `displayOrder`, cacheia, **nunca lança**; pré-aquecido no `AppApplication`). UI: `adapters/HomeComboAdapter.kt` + `item_home_combo.xml` (RecyclerView horizontal em `sectionCombos`, nasce `GONE`). Detalhe: `ComboDetailActivity` + `activity_combo_detail.xml` (serviços incluídos com preço resolvido do catálogo, resumo cheio/economia/promo). "Adicionar combo ao carrinho" pede o **endereço salvo** (`FirebaseAddressManager`) e adiciona cada item via **`FirebaseCartManager.addItem`** (mesmo fluxo do `CreateOrderActivity`) → o carrinho aplica o desconto sozinho pelas categorias. Combo lido por id do cache (`ComboRepository.cachedComboById`) via extra `combo_id`, sem passar objeto por Intent.
- **Coerência de preço:** `fullPrice/promoPrice/savings/discountPercent` são **exibição/curadoria**; a cobrança vem de `catalog_services` + backend. O painel calcula `fullPrice` da soma dos preços do catálogo e **avisa** (verde/âmbar) prevendo o % que o carrinho aplicará (replicando os grupos do `PromotionManager` + percentuais de `app_config/cashback`). Validado ao vivo: Combo Casa Nova (Elétrica+Caça-vazamentos+Ar condicionado) → carrinho "Combo Elétrica+Hidráulica+Instalações **-R$ 217,50 (15%)**", total **R$ 1232,50 == anunciado**.
- **Painel:** `app/api/combos/route.ts` (GET/POST/DELETE, Admin SDK; recalcula promo/savings no servidor) + `app/dashboard/servicos/combos/page.tsx` (multi-select de itens de `catalog_services` + cálculo automático + aviso de coerência) + item na sidebar (Serviços). Seed de teste: `scripts/seed-combos.mjs`.
- **Firestore/Storage:** `home_combos` = `read: isSignedIn()` / `write: false`; `storage.rules` libera `combo_images/{fileName}` (upload autenticado ≤10MB). Sem índice composto.
- **Analytics:** `home_combo_click` (id/nome), `combo_add_cart` (id/nome/itensAdicionados).

### Parceiros AquiResolve (vitrine na Home) — `partners`
Seção "🤝 Parceiros AquiResolve" na `ClientHomeActivity` (abaixo de Combos): patrocinadores (logo + benefício) gerenciados pelo painel → **sem novo APK** para criar/editar/desativar. Conteúdo é dado, não código.
- **App:** `models/Partner.kt` (helpers `hasCoupon()`/`hasUrl()`; `benefitType` = discount/cashback/coupon/link) + `PartnerRepository.kt` (espelha `ComboRepository`: lê `partners`, filtra `active`, ordena `displayOrder`, cacheia, **nunca lança**; pré-aquecido no `AppApplication`). UI: `adapters/PartnerAdapter.kt` + `item_partner.xml` (RecyclerView horizontal em `sectionPartners`, nasce `GONE`; logo `fitCenter` em fundo branco + pill de benefício). Detalhe: `PartnerDetailActivity` + `activity_partner_detail.xml` (banner/logo, descrição, benefício; **cupom copiável** via `ClipboardManager` quando `coupon`; **"Visitar site"** via `Intent.ACTION_VIEW` quando há `url`). Parceiro lido por id do cache (`PartnerRepository.cachedPartnerById`) via extra `partner_id`.
- **Painel:** `app/api/partners/route.ts` (GET/POST/DELETE, Admin SDK; `couponCode` só persistido p/ `benefitType=coupon`) + `app/dashboard/configuracoes/parceiros/page.tsx` (form: nome, logo+banner upload p/ `partner_images/`, descrição, tipo de benefício, rótulo, cupom condicional, URL, ordem, ativo) + item na sidebar (Configurações). Seed de teste: `scripts/seed-partners.mjs`.
- **Firestore/Storage:** `partners` = `read: isSignedIn()` / `write: false`; `storage.rules` libera `partner_images/{fileName}` (upload autenticado ≤10MB). Sem índice composto.
- **Analytics:** `parceiro_click`, `parceiro_cupom_copiado`, `parceiro_link_aberto` (id/nome).
- **Validado ao vivo** (Waydroid): 3 parceiros (desconto/cupom/cashback) renderizaram; Telhanorte → "Copiar" pôs `AQUI15` no clipboard (comprovado colando no campo de busca); "Visitar site" abriu `https://www.telhanorte.com.br/`.

### Home Premium (montagem — plano 07)
A `ClientHomeActivity` foi reorganizada na **ordem Premium** (do topo): Busca inteligente → Banner rotativo → Saudação (1 linha, personalizada com o nome) → **Categorias** → **Card de cashback** → **Combos** → **Parceiros** → **Pedidos recentes** → **CTA Assistente IA** (`cardAssistant`). O botão "Ver Serviços" foi removido (redundante com categorias + bottom nav). O `NestedScrollView` (`contentScroll`) agora vive dentro de um `SwipeRefreshLayout` (`swipeRefresh`): o pull-to-refresh re-chama os `setup*`/`load*` (`setupSwipeRefresh()`). Cada seção dinâmica continua isolada (erro/vazio → `GONE`), insets preservados em `setupWindowInsets`. Mudança é de **código** → exige novo APK.

### Assistente IA (app cliente — plano 06) — `AssistantActivity`
O cliente descreve o problema em linguagem natural ("minha pia está vazando") e a IA (Groq via **proxy no backend**) identifica o **nicho** do catálogo e direciona para o pedido. A IA é conveniência: qualquer falha cai no fallback "ver todos os serviços", nunca bloqueia a contratação.
- **App:** `AssistantActivity.kt` + `activity_assistant.xml` (registrada no Manifest). `AssistantClient.kt` (OkHttp, espelha o `RouteClient`) chama `POST /api/ai/classify` com `Authorization: Bearer <ID token>` — a chave Groq **nunca** fica no APK. Envia `CatalogRepository.cachedNicheNames()`; valida o nicho retornado contra o catálogo. "Continuar" → `CreateOrderActivity` (`service_category_name`). **Acessos:** card `cardAssistant` na Home + gancho no estado "sem resultado" da busca (abre o Assistente com `EXTRA_PREFILL`).
- **Backend:** `POST /api/ai/classify` (`backend/src/routes/ai.routes.js` + `services/ai-classify.service.js`): exige ID token (`authenticateRequest`), rate-limit `aiLimiter` (15/min/IP), chama Groq (`llama-3.3-70b-versatile`, `temperature:0`, `response_format: json_object`), valida `niche ∈ niches` (anti-alucinação), nunca derruba o fluxo. **Env:** `GROQ_API_KEY` (+ opcional `GROQ_MODEL`) no Render. **autoDeploy off → deploy manual** após setar a chave.
- **Analytics:** `ia_assistente_open`, `ia_nicho_sugerido` (niche/confidence), `ia_sugestao_aceita`.
- **Status:** código pronto e compilando; **falta** `GROQ_API_KEY` no Render + deploy + teste. Passo a passo em `novas-implementacoes/09-ativacao-ia-runbook.md`.

### Copiloto IA do Painel (plano 08) — aba Manual
Widget de chat no topo de `/dashboard/manual`: o admin pergunta "como faço X?" e recebe passos com onde clicar, **fundamentado** no conteúdo real do Manual.
- **Conteúdo único:** `dashboard_admin/lib/manual-content.ts` exporta `SECTIONS/CONCEPTS/INFRA` (movidos de `page.tsx`) + `manualAsPromptContext()`. A página **renderiza** e a rota **injeta no prompt** — Manual e IA nunca divergem. Ao adicionar área nova ao painel, edite **este** arquivo.
- **Rota:** `app/api/assistant/route.ts` (POST, `runtime='nodejs'`): system prompt + grounding do Manual + histórico curto → Groq. Chave `GROQ_API_KEY` **só no servidor** (Vercel), nunca no browser. Trata erro/timeout (502/504/503).
- **Widget:** `components/manual/assistant-chat.tsx` (chat + exemplos clicáveis + estados).
- **Status:** código pronto e passando no typecheck; **falta** `GROQ_API_KEY` na Vercel + deploy + teste. Passo a passo em `novas-implementacoes/09-ativacao-ia-runbook.md`.

### Manual do Painel — `/dashboard/manual`
Aba "Manual do Painel" na sidebar (`app/dashboard/manual/page.tsx`, ícone `BookOpen`): documentação navegável (índice + âncoras) de **cada área do painel** (Painel, Serviços, Controle, Usuários, Pedidos, Financeiro, Relatórios, Configurações, Área Master), além de **conceitos** ("conteúdo é dado, não código", segurança das coleções, preço do catálogo) e **infraestrutura** (Firebase/Render/Vercel). Conteúdo estático em arrays no próprio componente — atualizar ao adicionar área nova.

### infra-config/ (referência local — NÃO versionada)
Pasta na raiz (`infra-config/`, no `.gitignore`) com **referência completa e exposta** de toda a configuração: `firebase/` (regras copiadas da raiz + `firebase-web-config.json` + `service-account.json` decodificada + `firebase-config.md`), `render/` (`render-credentials.txt` + `render.env`/`render-env-vars.json` puxados ao vivo da API + `render-service.md`), `vercel/` (`vercel.env` = cópia do `.env.local` + `vercel-config.md`). Contém segredos → **local apenas**, nunca commitar. Atualizar quando uma variável mudar na origem.

### Recuperação de senha (esqueci minha senha)
- **Tela:** `ForgotPasswordActivity` (`activity_forgot_password.xml`). Acessível de **3 lugares**:
  login (`MainActivity` → `tvForgotPassword`), cadastro de cliente (`ClientSignUpActivity`) e
  cadastro de prestador (`ProviderSignUpActivity`) — todos têm o link "Esqueci minha senha".
- **Como funciona:** usa `FirebaseAuth.sendPasswordResetEmail(email).await()` (Firebase envia o
  email de redefinição pela infra padrão do projeto — nada a configurar no Console). Login e
  cadastro de cliente passam o email já digitado via extra `prefill_email`.
- **Privacidade:** o projeto tem **proteção contra enumeração de email** ligada → a API retorna
  sucesso mesmo para email não cadastrado (não revela se existe). Por isso a mensagem é "se houver
  uma conta com este email, você receberá o link" e **não** existe erro "email não encontrado".
- **Gotchas já corrigidos (não regredir):**
  1. O envio precisa de `.await()` — sem ele, qualquer falha era engolida e o sucesso aparecia sempre.
  2. O `successLayout` fica **dentro** do `cardRecovery`; esconder o card inteiro deixava a tela
     em branco. A activity esconde só o `formLayout` (container do formulário) e mostra o
     `successLayout` — o card permanece visível.
- **Testado ao vivo no Waydroid** (ver skill `aquiresolve-emulador`): login → "Esqueci minha
  senha" → enviar → tela "Email enviado com sucesso!". Firebase confirma o envio (REST `sendOobCode`).

### Fluxo de Pedido
```
awaiting_payment → pending → distributing → assigned → in_progress → completed
                                                                   └→ cancelled
```

### Tela Financeiro do Prestador (`ProviderFinancialActivity`)
- Arquivo: `app/src/main/java/com/aquiresolve/app/ProviderFinancialActivity.kt`
- Layout: `app/src/main/res/layout/activity_provider_financial.xml`
- Lê `providerBalance` e `providerTotalEarned` de `providers/{uid}`
- Lista pedidos concluídos (`status=completed`, `assignedProvider=uid`) com comissão de cada um
- **Acesso:** botão "💰 Financeiro" na `ProviderHomeActivity` (segundo botão na linha de ação)

### Tela Status de Verificação (`ProviderVerificationStatusActivity`)
- Arquivo: `app/src/main/java/com/aquiresolve/app/ProviderVerificationStatusActivity.kt`
- Layout: `app/src/main/res/layout/activity_provider_verification_status.xml`
- Lê `verificationStatus`, `rejectionReason`, `verificationNotes` de `providers/{uid}`
- Mostra histórico de revisões de `provider_verifications` (where providerId == uid)
- **Acesso:** banner na `ProviderHomeActivity` (visível quando status é pending ou rejected)

### ProviderHomeActivity — melhorias
- Banner de verificação (pending=âmbar, rejected=vermelho, approved=oculto) com link para ProviderVerificationStatusActivity
- Campo `tvEarnings` agora lê `providerBalance` (acumulado pelo painel admin) com fallback para `totalEarnings`
- Botão "💰 Financeiro" abre ProviderFinancialActivity

### Logs de Auditoria (adminLogs)
- Coleção Firestore: `adminLogs/{id}`
- Campos: `action`, `targetId`, `targetType`, `adminId`, `payload`, `createdAt`
- Gravado automaticamente em: `PATCH /api/providers/[id]/verify`, `PATCH /api/users/[id]` (bloqueio), `PATCH /api/orders/[id]` (cancelamento)
- Leitura: `GET /api/admin-logs` com filtros `action`, `targetType`, `limit`

### Métricas de Receita no Dashboard
- `totalRevenue` — soma de `estimatedPrice` de todos os pedidos `completed`
- `revenueLast30Days` — idem, filtrado pelos últimos 30 dias
- Exibidos como dois novos KPI cards no Dashboard principal

### KPI de Avaliações no Dashboard
- Fonte: campo `orders/{id}.rating` (1..5, gravado por `FirebaseOrderManager.rateOrder`).
- Agregado em `dashboard_admin/lib/services/firestore-analytics-simple.ts`:
  - `averageRating` — média de notas dos pedidos avaliados
  - `totalRated` — quantidade de pedidos avaliados (`rating > 0`)
  - `ratingDistribution` — histograma 1..5★
- KPI cards: **Avaliação Média** (ex.: `4.7 ★`) e **Avaliações Recebidas** em `components/dashboard/dashboard-metrics.tsx`.
- Widget de distribuição (média + barra horizontal por estrela): `components/dashboard/ratings-breakdown.tsx`, plugado em `/dashboard` na seção "Avaliações" (acima do mapa de rastreamento).

### Cashback — Fase Launch (desconto direto por nº de serviços + combos)
Onde é aplicado: **no carrinho**, NÃO no `PaymentActivity` (este só recebe o total já com desconto). O caminho completo é:
1. Painel admin em `/dashboard/configuracoes/aquicash` grava `activePhase`, `directDiscount2/3/4Plus`, `combosEnabled` e `combo*` em `app_config/cashback` (Admin SDK).
2. App lê via `CashbackManager.getConfig()` e calcula o desconto com `PromotionManager.computeDiscount(niches, subtotal, config)` — escolhe o **maior** entre desconto por quantidade (apenas na fase `launch`) e o melhor combo aplicável (vale nas 2 fases).
3. `ClientCartActivity.updateSummary()` exibe linha "Subtotal / Desconto / Total" e um **hint** `tvLaunchDiscountHint` quando o cliente está perto da próxima faixa (ex.: "🎁 Adicione mais 1 serviço e ganhe 10%").
4. `FirebaseCartManager.prepareCheckout(..., discountPercent)` aplica `finalPrice = effectivePrice * (1 − pct/100)` em **cada** pedido criado e grava `cartDiscountPercent` no doc. O `chargedTotal` (somatório dos `finalPrice`) vai para `PaymentActivity.EXTRA_ORDER_AMOUNT`.
5. **Comissão do prestador (`providerCommission`) NÃO muda** — o desconto é custeado pela plataforma.
6. `CashbackManager.creditForCompletedOrder` **só credita** na fase `growth` (early-return se `isLaunchPhase`). Isso garante que as duas fases sejam mutuamente exclusivas, como na arte oficial do programa.

**Pedidos single-service** (via `CreateOrderActivity.navigateToPayment`) não passam pelo carrinho e portanto não recebem desconto — o que está correto: tanto desconto direto (≥2 serviços) quanto combos (≥2 categorias) exigem mais de 1 item.

### Chat Base ↔ Cliente (Central AquiResolve)
Canal bidirecional admin → cliente para promoções, avisos e atualizações.
Coleção: `client_chats/{clientId}` (metadata) + subcoleção `messages/*`.

**Fluxo:**
1. **Admin** envia via painel (`/dashboard/controle/chat-clientes`) — chama `POST /api/client-chats/[clientId]/messages` ou `POST /api/client-chats/broadcast`. Tudo Admin SDK; metadata (lastMessage, unreadByClient, etc.) é atualizada na mesma chamada. FCM disparado em paralelo com `type=central_message`.
2. **Cliente** abre `ClientCentralChatActivity` (botão na home, ícone de envelope com badge). `CentralChatRepository.observeMessages()` lê via snapshot listener (regra: `isOwner(clientId)`).
3. **Cliente** responde criando mensagem em `client_chats/{uid}/messages` — regras forçam `senderType='client'` e `senderId == auth.uid`.
4. **Marcar como lido** — `markReadByClient()` faz `PATCH /api/client-chats/[clientId]/read?role=client` (zera contador via Admin SDK).
5. **Badge unread no app** — `CentralChatRepository.observeUnreadByClient()` observa `client_chats/{uid}.unreadByClient` em tempo real.
6. **Broadcast** — `POST /api/client-chats/broadcast` resolve audiência (`all`/`active`/`specific`), fan-out em chunks de 200 (2 writes por destinatário, batch limit 400), grava snapshot em `client_chat_broadcasts/{id}`.

**Tipos de mensagem (campo `type`):** `text`, `promotion`, `notice`, `order_update`. Painel e app exibem badge visual diferente para cada tipo.

**URL do painel** vem de `BuildConfig.PANEL_BASE_URL` (`app/build.gradle`) — usada apenas para chamar `read` do app.

**Gotchas:**
- Cliente NÃO consegue zerar `unreadByClient` direto (regra bloqueia escrita em `client_chats/{uid}` no client SDK) — usa o endpoint do painel.
- A página do painel usa polling (8s lista, 5s mensagens) em vez de snapshot listener — admin não tem custom claim por padrão, então client SDK não passa pelas rules; API routes (Admin SDK) cobrem leitura.
- FCM é best-effort — se o token estiver expirado, a mensagem ainda fica no Firestore e aparece quando o app abrir.

### Backend de Pagamentos (Pagar.me)
- URL: `https://aquiresolve.onrender.com/api/payments/`
- Configurada em `app/build.gradle` como `PAYMENTS_API_BASE_URL`
- Endpoints usados pelo app:
  - `POST /pricing/calculate`
  - `POST /card`
  - `POST /pix`
  - `GET /{orderId}/status`

### Arquivo de configuração Firebase
`app/google-services.json` — **NÃO está no repositório** (adicionar manualmente ou via CI/CD secrets)

---

## 4. Componente: Painel Admin (`dashboard_admin/`)

### Stack
- Next.js 15.5 · React 19 · TypeScript 5
- Firebase 14 (client SDK) + Firebase Admin 13 (server SDK)
- Tailwind CSS 4 · Radix UI · TanStack Query + Table
- React Hook Form · Zod

### Comandos
```bash
cd dashboard_admin
npm install          # ou pnpm install
npm run dev          # Inicia Next.js na porta 3000
npm run build        # Build de produção
npm run start        # Serve build de produção
```

### Variáveis de Ambiente
Criar `dashboard_admin/.env.local` com (arquivo já existe na máquina local, **não vai ao GitHub**):

```
# Firebase Client SDK
NEXT_PUBLIC_FIREBASE_API_KEY=
NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=
NEXT_PUBLIC_FIREBASE_PROJECT_ID=aplicativoservico-143c2
NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET=
NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID=
NEXT_PUBLIC_FIREBASE_APP_ID=
NEXT_PUBLIC_FIREBASE_MEASUREMENT_ID=

# Firebase Admin SDK (servidor only)
FIREBASE_SERVICE_ACCOUNT={"type":"service_account",...}  # JSON em uma linha

# Google Maps
NEXT_PUBLIC_GOOGLE_MAPS_API_KEY=

# Pagar.me
API_KEY_PRIVATE_PAGARME=sk_...
API_KEY_PUBLIC_PAGARME=pk_...
```

### Autenticação do Painel Admin
O painel usa **Firebase Auth** (`signInWithEmailAndPassword`). O usuário admin deve existir como usuário Firebase Auth no projeto `aplicativoservico-143c2`.

Para criar o usuário admin master via Firebase Admin:
```js
// No Firebase Console > Authentication > Add user
email: master@aquiresolve.com
// Ou via Admin SDK:
admin.auth().createUser({ email: 'master@aquiresolve.com', password: 'suaSenha' })
```

Após criar o usuário, rodar o setup do AdminMaster (cria o documento `adminmaster/master` no Firestore):
```bash
curl -X POST https://seu-dominio.vercel.app/api/setup-adminmaster \
  -H "Content-Type: application/json" \
  -d '{"email":"master@aquiresolve.com","senha":"suaSenha","nome":"Admin Master"}'
```

### Estrutura das API Routes (Next.js)
Todas as rotas estão em `dashboard_admin/app/api/`:

| Rota | Método | Finalidade |
|---|---|---|
| `/api/health` | GET | Health check |
| `/api/auth/master-login` | POST | Login do admin (verifica `adminmaster/master`) |
| `/api/setup-adminmaster` | POST | Cria documento inicial do admin no Firestore |
| `/api/orders` | GET | Lista pedidos do Firestore |
| `/api/orders/[id]` | GET | Retorna um pedido |
| `/api/orders/[id]` | PATCH | Atualiza status de pedido (Admin SDK — bypassa regras) |
| `/api/users/[id]` | GET | Retorna dados de um usuário |
| `/api/users/[id]` | PATCH | Atualiza/bloqueia usuário (Admin SDK) |
| `/api/users/[id]` | DELETE | Bloqueia conta do usuário |
| `/api/providers` | GET | Lista prestadores via Storage |
| `/api/providers/firebase-admin` | GET | Lista prestadores via Admin SDK |
| `/api/providers/[id]/verify` | GET | Status de verificação do prestador |
| `/api/providers/[id]/verify` | PATCH | Aprova ou rejeita prestador (Admin SDK) |
| `/api/cashback-config` | GET | Lê configuração AquiCash |
| `/api/cashback-config` | POST | Salva configuração AquiCash (Admin SDK) |
| `/api/notifications/send` | POST | Envia FCM push notification por uid, userIds[], token, tokens[] ou topic |
| `/api/orders/[id]/redirect` | POST | Remove prestador do pedido e retorna para distribuição (motivo obrigatório) |
| `/api/checklists/[orderId]` | GET | Retorna checklist + dados do pedido para visualização da OS |
| `/api/catalog` | POST | Cria/atualiza NICHO do catálogo (Admin SDK — `service_categories` + `service_types`) |
| `/api/catalog` | DELETE | Remove nicho do catálogo (`?id=`) das duas coleções (Admin SDK) |
| `/api/catalog/services` | GET | Lista SERVIÇOS de `catalog_services` (opcional `?niche=`) (Admin SDK) |
| `/api/catalog/services` | POST | Cria/atualiza serviço (nicho/valor/% do prestador); calcula `providerCommission` (Admin SDK) |
| `/api/catalog/services` | DELETE | Remove serviço de `catalog_services` (`?id=`) (Admin SDK) |
| `/api/banners` | GET | Lista banners da Home de `home_banners` ordenados por `displayOrder` (Admin SDK) |
| `/api/banners` | POST | Cria/atualiza banner (normaliza `actionType`; `id` opcional) (Admin SDK) |
| `/api/banners` | DELETE | Remove banner de `home_banners` (`?id=`) (Admin SDK) |
| `/api/combos` | GET/POST/DELETE | CRUD de combos em `home_combos`; recalcula promo/savings no servidor (Admin SDK) |
| `/api/partners` | GET/POST/DELETE | CRUD de parceiros em `partners`; `couponCode` só p/ `benefitType=coupon` (Admin SDK) |
| `/api/orders/[id]/refund` | POST | Reembolsa o pagamento do pedido via Pagar.me (Admin SDK). Body `{ amount?, reason? }` |
| `/api/client-chats` | GET | Lista chats Base↔Cliente (`?status=active\|archived&unreadOnly=true`) |
| `/api/client-chats/[clientId]` | PATCH | Pin/archive do chat (`{ pinned?, archived? }`) |
| `/api/client-chats/[clientId]/messages` | GET | Mensagens do chat ordenadas por createdAt |
| `/api/client-chats/[clientId]/messages` | POST | Admin envia mensagem (Admin SDK + atualiza metadata + FCM) |
| `/api/client-chats/[clientId]/read` | PATCH | Zera contador unread (`?role=admin\|client`) |
| `/api/client-chats/broadcast` | POST | Envia em massa (audience: all\|active\|specific) |
| `/api/specialty-requests` | GET | Lista solicitações de especialidades (`?status=pending\|approved\|rejected\|all`) |
| `/api/specialty-requests` | POST | Aprova ou rejeita uma solicitação. Body `{ requestId, action: 'approve'\|'reject', rejectionReason? }`. Aprovação atualiza `providers/{id}.services` e envia notificação FCM ao prestador |
| `/api/assistant` | POST | Copiloto IA do painel (plano 08): responde "como faço X?" fundamentado no Manual (`manualAsPromptContext()`). Chave `GROQ_API_KEY` só no servidor (Vercel) |
| `/api/admin-logs` | GET | Lista logs de auditoria (filtros: action, targetType, limit) |
| `/api/admin-logs` | POST | Grava ação de auditoria (action, targetId, targetType, payload) |
| `/api/financial/providers` | GET | Saldo/ganhos dos prestadores |
| `/api/financial/transactions` | GET | Transações financeiras |
| `/api/financial/accounts` | GET | Contas financeiras |
| `/api/pagarme/*` | GET/POST | Integração Pagar.me |
| `/api/lgpd/consent` | POST | Registro de consentimento LGPD |
| `/api/lgpd/rights` | POST | Exercício de direitos LGPD |
| `/api/adminmaster/users` | GET/POST | Gestão de usuários do painel |
| `/api/reports/financial` | GET | Relatórios financeiros |

### Páginas criadas/atualizadas (sessão atual)
| Página | Rota | O que faz |
|---|---|---|
| Visualizar Serviços | `/dashboard/servicos/visualizar` | Lista pedidos reais do Firestore com paginação, filtros, redirecionamento e cancelamento |
| Detalhe OS | `/dashboard/servicos/os/[orderId]` | Exibe checklist completo: GPS, fotos antes/durante/depois, assinaturas, comissão |
| Notificações | `/dashboard/controle/notificacoes` | Envia FCM push para todos clientes, todos prestadores, todos usuários ou UID específico |
| Rastreamento | `/dashboard/controle/autem-mobile/rastreamento` | Mapa ao vivo com pinos de prestadores + lista GPS com link Google Maps |
| Aprovação de Especialidades | `/dashboard/controle/especialidades` | Fila de solicitações de alteração de especialidades dos prestadores — aprovar/rejeitar com motivo opcional; aprovação atualiza `providers/{id}.services` via Admin SDK e notifica o prestador |
| Chat com Clientes | `/dashboard/controle/chat-clientes` | Chat Base↔Cliente — lista de clientes (busca/filtros/unread badge), painel da conversa (polling 5s), modal de broadcast (audience all/active) |
| Cashback (AquiCash) | `/dashboard/configuracoes/aquicash` | Configura fases, tiers, combos e salva em `app_config/cashback` via Admin SDK |
| Banners da Home | `/dashboard/configuracoes/banners` | CRUD do carrossel da Home: upload da imagem (Storage `banner_images/`) ou URL, título/subtítulo, ação (niche/service/cashback/url/combos/partners/none), cor de fundo, ordem, ativo. Escreve em `home_banners` via `/api/banners` (Admin SDK) |
| Combos Promocionais | `/dashboard/servicos/combos` | CRUD de combos: multi-select de `catalog_services` + cálculo automático de preços + aviso de coerência de desconto. Escreve em `home_combos` via `/api/combos` (Admin SDK) |
| Parceiros AquiResolve | `/dashboard/configuracoes/parceiros` | CRUD de parceiros: logo+banner (Storage `partner_images/`), descrição, tipo de benefício, cupom condicional, URL, ordem, ativo. Escreve em `partners` via `/api/partners` (Admin SDK) |
| Manual do Painel | `/dashboard/manual` | Documentação navegável de cada área do painel + conceitos + infraestrutura (página estática) |
| Logs de Auditoria | `/dashboard/controle/logs` | Histórico de todas as ações críticas do admin (verificações, bloqueios, cancelamentos) |

### Hooks atualizados
| Hook | Mudança |
|---|---|
| `hooks/use-users.ts` | `blockUser`/`unblockUser` agora usam `PATCH /api/users/[id]` (Admin SDK) em vez de client SDK |
| `hooks/use-document-verification.ts` | `approveVerification`/`rejectVerification` usam `PATCH /api/providers/[id]/verify` |

### Como as páginas buscam dados
- **Firestore direto (client SDK):** `lib/firestore.ts` → `getCollection()`, `listenToCollection()`
- **Admin SDK (server):** via API Routes `app/api/` que usam `lib/firebase-admin.ts`
- **Hooks React:** `hooks/use-users.ts`, `hooks/use-analytics.ts`, etc.

### Serviços de biblioteca
| Arquivo | Finalidade |
|---|---|
| `lib/firebase.ts` | Init Firebase client SDK |
| `lib/firebase-admin.ts` | Init Firebase Admin SDK (server only) |
| `lib/firestore.ts` | Helpers para ler coleções via client SDK |
| `lib/services/firebase-providers.ts` | Lista prestadores do Firestore |
| `lib/services/firebase-orders.ts` | Pedidos em tempo real |
| `lib/services/users-service.ts` | CRUD de usuários |
| `lib/services/firebase-financial.ts` | Dados financeiros |

### Backend Express (AVISO)
O diretório `dashboard_admin/src/` contém um servidor Express separado (`dev:server`). Ele **não é chamado pelo frontend** — as API Routes do Next.js (em `app/api/`) são o backend real. O Express foi reescrito para usar Firebase Admin SDK e é um servidor auxiliar opcional.

---

## 5. Componente: Backend de Pagamentos (`backend/`)

### Stack
- Node.js 20+ · Express 4
- Firebase Admin SDK 12
- Axios (chamadas Pagar.me)
- Helmet · Morgan · express-rate-limit

### Variáveis de Ambiente (`backend/.env`)
```
NODE_ENV=production
PORT=3000
PAGARME_BASE_URL=https://api.pagar.me/core/v5
PAGARME_SECRET_KEY=sk_...
FIREBASE_PROJECT_ID=aplicativoservico-143c2
FIREBASE_CLIENT_EMAIL=firebase-adminsdk-...@aplicativoservico-143c2.iam.gserviceaccount.com
FIREBASE_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\n..."
CORS_ORIGIN=*
GROQ_API_KEY=gsk_...        # IA do assistente do app (plano 06); opcional GROQ_MODEL
```

### Endpoints
| Método | Rota | Finalidade |
|---|---|---|
| GET | `/api/health` | Health check |
| POST | `/api/payments/card` | Pagamento cartão crédito |
| POST | `/api/payments/pix` | Pagamento PIX |
| POST | `/api/payments/pricing/calculate` | Cálculo de preço — lê `catalog_services` (Firestore) PRIMEIRO, com fallback na tabela hardcoded. Requer `FIREBASE_*` no Render |
| GET | `/api/payments/{orderId}/status` | Status do pagamento |
| POST | `/api/ai/classify` | Assistente IA (plano 06): classifica a descrição do cliente em UM nicho do catálogo (proxy Groq). Exige ID token + rate-limit. Requer `GROQ_API_KEY` no Render |

### Deploy (Render.com)
- URL produção: `https://aquiresolve.onrender.com`
- Configurado via `backend/render.yaml`
- Keep-alive embutido para evitar cold starts

---

## 6. Firebase: Regras de Segurança

### Regras do Firestore
Arquivo: `firestore.rules` (raiz do repo) — deploy com:
```bash
firebase deploy --only firestore:rules,firestore:indexes
```

**Funções de autorização:**
- `isSignedIn()` — usuário autenticado via Firebase Auth
- `isAdmin()` — custom claim `{ role: 'admin' }` ou `{ admin: true }`
- `isProvider()` — custom claim `{ role: 'prestador' }`
- `isClient()` — custom claim `{ role: 'cliente' }`
- `isOwner(uid)` — uid do token == uid do doc

**Regra crítica:** A coleção `adminmaster` só pode ser lida/escrita pelo Firebase Admin SDK (regras bloqueiam client SDK). O login do painel usa Admin SDK no servidor.

**Catálogo de serviços (segurança):** `service_categories`, `service_types`, `service_providers` e **`catalog_services`** têm `allow read: if isSignedIn()` e **`allow write: if false`** — escrita exclusiva via Admin SDK (rotas `/api/catalog` e `/api/catalog/services`). Antes a escrita era liberada a qualquer usuário autenticado, o que permitia adulterar o catálogo/preços pelo app; isso foi corrigido.

**Atenção sobre `isAdmin()`:** hoje **nenhum** usuário tem custom claim (`role:'admin'`/`admin:true`), então as regras que dependem de `isAdmin()` via client SDK não passam. Isso é intencionalmente coberto porque **toda escrita privilegiada do painel passa por API Routes (Admin SDK)**, que ignoram as regras. Se um dia for preciso escrita privilegiada via client SDK no painel, setar o claim no usuário Firebase Auth correspondente (ver abaixo).

**Para setar custom claims de admin:**
```js
await admin.auth().setCustomUserClaims(uid, { role: 'admin' });
// Ou:
await admin.auth().setCustomUserClaims(uid, { admin: true });
```

### Regras de Storage
Arquivo: `storage.rules` — apenas usuários autenticados, max 10MB por arquivo.

### Índices do Firestore
Arquivo: `firestore.indexes.json` — deploy com `firebase deploy --only firestore:indexes`

---

## 7. Programa de Cashback (AquiCash)

Configurado via documento `app_config/cashback` no Firestore. **Só o Admin SDK (dashboard) escreve nesse documento.**

### Campos
```json
{
  "activePhase": "growth",   // "growth" ou "launch"
  
  // Fase growth (cashback por tier)
  "bronze": { "minSpend": 0, "cashbackPercent": 3 },
  "silver": { "minSpend": 500, "cashbackPercent": 5 },
  "gold":   { "minSpend": 1000, "cashbackPercent": 8 },
  
  // Fase launch (desconto direto no carrinho)
  "launch": {
    "2services": 5,
    "3services": 10,
    "4plusServices": 15
  },
  
  // Combos por categoria (ambas as fases)
  "combos": [
    { "categories": ["Elétrica", "Hidráulica"], "discountPercent": 10 }
  ]
}
```

---

## 8. Fluxo de Setup Completo (novo ambiente)

### 1. Firebase Console
1. Criar usuário Firebase Auth: `master@aquiresolve.com` com senha segura
2. Baixar `google-services.json` e colocar em `app/`
3. Criar Service Account no Firebase Console → Projeto → Configurações → Contas de serviço → Gerar nova chave privada

### 2. Regras e Índices
```bash
firebase login
firebase use aplicativoservico-143c2
firebase deploy --only firestore:rules,firestore:indexes,storage:rules
```

### 3. Painel Admin
```bash
cd dashboard_admin
cp .env.local.example .env.local
# Preencher .env.local com os valores reais
npm install
npm run dev
# Acessar http://localhost:3000/setup-adminmaster e clicar em "Configurar"
# Ou:
curl -X POST http://localhost:3000/api/setup-adminmaster -H "Content-Type: application/json" \
  -d '{"email":"master@aquiresolve.com","nome":"Admin Master"}'
```

### 4. Backend de Pagamentos
```bash
cd backend
cp .env.example .env
# Preencher .env com chaves Pagar.me e credenciais Firebase
npm install
npm start
```

### 5. App Mobile
- Abrir `app/` no Android Studio
- Colocar `google-services.json` em `app/`
- `Run → Run 'app'`

---

## 9. Decisões de Arquitetura Importantes

### Por que o painel admin usa Firebase Auth e não sessão própria?
O `auth-provider.tsx` usa `signInWithEmailAndPassword` do Firebase Auth. Isso permite que o client SDK faça leituras diretas do Firestore com as regras `isSignedIn()`, sem precisar passar por API routes para cada leitura.

### Por que `adminmaster/master` está bloqueado ao client SDK?
Evita que qualquer usuário Firebase Auth (como clientes ou prestadores do app mobile) acesse os dados do admin. Só o servidor (Admin SDK) pode ler/escrever essa coleção.

### Por que o Express server (`src/`) existe se não é usado pelo frontend?
É um servidor auxiliar para uso futuro ou integração via API externa. O Next.js API Routes (`app/api/`) é o backend principal do painel. Os dois podem rodar em paralelo com `npm run dev:full`, mas o frontend só chama `/api/*` do Next.js.

### Por que `app_config/cashback` tem `allow write: if false`?
Cashback é uma configuração financeira crítica. Só o Firebase Admin SDK (via dashboard no servidor) pode alterá-la, nunca diretamente pelo client SDK do app mobile.

---

## 10. Problemas Conhecidos e Soluções

| Problema | Causa | Solução |
|---|---|---|
| Firebase Admin não inicializa / "Login master indisponível" | `FIREBASE_SERVICE_ACCOUNT` no Vercel com double-encoding (`\"` e `\n` literais) | Ver script Python na seção 11 para gerar o JSON limpo e re-upload correto |
| Backend Render não autentica | Valores quebrados com prefixos JSON no env | Ver seção "Render — Env Vars Corretas" abaixo |
| Aprovação de prestador falha com 403 | Client SDK não pode escrever em `providers/` (Firestore rules) | O hook agora usa `PATCH /api/providers/[id]/verify` (Admin SDK) |
| **Aprovar/rejeitar prestador silenciosamente falha (botão não funciona)** | `approveVerification`/`rejectVerification` em `hooks/use-document-verification.ts` tentavam escrever em `provider_verifications` via client SDK antes de chamar a API — mas `allow write: if false` nas regras lança `PERMISSION_DENIED`, que o `catch` engolia e retornava `false` sem chegar à chamada `PATCH /api/providers/[id]/verify` | **CORRIGIDO 2026-06-22**: removidos os writes via client SDK do hook — a rota `/api/providers/[id]/verify` (Admin SDK) já grava o histórico em `provider_verifications`. **Regra:** nunca escrever em `provider_verifications` pelo client SDK; tudo passa pela API route. |
| Cashback não atualiza no app | Admin não tinha UI para configurar `app_config/cashback` | Acesse `/dashboard/configuracoes/aquicash` |
| Admin não consegue atualizar usuário | Firestore rules exigiam `isOwner` | Regra corrigida: `isAdmin()` pode atualizar qualquer `users/` |
| Login falha no painel | Usuário não existe no Firebase Auth | Criar usuário no Firebase Console |
| `adminmaster/master` not found | Setup não executado | Chamar `POST /api/setup-adminmaster` |
| `providerBalance` sempre zero | Campo não era atualizado ao concluir pedido | CORRIGIDO: `PATCH /api/orders/[id]` com `status=completed` faz `FieldValue.increment(commission)` em `providers/{id}` e `users/{id}` |
| Providers aparecem vazios | Firestore `providers` vazio ou SDK não autenticado | Verificar auth e dados no Firestore |
| Pedidos não aparecem | `NEXT_PUBLIC_FIREBASE_*` não configurados | Preencher `.env.local` |
| Pagar.me falha | Chave de API incorreta ou expirada | Verificar `API_KEY_PRIVATE_PAGARME` |
| Storage Upload falha | `storageBucket` incorreto | Verificar `NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET` |
| Catálogo não salva no painel | Regra bloqueia escrita client-SDK (esperado) | O painel usa `POST/DELETE /api/catalog` (Admin SDK); confira `FIREBASE_SERVICE_ACCOUNT` no Vercel |
| Catálogo não aparece no app | `service_categories` vazio | Rodar `node dashboard_admin/scripts/seed-catalog.mjs`; o app cai no fallback estático se vazio |
| Serviços/preços do painel não refletem na cobrança | `catalog_services` vazio OU backend sem `FIREBASE_*` no Render | Rodar `node dashboard_admin/scripts/seed-catalog-services.mjs`; conferir `FIREBASE_*` no Render (backend lê Firestore-first) |
| Novos serviços não aparecem na lista do app | App ainda com APK antigo (lista de serviços era hardcoded) | Gerar novo APK (`./gradlew assembleDebug`); preço de serviços já existentes muda na hora via backend |
| Reembolso falha no painel | `API_KEY_PRIVATE_PAGARME` ausente ou cobrança não-paga | Conferir chave no Vercel; só cobranças `paid`/`captured` são reembolsáveis |
| Webhook Pagar.me rejeitado (401) | `PAGARME_WEBHOOK_SECRET` no Render ≠ segredo enviado pelo painel Pagar.me | Manter os dois iguais OU deixar ambos vazios (polling de 5s do app já confirma o pagamento) |
| Botão "Salvar Serviços" desabilitado permanentemente | Prestador tem solicitação `pending` em `provider_specialty_requests` | Aprovar ou rejeitar no painel (`/dashboard/controle/especialidades`); o botão reabilita no próximo reload |
| **`PERMISSION_DENIED` ao fazer pedido (com fotos)** | `validClientOrderUpdate` → `orderSensitiveAssignmentFieldsUnchanged()` lia `assignedProvider`/códigos direto; pedido recém-criado não tem esses campos (proibidos por `validOrderCreate`), então o `update("images")` pós-criação era negado e o pedido revertia | **CORRIGIDO** (commit `94d9136`, ruleset `c4770cb9`): guard usa `get(campo, null)`. É correção de **regra** → vale pro APK já instalado, sem novo APK. Detalhes: `docs/CORRECAO_PERMISSION_DENIED_PEDIDO_2026-06-15.md` |
| `PERMISSION_DENIED` ao criar pedido (APK antigo) | APK pré-18/05 grava o `OrderData` inteiro com `status='distributing'` e sem `paymentStatus`; `validOrderCreate` (pay-before-distribution) nega | Rebuildar o APK (o código atual já grava o payload enxuto correto) |
| Precisa publicar/testar regra sem `firebase` CLI | A máquina não tem o Firebase CLI | Usar a REST API com a service account: `POST /v1/projects/{P}/rulesets` + `PATCH …/releases/cloud.firestore?updateMask=rulesetName`; testar com ID token (`signInWithCustomToken`) na REST do Firestore. Ver doc da correção 2026-06-15 |

### Render — Env Vars Corretas

O backend de pagamentos (`aquiresolve.onrender.com`) precisa das variáveis abaixo. Os valores corretos **sem** prefixos JSON:

```
NODE_ENV=production
PORT=10000
PAGARME_BASE_URL=https://api.pagar.me/core/v5
PAGARME_SECRET_KEY=sk_...       # chave secreta Pagar.me
FIREBASE_PROJECT_ID=aplicativoservico-143c2
FIREBASE_CLIENT_EMAIL=firebase-adminsdk-fbsvc@aplicativoservico-143c2.iam.gserviceaccount.com
FIREBASE_PRIVATE_KEY=-----BEGIN PRIVATE KEY-----\nMIIEvgI...-----END PRIVATE KEY-----\n
CORS_ORIGIN=*
KEEP_ALIVE_ENABLED=true
KEEP_ALIVE_URL=https://aquiresolve.onrender.com/api/health
KEEP_ALIVE_INTERVAL_MS=840000
GROQ_API_KEY=gsk_...          # IA do assistente do app (plano 06); opcional GROQ_MODEL (default llama-3.3-70b-versatile)
```

**Atenção:** `FIREBASE_PRIVATE_KEY` deve conter a chave PEM completa com `\n` literal (não quebras de linha reais). O `env.js` do backend faz o `replace(/\\n/g, '\n')` automaticamente.

### Status de configuração verificado (2026-06-13)
Conferência completa de regras + variáveis (tudo OK, nada precisou de correção além de publicar a regra nova):
- **Firebase rules:** `firestore.rules` (com `catalog_services`) **publicada** via `firebase deploy --only firestore:rules` (service account `firebase-adminsdk-fbsvc@…`). Compila com avisos pré-existentes (funções não usadas), sem erros.
- **Render** (`srv-d6hmk2p4tr6s73bu5fm0` / serviço "AquiResolve", branch `main`, autoDeploy **off**): presentes e válidos `FIREBASE_PROJECT_ID`/`FIREBASE_CLIENT_EMAIL`/`FIREBASE_PRIVATE_KEY` (PEM ok), `PAGARME_SECRET_KEY` (sk_ LIVE), `PAGARME_BASE_URL`, `CORS_ORIGIN`, `NODE_ENV=production`, `KEEP_ALIVE_*`, `CRON_SECRET`. Como autoDeploy é off, o backend só pega o código novo (pricing Firestore-first) com deploy manual (`git push render main` ou Manual Deploy no painel/API Render).
- **Vercel** (`alvaro209890s-projects/aquiresolve-dashboard`): 14 vars em Production, incluindo `FIREBASE_SERVICE_ACCOUNT` (validado pelo login master) + `NEXT_PUBLIC_FIREBASE_*` + `*_PAGARME` + Google Maps. Sem integração GitHub — deploy do painel é manual (`npx vercel deploy --prod --yes` de `dashboard_admin/`).

### Custom Claims — Admin

Para que o painel admin tenha `isAdmin()` nas Firestore rules via client SDK, o usuário admin precisa do custom claim:

```js
// No Firebase Console > Functions ou via Admin SDK uma vez:
await admin.auth().setCustomUserClaims(uid, { role: 'admin' })
```

Sem isso, o admin loga mas as Firestore rules rejeitam escritas via client SDK. As API Routes no servidor (Admin SDK) funcionam independentemente dos claims.

---

## 11. Git e Deploy

### Regra de commit
Commitar diretamente no `master` (sem PR). Push no master dispara deploy automático no Vercel.

### O que NÃO vai ao GitHub
- `dashboard_admin/.env.local` — credenciais do painel
- `app/google-services.json` — config Firebase do app
- `app/keystore/` — keystore de assinatura do APK
- `backend/.env` — chaves Pagar.me e Firebase

### Deploy do Painel Admin (Vercel)

**Conta Vercel:** `alvaro209890` (`alvaro209890s-projects`)
**Projeto:** `aquiresolve-dashboard`
**URL de produção:** https://aquiresolve-dashboard.vercel.app
**Painel Vercel:** https://vercel.com/alvaro209890s-projects/aquiresolve-dashboard

O projeto está vinculado via CLI (`dashboard_admin/.vercel/project.json`). **Não há integração automática com GitHub** — o deploy precisa ser disparado manualmente via CLI:

```bash
cd dashboard_admin
npx vercel deploy --prod --yes
```

Para vincular em uma nova máquina (se `.vercel/` não existir):
```bash
cd dashboard_admin
npx vercel login          # autenticar como alvaro209890
npx vercel link --yes --project aquiresolve-dashboard
npx vercel deploy --prod --yes
```

**Variáveis de ambiente já configuradas no Vercel (production):**
| Variável | Finalidade |
|---|---|
| `FIREBASE_SERVICE_ACCOUNT` | JSON da service account Firebase (Admin SDK) |
| `NEXT_PUBLIC_FIREBASE_API_KEY` | Firebase client SDK |
| `NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN` | Firebase client SDK |
| `NEXT_PUBLIC_FIREBASE_PROJECT_ID` | `aplicativoservico-143c2` |
| `NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET` | Firebase Storage |
| `NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID` | FCM |
| `NEXT_PUBLIC_FIREBASE_APP_ID` | Firebase client SDK |
| `NEXT_PUBLIC_FIREBASE_MEASUREMENT_ID` | Analytics |
| `NEXT_PUBLIC_FIREBASE_DATABASE_URL` | Realtime Database |
| `NEXT_PUBLIC_GOOGLE_MAPS_API_KEY` | Google Maps (web) |
| `NEXT_PUBLIC_GOOGLE_MAPS_API_KEY_ANDROID` | Google Maps (Android) |
| `API_KEY_PRIVATE_PAGARME` | Pagar.me secret key |
| `API_KEY_PUBLIC_PAGARME` | Pagar.me public key |
| `ID_PUBLIC_PAGARME` | Pagar.me public ID |
| `GROQ_API_KEY` | Copiloto IA do painel (plano 08) — server-only; opcional `GROQ_MODEL` (pendente de configuração) |

**ATENÇÃO ao atualizar `FIREBASE_SERVICE_ACCOUNT` no Vercel:**

O `.env.local` armazena o valor com double-encoding (`\"` e `\n` literais). Enviar esse valor diretamente para o Vercel causa erro "Login master indisponível" / Firebase Admin não inicializa.

Use o script Python abaixo para extrair o JSON limpo e fazer o upload correto:

```bash
cd dashboard_admin

# 1. Gera o JSON limpo
python3 << 'EOF'
import re, json
with open('.env.local') as f:
    content = f.read()
match = re.search(r'^FIREBASE_SERVICE_ACCOUNT=(.+)$', content, re.MULTILINE)
raw = match.group(1)
step1 = raw.strip('"').replace('\\n', '\n').replace('\\"', '"')
fixed = re.sub(
    r'("private_key":\s*")(.*?)(")',
    lambda m: m.group(1) + m.group(2).replace('\n', '\\n') + m.group(3),
    step1, flags=re.DOTALL
)
sa = json.loads(fixed)
with open('/tmp/sa_clean.json', 'w') as f:
    f.write(json.dumps(sa, separators=(',', ':')))
print("OK:", sa['client_email'])
EOF

# 2. Faz o upload para o Vercel (sem aspas extras)
npx vercel env rm FIREBASE_SERVICE_ACCOUNT production --yes
cat /tmp/sa_clean.json | npx vercel env add FIREBASE_SERVICE_ACCOUNT production --yes
npx vercel deploy --prod --yes
```

**Sinal de que está correto:** `vercel env add` não deve exibir o aviso "Value includes surrounding quotes". Se exibir, o script não removeu as aspas externas.

### Deploy do Backend (Render)
- **Render:** deploy manual ou via webhook — `cd backend && git push render master`

---

## 12. Referências Rápidas

- **Firebase Console:** https://console.firebase.google.com/project/aplicativoservico-143c2
- **Painel Admin (produção):** https://aquiresolve-dashboard.vercel.app
- **Vercel Dashboard:** https://vercel.com/alvaro209890s-projects/aquiresolve-dashboard
- **Render Dashboard:** https://dashboard.render.com (backend de pagamentos)
- **Pagar.me Dashboard:** https://dashboard.pagar.me
- **Docs técnicas detalhadas:** `docs/` (cashback, pagamentos, checklist OS, etc.)
