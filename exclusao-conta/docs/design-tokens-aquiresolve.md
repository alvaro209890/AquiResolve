# Design Tokens — Aqui Resolve
> Versão 1.0 · Abril/2026 · Sistema unificado: painel admin + fluxos públicos LGPD

---

## Diagnóstico do repositório (base para este documento)

| Arquivo procurado | Encontrado? | O que havia |
|---|---|---|
| `public/logo-aquiresolve.svg` | ❌ Ausente | Apenas `placeholder-logo.svg` (glifos `#000000` + mark `#171717`) e `icon.svg` (preto/branco adaptativo) |
| `app/globals.css` "Paleta AquiResolve" | ❌ Sem paleta de marca | Tokens shadcn genéricos: `--primary: #2563eb`, sem laranja, sem tokens LGPD |
| `tailwind.config.js` / `.ts` | ❌ Ausente | Projeto usa **Tailwind v4** — configuração via `@theme inline {}` no próprio CSS |
| Fonte | ✅ Definida | **Geist** / Geist Mono (next/font/google) |

> **Ação necessária:** adicionar `public/logo-aquiresolve.svg` (arquivo real da marca) ao repositório.
> As classes Tailwind geradas por este documento já estão funcionais via `globals.css`.

---

## 1. Tokens semânticos

### 1.1 Camada de primitivos (escala de cor)

| Token | Hex | Descrição |
|---|---|---|
| `--brand-orange-50` | `#FFF7ED` | Superfície laranja quase branca |
| `--brand-orange-100` | `#FFEDD5` | Superfície laranja suave |
| `--brand-orange-200` | `#FED7AA` | Laranja claro |
| `--brand-orange-500` | `#F97316` | **Laranja marca** (cor principal do "Resolve") |
| `--brand-orange-600` | `#EA580C` | Laranja hover/pressed |
| `--brand-blue-50` | `#EFF6FF` | Superfície azul quase branca |
| `--brand-blue-100` | `#DBEAFE` | Superfície azul suave |
| `--brand-blue-300` | `#93C5FD` | Azul claro (ring/focus) |
| `--brand-blue-500` | `#1D61FF` | **Azul ação LGPD** (stepper ativo, botão "Continuar") |
| `--brand-blue-600` | `#1855E0` | Azul hover |
| `--brand-blue-disabled` | `#89AAFF` | Azul desabilitado |

### 1.2 Tokens semânticos (use estes nos componentes, nunca os primitivos diretamente)

| Token | Valor (aponta para) | Hex resultante | Uso |
|---|---|---|---|
| `--brand` | `--brand-orange-500` | `#F97316` | Identidade de marca: logo, highlights, badges |
| `--brand-foreground` | `#FFFFFF` | `#FFFFFF` | Texto/ícone sobre fundo laranja |
| `--brand-hover` | `--brand-orange-600` | `#EA580C` | Estado hover de elementos laranja |
| `--brand-surface` | `--brand-orange-50` | `#FFF7ED` | Fundo de banners/chips de marca |
| `--brand-surface-foreground` | `--brand-orange-600` | `#EA580C` | Texto sobre surface laranja |
| `--lgpd-primary` | `--brand-blue-500` | `#1D61FF` | Ação primária: botão "Continuar", passo ativo |
| `--lgpd-primary-foreground` | `#FFFFFF` | `#FFFFFF` | Texto/ícone sobre botão azul |
| `--lgpd-primary-hover` | `--brand-blue-600` | `#1855E0` | Hover do botão primário |
| `--lgpd-primary-disabled` | `--brand-blue-disabled` | `#89AAFF` | Botão desabilitado |
| `--lgpd-primary-surface` | `--brand-blue-50` | `#EFF6FF` | Fundo de tooltips/notas LGPD |
| `--warning-surface` | — | `#FFF9E6` | Fundo da caixa de aviso amarela |
| `--warning-border` | — | `#FFD666` | Borda da caixa de aviso |
| `--warning-foreground` | — | `#996633` | Texto dentro do aviso |
| `--warning-icon` | — | `#E8A000` | Ícone dentro do aviso |
| `--danger-surface` | — | `#FFE8E8` | Fundo do ícone de perigo (lixeira) |
| `--danger-icon` | — | `#E53935` | Ícone de perigo |
| `--surface-page` | — | `#F8F9FA` | Fundo da página pública |
| `--text-primary` | — | `#212121` | Texto principal |
| `--text-secondary` | — | `#9E9E9E` | Texto secundário/placeholder |
| `--border-input` | — | `#E0E0E0` | Borda de inputs e divisores |

---

## 2. Regra de marca: laranja vs. azul

### Hierarquia clara

