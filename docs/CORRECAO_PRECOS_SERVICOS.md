# 🔧 Correção de Preços nos Serviços — 11/05/2026

## Problema Identificado

Os nomes dos serviços no dropdown da tela "Novo Pedido" (`CreateOrderActivity.kt`) **não correspondiam** às chaves da tabela de preços (`ServicePricing.kt`). Isso fazia com que `getPrice()` retornasse `null` e o sistema usasse o **fallback genérico** (`getDefaultPrice()`), exibindo preços errados.

### Exemplo (Ar Condicionado — Antes)

```
Instalação de ar condicionado → R$ 650,00 ❌ (fallback, deveria nem existir esse nome)
Manutenção preventiva      → R$ 650,00 ❌ (fallback, sem preço real)
Limpeza e profunda...      → R$ 650,00 ❌ (fallback)
Recarga de gás             → R$ 650,00 ❌ (fallback, sem preço real)
```

### Exemplo (Ar Condicionado — Depois)

```
9 a 12 mil BTUs split              → R$ 650,00 ✅
18 a 30 mil BTUs                   → R$ 750,00 ✅
Ar de janela                       → R$ 220,00 ✅
Higienização de 9 a 30 mil BTUs    → R$ 300,00 ✅
```

---

## ✅ O que foi corrigido

### Elétrica — 4 correções
| Antes (errado) | Depois (certo) | Preço |
|---|---|---|
| Instalação de lâmpadas | Instalação de **lâmpada** | R$ 110 |
| Instalação de tomadas | Instalação de **tomada** | R$ 110 |
| Instalação de luminárias | Instalação de **luminária** | R$ 150 |
| Instalação de interruptores | Instalação de **interruptor** | R$ 110 |

### Encanador — 7 correções
| Antes (errado) | Depois (certo) | Preço |
|---|---|---|
| Troca de torneiras | Troca de **torneira** | R$ 160 |
| Troca de sifões | Troca de **sifão** | R$ 110 |
| Troca de registros | Troca de **reparos de registro** | R$ 160 |
| Troca de Filtros | Troca de **Filtro** | R$ 160 |
| troca de reparos de registros | Troca de reparos de **torneira** | R$ 160 |
| Troca de reparos de torneiras | Troca de reparos de **torneira** | R$ 160 |
| Revisão hidráulica até 7 pontos | Revisão hidráulica **(até 7 pontos)** | R$ 160 |
| troca de torneira monobloco | **Troca** de torneira monobloco | R$ 260 |

### Instalação — 1 adição
Faltava "Varal de teto" → R$ 150,00 ✅

### Desentupimento com maquinário — correção completa
| Antes | Depois | Preço |
|---|---|---|
| Desentupimento de pia (serviço errado) | **Até 2 metros** | R$ 200 |
| Desentupimento ralo (serviço errado) | **Adicional por Metro** | R$ 90 |
| Desentupimento vaso (serviço errado) | *(removido)* | — |

### Limpeza de Estofados — 4 correções
- Removido "Limpeza de sofá **4** lugares" (não existe na tabela)
- Removida duplicata "Limpeza de cadeiras estofadas"
- "Limpeza de tapetes (até 2 m)" → "Limpeza de tapetes **pequenos** (até 2 **mts**)" ✅
- "Limpeza de carpetes pequenos (até 2 m)" → "Limpeza de carpetes pequenos (até **2mts**)" ✅

### Ar Condicionado — correção completa
| Antes | Depois | Preço |
|---|---|---|
| Instalação de ar condicionado | **9 a 12 mil BTUs split** | R$ 650 |
| Manutenção preventiva | **18 a 30 mil BTUs** | R$ 750 |
| Limpeza e profunda (filtros e serpentinas) | **Ar de janela** | R$ 220 |
| Recarga de gás | **Higienização de 9 a 30 mil BTUs** | R$ 300 |

### Faxina — 3 correções
Nomes atualizados para incluir duração (ex: " - 4h a 5h") para bater com a tabela.
Removido "Faxina expressa (só manutenção)" — não existe na planilha.

---

## ❌ AINDA PENDENTE — 19 serviços sem preço

Aguardando definição de valores via WhatsApp.

### 🎨 Pintura (5 serviços — sem tabela)
- Pintura de parede interna
- Pintura de teto
- Pintura de porta
- Pintura de janela
- Retoques gerais

### 🌿 Jardinagem (5 serviços — sem tabela)
- Corte de grama
- Poda de arbustos
- Limpeza de jardim
- Adubação
- Plantio de mudas

### 🧹 Limpeza (5 serviços — sem tabela)
- Limpeza residencial básica
- Limpeza pós-obra
- Limpeza pesada
- Limpeza de vidros
- Organização

### 🔧 Serviços Automotivos (4 serviços extras)
- Troca de palhetas de limpador
- Troca de lâmpadas automotivas
- Troca de óleo e filtro domiciliar
- Higienização de ar-condicionado automotivo

---

## 🎯 Resumo

| Status | Quantidade |
|---|---|
| ✅ Corrigidos | 74 serviços |
| 💰 "A consultar" (Mont. móveis) | 9 serviços |
| ❌ Sem preço (pendente) | 19 serviços |

**Arquivo modificado:** `app/src/main/java/com/aquiresolve/app/CreateOrderActivity.kt`

**Fonte oficial:** `tabela.xlsx` (sheet `Dados_Para_Importacao`)
