# Sistema de Checklist OS (Ordem de Serviço)

## Visão Geral

O checklist de OS registra a execução do atendimento pelo prestador no app Android e entrega evidências para o painel admin. O fluxo atual remove a assinatura desenhada na tela e finaliza o serviço com o código único exibido apenas ao cliente.

Objetivos:

- registrar chegada, execução e conclusão do atendimento;
- guardar fotos de antes/chegada e pós-serviço;
- registrar materiais/suprimentos usados quando houver;
- validar o encerramento com código do cliente;
- expor tudo no painel admin em uma aba própria de checklists.

## Fluxo Atual

### 1. Início da OS

Ao tocar em **Iniciar OS**, `OrderDetailsActivity`:

1. solicita localização do prestador;
2. chama `FirebaseOrderManager.startService(orderId)` para colocar o pedido em atendimento;
3. chama `FirebaseChecklistManager.startService(orderId, latitude, longitude)`;
4. cria/mescla `checklists/{orderId}` com `status = checklist_pending`;
5. abre `ChecklistActivity`.

### 2. Checklist textual

`ChecklistActivity` registra:

- cliente presente;
- serviço corresponde ao solicitado;
- avarias visíveis e avarias pré-existentes em texto;
- material disponível;
- se materiais/suprimentos foram usados;
- descrição obrigatória dos materiais quando `materialsUsed = true`;
- execução conforme solicitado;
- peças substituídas;
- alteração de valor;
- serviço concluído;
- local limpo;
- serviços realizados;
- resolução do problema;
- relatório técnico e observações.

Ao salvar, o status vai para `photos_pending`.

### 3. Fotos

`PhotoEvidenceActivity` exige:

| Fase | Campo | Obrigatório |
|---|---|---|
| Chegada / antes | `photosBefore` | Sim |
| Durante | `photosDuring` | Não |
| Pós-serviço | `photosAfter` | Sim |

As fotos são enviadas para:

```text
checklists/{orderId}/{arquivo}
```

Depois das fotos mínimas, o status vai para `ready_for_completion_code`.

### 4. Finalização por Código

Após as fotos, o app abre o diálogo de código. O prestador pede ao cliente o código único que aparece apenas no app do cliente. A validação usa:

```kotlin
FirebaseOrderManager.completeOrderWithVerification(orderId, code)
```

Se o código estiver correto:

- o pedido vira `completed`;
- a liquidação segue o fluxo existente;
- `FirebaseChecklistManager.markCompletedByClientCode(orderId)` marca o checklist como `completed`;
- `completionMethod = client_code` é salvo no checklist.

## Status

| Status | Descrição |
|---|---|
| `checklist_pending` | Dados textuais em preenchimento |
| `photos_pending` | Checklist textual salvo; faltam fotos |
| `ready_for_completion_code` | Fotos salvas; aguardando código do cliente |
| `completed` | Checklist finalizado |
| `signatures_pending` | Legado de versões antigas |

## Firestore

Documento canônico:

```text
checklists/{orderId}
```

Campos principais:

| Campo | Descrição |
|---|---|
| `providerId` | prestador que iniciou/preencheu |
| `startedAt` | início do serviço |
| `startLatitude/startLongitude` | GPS de chegada quando autorizado |
| `materialsUsed` | indica uso de material/suprimento |
| `materialsDescription` | descrição dos materiais usados |
| `photosBefore/photosDuring/photosAfter` | URLs das evidências |
| `photoTimestampsBefore/During/After` | horários das fotos |
| `completionMethod` | `client_code` quando finalizado pelo código do cliente |
| `completedAt` | conclusão do checklist |

## Painel Admin

A aba dedicada fica em:

```text
/dashboard/servicos/checklists
```

Ela lista todos os checklists mobile recentes por rota Admin SDK (`/api/checklists`) e permite busca por:

- pedido/protocolo;
- cliente;
- prestador;
- serviço;
- status;
- material usado.

O painel também continua exibindo o checklist dentro do detalhe do pedido por `ServiceChecklistPanel`, normalizando `checklists/{orderId}` para o tipo `ServiceChecklist`.

## Regras

Firestore:

- `checklists/{orderId}` pode ser lido por usuário autenticado;
- criação exige `request.resource.data.orderId == orderId`;
- atualização é permitida a usuário autenticado;
- exclusão é bloqueada.

Storage:

- evidências de OS ficam em `checklists/{orderId}/{arquivo}`;
- leitura e upload de imagem exigem usuário autenticado;
- limite de imagem: 10 MB.

## Validação

1. `./gradlew test`
2. `corepack pnpm exec tsc --noEmit` dentro de `dashboard_admin/`
3. Verificar Render apenas por saúde de infraestrutura: `GET https://aquiresolve.onrender.com/api/health`

Não é necessário deploy do Render para mudanças apenas no checklist mobile/painel/Firebase rules.