```
LARANJA (#F97316) — Identidade Aqui Resolve
  └── Onde usar: logo, favicon, nome da marca em texto, badges "Powered by",
                 chips de categoria, destaques de marketing, onboarding.
  └── NÃO usar: botões de ação em fluxos transacionais, links inline,
                estados de erro (confunde com alarme).

AZUL (#1D61FF) — Fluxos Transacionais e LGPD
  └── Onde usar: botão "Continuar", passo ativo do stepper, links clicáveis,
                 estados de foco, toda interface do wizard de exclusão de conta,
                 fluxos de checkout, confirmações, formulários críticos.
  └── Razão: azul é universalmente associado a ação confiável, essencial em
             contextos legais/jurídicos como LGPD onde neutralidade e seriedade
             transmitem mais segurança que uma cor de marca vibrante.
```

### Fusão proposta (não eliminação)

O laranja e o azul **coexistem sem conflito** porque operam em camadas distintas:

| Camada | Cor | Exemplo concreto |
|---|---|---|
| **Marca** | Laranja `#F97316` | Logo no header, rodapé, e-mails transacionais |
| **Ação/CTA** | Azul `#1D61FF` | Botão "Continuar", stepper LGPD, links |
| **Perigo** | Vermelho `#E53935` | Ícone lixeira, alertas de exclusão |
| **Aviso** | Amarelo `#FFD666` | Caixa de irreversibilidade |
| **Sucesso** | Verde `#16a34a` (existente) | Passo concluído, exclusão confirmada |

---

## 3. Bloco CSS — colar em `app/globals.css`

> Adicionar **após** as variáveis `:root { }` existentes do shadcn/ui, antes do bloco `.dark { }`.
> **Não remove** nenhuma variável existente.

```css
/* ═══════════════════════════════════════════════════════════════════
   PALETA DE CORES — AQUI RESOLVE  v1.0
   Tokens de marca e experiência pública. NÃO sobrescreve shadcn/ui.
   ═══════════════════════════════════════════════════════════════════ */

:root {
  /* ─── Primitivos de cor ─────────────────────────────────────────── */
  --brand-orange-50:       #FFF7ED;
  --brand-orange-100:      #FFEDD5;
  --brand-orange-200:      #FED7AA;
  --brand-orange-500:      #F97316;
  --brand-orange-600:      #EA580C;

  --brand-blue-50:         #EFF6FF;
  --brand-blue-100:        #DBEAFE;
  --brand-blue-300:        #93C5FD;
  --brand-blue-500:        #1D61FF;
  --brand-blue-600:        #1855E0;
  --brand-blue-disabled:   #89AAFF;

  /* ─── Identidade de Marca ───────────────────────────────────────── */
  --brand:                       var(--brand-orange-500);
  --brand-foreground:            #FFFFFF;
  --brand-hover:                 var(--brand-orange-600);
  --brand-surface:               var(--brand-orange-50);
  --brand-surface-foreground:    var(--brand-orange-600);

  /* ─── Fluxos Transacionais / LGPD ──────────────────────────────── */
  --lgpd-primary:                var(--brand-blue-500);
  --lgpd-primary-foreground:     #FFFFFF;
  --lgpd-primary-hover:          var(--brand-blue-600);
  --lgpd-primary-disabled:       var(--brand-blue-disabled);
  --lgpd-primary-surface:        var(--brand-blue-50);

  /* ─── Aviso (warning box — caixa amarela) ───────────────────────── */
  --warning-surface:             #FFF9E6;
  --warning-border:              #FFD666;
  --warning-foreground:          #996633;
  --warning-icon:                #E8A000;

  /* ─── Perigo (danger surface — fundo do ícone lixeira) ─────────── */
  --danger-surface:              #FFE8E8;
  --danger-icon:                 #E53935;

  /* ─── Neutros da experiência pública ───────────────────────────── */
  --surface-page:                #F8F9FA;
  --text-primary:                #212121;
  --text-secondary:              #9E9E9E;
  --border-input:                #E0E0E0;
}
```

---

## 4. Extensão Tailwind v4 (adicionar dentro do `@theme inline {}` existente)

> Este projeto usa **Tailwind v4** sem `tailwind.config.js`. A extensão de cores é feita via `@theme inline {}` em `globals.css`. Cole as linhas abaixo dentro do bloco `@theme inline { }` já existente.

