# 12 — Dashboard Financeiro Completo

**Data:** 2026-06-27
**Autor:** Hermes Agent (a pedido do Álvaro)
**Status:** Implementado

## Resumo

Refatoração completa da aba Financeiro do painel admin, transformando-a em um dashboard financeiro profissional com gráficos interativos, KPIs avançados, página de analytics e relatórios com dados reais do Firestore/Pagar.me.

---

## O que foi criado/modificado

### 1. Nova API: `GET /api/financial/analytics`

Endpoint agregador que combina dados do Firestore (`orders`, `providers`, `order_settlements`, `users`) para produzir métricas financeiras completas.

**Parâmetros:** `?period=` (`7d`, `30d`, `90d`, `mes`, `ano`)

**Retorna:**
- **KPIs:** Receita total, transações, ticket médio, taxa de sucesso, conversão, total de clientes/prestadores, comissão plataforma, cashback distribuído, saldo a pagar
- **Charts:** Receita diária (array `dailyRevenue[]`), métodos de pagamento, status breakdown
- **Rankings:** Top 10 serviços por receita
- **Transações recentes:** Últimas 10

**Arquivo:** `dashboard_admin/app/api/financial/analytics/route.ts`

### 2. Novo Hook: `useFinancialAnalytics`

Hook React que consome a API acima com suporte a período dinâmico.

**Arquivo:** `dashboard_admin/hooks/use-financial-analytics.ts`

### 3. Componentes de Gráfico (Recharts)

Três componentes reutilizáveis baseados em Recharts com suporte a dark mode, loading states e dados vazios:

| Componente | Arquivo | Descrição |
|---|---|---|
| `RevenueAreaChart` | `components/charts/revenue-area-chart.tsx` | Gráfico de área para receita ao longo do tempo |
| `DonutChart` | `components/charts/donut-chart.tsx` | Rosca para distribuição (métodos de pagamento, categorias) |
| `StatusBarChart` | `components/charts/status-bar-chart.tsx` | Barras para status de transações |

### 4. Página Principal de Financeiro (Refatorada)

**Arquivo:** `app/dashboard/financeiro/page.tsx`

**Antes:** 4 KPIs simples + métodos de pagamento + últimas cobranças (169 linhas).
**Depois:** Dashboard completo com (+500 linhas):

- **Seletor de período:** 7d, 30d, 90d, mês, ano
- **KPI Row 1 (Receita):** Receita Total, Ticket Médio, Transações, Conversão
- **KPI Row 2 (Plataforma):** Comissão, Cashback, A Pagar Prestadores, Margem Líquida
- **Gráfico de receita:** Evolução diária (área)
- **Gráfico de rosca:** Distribuição por método de pagamento
- **Gráfico de barras:** Status das transações (pagos, pendentes, falhas, cancelados)
- **Ranking:** Top 10 serviços por receita com barras de progresso
- **Tabela:** Últimas transações com status e método
- **Loading states** com skeletons e `empty states` visuais

### 5. Nova Página: Analytics Financeiro

**Arquivo:** `app/dashboard/financeiro/analytics/page.tsx`

- **Insight Cards:** % PIX, Ticket Médio, Média Diária, Melhor Dia
- **Tendência de receita:** Gráfico de área full-width (350px)
- **Distribuição:** Rosca de métodos + Barras de status lado a lado
- **Ranking visual:** Barras horizontais proporcionais com % relativa ao top 1

### 6. Página de Relatórios (Reconstruída)

**Arquivo:** `app/dashboard/financeiro/relatorios/page.tsx`

**Antes:** Placeholder dizendo "área sem dados reais".
**Depois:** Relatório completo com:

- KPIs: Receitas, Despesas, Saldo, Contas Ativas
- Gráfico de receitas por período
- Rosca de receitas por categoria
- Lista de despesas por categoria
- Tabela de transações com indicador visual (verde/vermelho)
- Cards de saldo por conta bancária
- **Exportação CSV** com 1 clique

### 7. Sidebar Atualizado

Adicionados links "Analytics" e "Relatórios" no dropdown Financeiro.

**Arquivo:** `components/layout/sidebar.tsx`

---

## Dependências novas

```bash
npm install recharts
```

---

## Dados

Todos os dados vêm de fontes reais:

| Fonte | Coleção | Uso |
|---|---|---|
| Firestore | `orders` | Receita, status, métodos, serviços |
| Firestore | `providers` | Contagem de prestadores |
| Firestore | `order_settlements` | Comissão plataforma, cashback |
| Firestore | `users` | Contagem de clientes |
| Firestore | `transactions` | Relatórios (receitas/despesas manuais) |
| Firestore | `accounts` | Saldos por conta bancária |

**Nenhum dado fake ou hardcoded.** Se não houver dados, mostra "Sem dados no período".

---

## Dark Mode

Todos os componentes suportam tema escuro:
- Cores dos gráficos usam tokens CSS (`var(--border)`, `var(--muted-foreground)`, etc.)
- Tooltips do Recharts usam `var(--popover)` + `var(--popover-foreground)`
- Loading skeletons e empty states têm variantes escuras
- Badges, tabelas e cards seguem o padrão existente do projeto

---

## Permissões

Todas as páginas exigem `permission="financeiro"` via `RouteGuard`.
A API `/api/financial/analytics` exige `requireAdminPermission(request, 'financeiro')`.

---

## Testes

- Build Next.js: `npm run build` — sem erros
- Lint TypeScript: sem novos erros introduzidos
- Componentes testados com estados: loading, empty, error, dados normais

---

## Arquivos alterados/criados

```
NOVOS:
  app/api/financial/analytics/route.ts
  hooks/use-financial-analytics.ts
  components/charts/revenue-area-chart.tsx
  components/charts/donut-chart.tsx
  components/charts/status-bar-chart.tsx
  app/dashboard/financeiro/analytics/page.tsx

MODIFICADOS:
  app/dashboard/financeiro/page.tsx         (refatoração completa)
  app/dashboard/financeiro/relatorios/page.tsx  (reconstrução completa)
  components/layout/sidebar.tsx              (+2 links)
  package.json                               (+recharts)
```
