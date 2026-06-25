# Checklist do prestador — mobile, painel admin, Firebase e Render

## Escopo

Este documento registra o contrato operacional do checklist de Ordem de Serviço preenchido pelo prestador no app Android e consumido pelo painel administrativo.

## Fluxo mobile atual

1. O prestador abre o pedido no app Android.
2. Ao chegar no local, toca em **Iniciar OS**.
3. `OrderDetailsActivity` chama `FirebaseOrderManager.startService(orderId)` para mover o pedido para `in_progress`.
4. `FirebaseChecklistManager.startService(orderId, latitude, longitude)` cria/mescla `checklists/{orderId}` com:
   - `orderId`
   - `providerId`
   - `status = checklist_pending`
   - `startedAt`
   - `startLatitude` e `startLongitude`, quando disponíveis
5. `ChecklistActivity` registra:
   - confirmação de chegada e condições do local;
   - serviços realizados;
   - avarias pré-existentes;
   - se materiais/suprimentos foram usados;
   - descrição obrigatória dos materiais quando `materialsUsed = true`;
   - resolução do problema;
   - relatório técnico e observações.
6. `PhotoEvidenceActivity` exige pelo menos:
   - uma ou mais fotos de **chegada/antes** em `photosBefore`;
   - uma ou mais fotos de **pós-serviço** em `photosAfter`.
7. Fotos durante o serviço continuam aceitas em `photosDuring`, mas são opcionais.
8. Após salvar as fotos, o app pede o **código único do cliente**. Esse código aparece apenas para o cliente e é validado por `FirebaseOrderManager.completeOrderWithVerification(orderId, code)`.
9. A assinatura desenhada na tela não faz mais parte do fluxo novo.

As gravações usam `set(..., SetOptions.merge())`, então uma OS parcial pode ser retomada por versões novas ou antigas do app sem apagar campos existentes.

## Fonte de dados no Firebase

Fonte canônica do checklist mobile:

```text
checklists/{orderId}
```

Campos principais:

| Campo | Uso |
|---|---|
| `orderId` | ID do pedido |
| `providerId` | prestador autenticado que iniciou/preencheu |
| `status` | estágio do checklist |
| `startedAt` | início do atendimento/OS |
| `completedAt` | conclusão do checklist ou fechamento final quando disponível |
| `startLatitude/startLongitude` | GPS de chegada quando permitido |
| `materialsUsed` | indica se houve uso de material/suprimento |
| `materialsDescription` | descrição dos materiais usados |
| `photosBefore` | fotos de chegada/estado inicial |
| `photosDuring` | fotos opcionais da execução |
| `photosAfter` | fotos do reparo finalizado |
| `photoTimestampsBefore/During/After` | horários de upload por fase |

Status do checklist mobile:

| Status | Significado |
|---|---|
| `checklist_pending` | Prestador ainda está preenchendo dados textuais |
| `photos_pending` | Checklist textual salvo, fotos pendentes |
| `ready_for_completion_code` | Fotos mínimas salvas; aguardando código do cliente para finalizar pedido |
| `completed` | Checklist/OS concluído |
| `signatures_pending` | Legado: versões antigas que ainda pediam assinatura |

Storage usado pelo fluxo novo:

```text
checklists/{orderId}/{arquivo}
```

Esse caminho mantém as evidências de OS separadas de imagens genéricas do pedido.

## Painel admin

O painel lê dois formatos:

1. `checklists/{orderId}`: contrato mobile atual.
2. `orders/{orderId}/checklists/{checklistId}`: contrato legado/configurável do dashboard.

O adaptador em `dashboard_admin/lib/services/firebase-checklists.ts` normaliza o documento mobile para `ServiceChecklist`.

Mapeamento principal:

| Mobile | Admin |
|---|---|
| `serviceDescription` | `servicosRealizados` e resposta "Serviços realizados" |
| `preExistingDamages` | `avariasPreExistentes` |
| `materialsUsed/materialsDescription` | resposta de execução "Materiais/suprimentos usados" |
| `problemResolution = resolved` | `statusFechamento = concluido_sucesso` |
| `problemResolution = return_needed` | `statusFechamento = retorno_pendente` |
| `problemResolution = not_resolved` | `statusFechamento = nao_concluido_sem_retorno` |
| `photosBefore/During/After` | galeria `fotos` por fase |
| `ready_for_completion_code` | checklist pronto para validação por código do cliente |

A aba administrativa dedicada fica em:

```text
/dashboard/servicos/checklists
```

Ela lista checklists e permite busca por pedido, cliente, prestador, serviço, status e descrição de material.

## Render

O Render hospeda o backend de pagamentos em `https://aquiresolve.onrender.com`.

O fluxo de checklist não depende de endpoint do Render. A relação indireta é:

- o app Android usa `PAYMENTS_API_BASE_URL` para pagamentos e fechamento financeiro;
- checklist usa Firebase Firestore/Storage diretamente;
- o fechamento final chama a rotina existente que valida o código do cliente e aciona a liquidação no backend quando aplicável.

## Validação recomendada

1. Criar/usar um pedido atribuído a prestador.
2. No app, iniciar a OS como prestador.
3. Confirmar que `checklists/{orderId}` recebe `startedAt`, `providerId` e GPS quando permitido.
4. Preencher checklist textual.
5. Marcar `materialsUsed = true` e confirmar que o app exige `materialsDescription`.
6. Anexar pelo menos uma foto de chegada e uma foto pós-serviço.
7. Confirmar no Storage que os arquivos foram para `checklists/{orderId}/...`.
8. Informar o código único exibido para o cliente e validar que o pedido fecha.
9. Abrir `/dashboard/servicos/checklists` e conferir respostas, fotos, materiais e status.
10. Verificar `https://aquiresolve.onrender.com/api/health` apenas como saúde do backend de pagamentos.