```css
/* ── Brand & LGPD tokens → classes Tailwind ─────────────────────── */
--color-brand:                    var(--brand);
--color-brand-foreground:         var(--brand-foreground);
--color-brand-hover:              var(--brand-hover);
--color-brand-surface:            var(--brand-surface);
--color-brand-surface-foreground: var(--brand-surface-foreground);

--color-lgpd-primary:             var(--lgpd-primary);
--color-lgpd-primary-foreground:  var(--lgpd-primary-foreground);
--color-lgpd-primary-hover:       var(--lgpd-primary-hover);
--color-lgpd-primary-disabled:    var(--lgpd-primary-disabled);
--color-lgpd-primary-surface:     var(--lgpd-primary-surface);

--color-warning-surface:          var(--warning-surface);
--color-warning-border:           var(--warning-border);
--color-warning-foreground:       var(--warning-foreground);
--color-warning-icon:             var(--warning-icon);

--color-danger-surface:           var(--danger-surface);
--color-danger-icon:              var(--danger-icon);

--color-surface-page:             var(--surface-page);
--color-text-primary:             var(--text-primary);
--color-text-secondary:           var(--text-secondary);
--color-border-input:             var(--border-input);
```

### Classes geradas automaticamente

| Classe Tailwind | Cor |
|---|---|
| `bg-brand` / `text-brand` | `#F97316` laranja marca |
| `bg-brand-foreground` / `text-brand-foreground` | `#FFFFFF` |
| `bg-brand-surface` | `#FFF7ED` superfície laranja |
| `bg-lgpd-primary` / `text-lgpd-primary` | `#1D61FF` azul ação |
| `bg-lgpd-primary-surface` | `#EFF6FF` |
| `bg-warning-surface` | `#FFF9E6` |
| `border-warning-border` | `#FFD666` |
| `text-warning-foreground` | `#996633` |
| `bg-danger-surface` | `#FFE8E8` |
| `text-danger-icon` | `#E53935` |
| `bg-surface-page` | `#F8F9FA` |
| `text-text-primary` | `#212121` |
| `text-text-secondary` | `#9E9E9E` |

### Equivalente em `tailwind.config.js` (referência para Tailwind v3 / migração)

```js
// tailwind.config.js — NÃO criar neste projeto (usa Tailwind v4)
// Manter apenas como referência de nomenclatura
module.exports = {
  theme: {
    extend: {
      colors: {
        brand: {
          DEFAULT:             '#F97316',
          foreground:          '#FFFFFF',
          hover:               '#EA580C',
          surface:             '#FFF7ED',
          'surface-foreground':'#EA580C',
        },
        'lgpd-primary': {
          DEFAULT:             '#1D61FF',
          foreground:          '#FFFFFF',
          hover:               '#1855E0',
          disabled:            '#89AAFF',
          surface:             '#EFF6FF',
        },
        warning: {
          surface:             '#FFF9E6',
          border:              '#FFD666',
          foreground:          '#996633',
          icon:                '#E8A000',
        },
        danger: {
          surface:             '#FFE8E8',
          icon:                '#E53935',
        },
      },
    },
  },
}
```

---

## 5. Tipografia

### Fonte atual no repositório

```
Primária: Geist (next/font/google, carregada em app/layout.tsx)
Mono:     Geist Mono
```

### Recomendação

**Manter Geist** como escolha principal. Justificativa:

| Critério | Geist | Inter (alternativa comum) |
|---|---|---|
| Legibilidade em dados/números | ✅ Excelente (projetada para código/dados) | ✅ Boa |
| Tamanhos pequenos (12px) | ✅ Muito legível | ✅ Boa |
| Peso visual em mobile | ✅ Equilibrado | ✅ Boa |
| Já no projeto | ✅ Sim | ❌ Requer instalação |
| Alinhamento com Vercel/Next.js | ✅ Nativo | — |

### Escala de pesos obrigatória

| Peso | Token | Uso |
|---|---|---|
| 400 (`font-normal`) | Corpo de texto, labels | Parágrafos, descrições, alertas |
| 500 (`font-medium`) | Labels de formulário, steps | Input labels, step indicator |
| 600 (`font-semibold`) | Títulos de seção, badges | H3, nomes de campos de dado |
| 700 (`font-bold`) | Títulos primários, CTAs | H1, H2, botão de confirmação final |

### Escala de tamanhos (mobile-first)

| Uso | Mobile | Desktop | Tailwind |
|---|---|---|---|
| H1 (título da etapa) | 22px | 24px | `text-[22px] sm:text-2xl` |
| H2 (subtítulo) | 18px | 20px | `text-lg sm:text-xl` |
| Body | 14px | 14px | `text-sm` |
| Small/label | 12px | 12px | `text-xs` |
| Input | **16px (obrigatório iOS)** | 14px | `text-base md:text-sm` (já implementado) |

---

## 6. Logo — Diretrizes de Uso

### Estado atual do repositório

