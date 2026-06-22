# 00 — Roadmap & Prioridades

> Ordem de execução pensada para **gerar conversão o quanto antes**. A IA fica por último
> de propósito: hoje o maior ganho é organização visual + velocidade de contratação,
> não inteligência artificial.

---

## 🪜 Fases

### Fase 1 — Fundamentos visuais da Home (entrega rápida, alto impacto)
Foco: o cliente bate o olho e em segundos sabe o que fazer.

1. **Categorias horizontais** (`02`) — substitui a navegação atual por nichos em scroll lateral.
2. **Banner rotativo** (`01`) — topo da Home, comunica cashback/promos/combos/parceiros.
3. **Busca inteligente** (`05`) — sugestões instantâneas ao digitar → contratação em 1 toque.

> Ao final da Fase 1 a Home já parece "premium" e a conversão tende a subir, **sem IA**.

### Fase 2 — Monetização e parcerias (conteúdo gerenciável)
4. **Combos promocionais** (`03`) — reaproveita a lógica de desconto já existente.
5. **Parceiros AquiResolve** (`04`) — espaço para patrocinadores (descontos/cashback/cupons).

### Fase 3 — Inteligência (conveniência)
6. **Assistente IA Groq** (`06`) — descreve o problema → IA identifica o nicho e direciona.

### Fase 4 — Fechamento
7. **Home Premium — montagem** (`07`) — integra todas as seções, ajusta ordem/scroll/estados,
   polimento final e QA de regressão.

---

## 🔗 Dependências entre features

```
Categorias horizontais ──┐
                         ├──► Home Premium (montagem 07)
Banner rotativo ─────────┤
Busca inteligente ───────┤
Combos promocionais ─────┤        (Combos depende de PromotionManager/app_config já existentes)
Parceiros ───────────────┤
Assistente IA ───────────┘        (IA depende de proxy no backend Render + CatalogRepository)
```

- **Categorias** e **Busca** consomem o mesmo catálogo (`CatalogRepository` + `catalog_services`) — fazer juntas economiza esforço.
- **Combos** reusa `PromotionManager` + `CashbackManager.CashbackConfig` (`app_config/cashback`); a seção visual é nova, a regra de desconto **não muda**.
- **IA** precisa do **endpoint proxy no backend** antes da tela do app (não embutir a chave Groq no APK).
- **Montagem (07)** só fecha depois das seções existirem (mas pode começar com placeholders).

---

## 📊 Métricas de sucesso (o que observar)

| Métrica | Antes (baseline) | Meta |
|---------|------------------|------|
| Tempo até iniciar um pedido (abrir app → tela de criar pedido) | medir | < 30s |
| % de sessões que tocam em uma categoria/banner | medir | ↑ |
| Taxa de uso da busca | medir | ↑ |
| Combos adicionados ao carrinho | 0 | > 0 |
| Cliques em parceiros | 0 | > 0 |
| Uso do assistente IA (quando lançado) | 0 | acompanhar |

> Instrumentar via Firebase Analytics (já no projeto: `firebase-analytics`). Eventos sugeridos:
> `home_categoria_click`, `home_banner_click`, `busca_sugestao_click`, `combo_add_cart`,
> `parceiro_click`, `ia_assistente_open`, `ia_nicho_sugerido`.

---

## 🧱 Estimativa de esforço (relativa)

| Feature | Complexidade | Risco | Nota |
|---------|--------------|-------|------|
| Categorias horizontais | Baixa | Baixo | RecyclerView horizontal; adapter já existe parecido |
| Banner rotativo | Baixa-Média | Baixo | ViewPager2 (já no projeto) + auto-scroll + dots |
| Busca inteligente | Média | Baixo | Reusa `ServiceSearchHelper`; dropdown de sugestões |
| Combos promocionais | Média | Médio | UI nova + admin CRUD; lógica de desconto já existe |
| Parceiros | Média | Baixo | CRUD + seção; imagens via Storage/URL |
| Assistente IA | Média-Alta | Médio | Proxy backend + prompt + parsing + roteamento |
| Montagem Home | Média | Médio | Regressão de layout/scroll/insets |

---

## ✅ Status global da leva

| # | Feature | Painel | Firestore/Regras | App | QA | Status |
|---|---------|--------|------------------|-----|----|--------|
| 1 | Banner rotativo | [ ] | [ ] | [ ] | [ ] | ⬜ Não iniciado |
| 2 | Categorias horizontais | n/a | reusa `service_categories` | [x] | [x] | ✅ Concluído (2026-06-22) |
| 3 | Combos promocionais | [ ] | [ ] | [ ] | [ ] | ⬜ Não iniciado |
| 4 | Parceiros | [ ] | [ ] | [ ] | [ ] | ⬜ Não iniciado |
| 5 | Busca inteligente | n/a | [ ] | [ ] | [ ] | ⬜ Não iniciado |
| 6 | Assistente IA | [ ] (proxy) | [ ] | [ ] | [ ] | ⬜ Não iniciado |
| 7 | Home Premium (montagem) | n/a | n/a | [ ] | [ ] | ⬜ Não iniciado |

> Atualize esta tabela conforme avança (⬜ → 🟨 em andamento → ✅ concluído).

---

## 🧩 Decisões de arquitetura já fixadas

1. **Conteúdo é dado, não código.** Banner/combos/parceiros vivem no Firestore e são editados no painel.
2. **Padrão de coleção nova:** `read: if isSignedIn()` / `write: if false` + rota Admin SDK no painel.
3. **Pré-carregamento:** repositórios novos podem aquecer cache no `AppApplication` (como o catálogo já faz), com fallback silencioso.
4. **IA via proxy:** chave Groq fica no backend Render; o app só chama `POST /api/ai/...`.
5. **Sem novas libs pesadas:** carrossel usa `ViewPager2` (já presente); evitar dependências externas quando o nativo resolve.
</content>
