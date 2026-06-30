# Validação e correção — Avaliação bidirecional (prestador ↔ cliente) — 2026-06-29

## Pedido

"Validar toda a lógica de avaliação de prestador e cliente, ver por que não
funciona e corrigir."

## Como validei (ponta a ponta)

Revisei as 4 camadas:

1. **Activities** (`RatingActivity` cliente→prestador, `ClientRatingActivity`
   prestador→cliente) — ambas registradas no `AndroidManifest`, coletam nota geral +
   notas detalhadas + tags e chamam o manager corretamente.
2. **Manager** (`FirebaseOrderManager.submitOrderRating` / `submitClientRating` +
   recálculos `updateProviderAverageRatingFromOrders` / `updateClientAverageRatingFromReviews`).
3. **Firestore rules** — comparei o `firestore.rules` local com o **ruleset publicado em
   produção** (via REST `firebaserules.googleapis.com`): **idênticos** (29.507 bytes) e
   contêm todos os ramos de avaliação. Ou seja, **regra não era o problema** (diferente
   do bug histórico de banners/combos).
4. **Fluxo de UI** que dispara cada avaliação (`OrderDetailsActivity`).

### Resultado da validação

| Direção | Backend (regras+manager) | UI / acionamento | Status |
|---|---|---|---|
| **Cliente → Prestador** | OK | Botão **persistente** "Avaliar Serviço" no pedido concluído; respeita "já avaliado" (`order.rating`) | ✅ Funciona |
| **Prestador → Cliente** | OK | Disparo **one-shot** só logo após "Finalizar com código" | ❌ Inacessível na prática |

## Causa raiz (por que "não funcionava")

O backend dos dois lados estava correto. O problema era **só de acionamento da UI**, e
**assimétrico**:

- O cliente tem um ponto de entrada **persistente**: enquanto o pedido concluído não foi
  avaliado, o botão principal é "Avaliar Serviço". Funciona sempre.
- O prestador **não tinha** ponto de entrada persistente. A tela de avaliar o cliente
  (`ClientRatingActivity`) só era aberta **uma única vez**, dentro de
  `finishServiceWithCode`, imediatamente após o prestador finalizar o serviço com o
  código do cliente (`launchClientRating`). Consequências:
  1. Se o prestador tocasse em **"Pular"** ou voltasse, **nunca mais** conseguia avaliar.
  2. Se o pedido fosse concluído pela **dupla confirmação** (`confirmCompletion`, sem
     código), o prestador **jamais** era convidado a avaliar.
  3. O botão do pedido concluído do prestador era só "Ver OS"/"Ver Detalhes" — sem
     qualquer caminho de avaliação.

## Correção (app — `OrderDetailsActivity.kt`)

Dei ao prestador o **mesmo padrão persistente** do cliente:

1. Novo estado `hasRatedClientCached`, carregado de `client_reviews` (via
   `FirebaseOrderManager.hasRatedClient(orderId)`) sempre que um pedido **concluído** é
   aberto na **visão do prestador** (`loadProviderRatedClientState`).
2. **Rótulo do botão** (pedido concluído, prestador):
   - ainda não avaliou → **"Avaliar Cliente"** (secundário "Ver OS" quando há OS);
   - já avaliou → "Ver OS"/"Ver Detalhes" (comportamento antigo).
3. **Ação principal**: se ainda não avaliou → abre `ClientRatingActivity`; senão →
   histórico da OS / chat.
4. **Secundário "Ver OS"** continua acessível mesmo com o primário virando "Avaliar
   Cliente" (cor neutra, não-destrutiva).
5. O launcher de resultado passou a distinguir os dois cenários via
   `returnHomeAfterClientRating`:
   - **fluxo one-shot** pós-conclusão por código → volta para a Home do prestador (como antes);
   - **botão persistente** → permanece na tela e o botão vira "Ver OS" ao concluir
     (atualiza `hasRatedClientCached`).

Nenhuma mudança de regra Firestore foi necessária (as regras de avaliação já estavam
corretas e publicadas). Não há mudança no manager — o `submitClientRating`/`hasRatedClient`
já existiam; o que faltava era **chamá-los de um lugar acessível**.

## Arquivos alterados

- `app/src/main/java/com/aquiresolve/app/OrderDetailsActivity.kt`

## Como testar

1. Concluir um pedido (qualquer caminho: código **ou** dupla confirmação).
2. Como **prestador**, abrir o pedido concluído → botão **"Avaliar Cliente"** presente.
3. Avaliar → grava em `client_reviews/{orderId}` e recalcula `users/{clientId}.clientRating`;
   reabrindo, o botão vira **"Ver OS"** (não pede avaliar de novo).
4. Pular e reabrir → **"Avaliar Cliente"** continua disponível (não some mais).
5. Como **cliente**, o fluxo "Avaliar Serviço" permanece igual (já funcionava).

APK: `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