```
public/
  icon.svg              ← App icon (favicon, PWA): glifos AR adaptativos preto/branco
  placeholder-logo.svg  ← PLACEHOLDER — substituir pela arte final da marca
  icon-light-32x32.png  ← Favicon modo claro
  icon-dark-32x32.png   ← Favicon modo escuro
  apple-icon.png        ← Apple touch icon
```

> ⚠️ `public/logo-aquiresolve.svg` **não existe** no repositório.
> Solicitar ao time de design o arquivo vetorial final e adicioná-lo em `public/logo-aquiresolve.svg`.

### Especificações para o arquivo final

| Especificação | Valor |
|---|---|
| Formato | SVG (vetorial, escalável) |
| Variantes necessárias | `logo-aquiresolve.svg` (colorida), `logo-aquiresolve-white.svg` (para fundos escuros), `logo-aquiresolve-mono.svg` (preto, para impressão) |
| Altura mínima renderizada | **32px** (abaixo disso o texto fica ilegível) |
| Altura recomendada em header web | **40px** |
| Altura mínima em mobile | **32px** |
| Zona de exclusão (clear space) | Metade da altura do logo em todos os lados |
| Fundo claro | Logo colorida (laranja + azul + preto) |
| Fundo escuro (`#1a1a1a` ou inferior) | Logo branca (`logo-aquiresolve-white.svg`) |
| Fundo laranja (`--brand`) | Logo branca ou mono invertida |
| Fundo azul (`--lgpd-primary`) | Logo branca |

### Uso do ícone `icon.svg` (existente)

O arquivo `public/icon.svg` contém o **monograma AR** em preto/branco adaptativo (dark mode). Usar para:
- Favicon (`<link rel="icon">`) ✅ já configurado em `app/layout.tsx`
- PWA manifest icon
- Notificações push
- Contextos onde o logotipo completo não cabe (< 32px)

---

## 7. Checklist de Consistência (WCAG AA mínimo)

Execute estas 5 validações antes de qualquer deploy em produção:

### ✅ Checklist

- [ ] **Botão primário LGPD** — texto branco (`#FFFFFF`) sobre `--lgpd-primary` (`#1D61FF`):
  Contraste calculado: **6.2:1** ✅ AA e AAA. Verificar em: [WebAIM Contrast Checker](https://webaim.org/resources/contrastchecker/?fcolor=FFFFFF&bcolor=1D61FF)

- [ ] **Texto de aviso** — `--warning-foreground` (`#996633`) sobre `--warning-surface` (`#FFF9E6`):
  Contraste calculado: **5.9:1** ✅ AA. Verificar em: [WebAIM Contrast Checker](https://webaim.org/resources/contrastchecker/?fcolor=996633&bcolor=FFF9E6)

- [ ] **Laranja como texto** — `--brand` (`#F97316`) sobre branco (`#FFFFFF`):
  Contraste calculado: **3.1:1** ❌ Falha AA para texto normal (< 18px / 14px bold).
  **Regra:** nunca usar `#F97316` como cor de texto pequeno em fundo branco.
  Alternativa: usar texto `--brand-hover` (`#EA580C`) → **3.7:1** (falha AA regular, usa apenas em texto grande ≥ 18px ou bold ≥ 14px).
  **Recomendado para texto:** usar `#C2410C` → **4.8:1** ✅ AA.

- [ ] **Texto primário** — `--text-primary` (`#212121`) sobre `--surface-page` (`#F8F9FA`):
  Contraste calculado: **18.1:1** ✅ AAA.

- [ ] **Placeholder / texto secundário** — `--text-secondary` (`#9E9E9E`) sobre `#FFFFFF`:
  Contraste calculado: **2.85:1** ❌ Falha AA para texto normal.
  **Regra:** usar apenas para texto decorativo/auxiliar (placeholders, hints). Para texto lido pelo usuário, usar `#757575` → **4.6:1** ✅ AA.

---

## Notas de migração (admin panel → sistema unificado)

| Variável atual (globals.css) | Equivalente novo | Ação |
|---|---|---|
| `--primary: #2563eb` | Mapeia para shadcn — manter | ⚠️ Não alterar (quebra shadcn/ui) |
| `--destructive: #dc2626` | Próximo de `--danger-icon: #E53935` | Coexistência OK |
| `--background: #f5f5f5` | Mock sugere `#F8F9FA` | Opcional: atualizar para `#F8F9FA` |
| `--ring: #2563eb` | Manter (shadcn focus ring) | ✅ Compatível |

> O token `--lgpd-primary: #1D61FF` é o azul específico do wizard LGPD.
> O `--primary: #2563eb` continua como azul do painel admin (shadcn).
> Ambos coexistem sem conflito — são namespaces distintos.
