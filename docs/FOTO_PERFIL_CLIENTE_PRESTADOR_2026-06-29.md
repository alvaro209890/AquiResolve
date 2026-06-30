# Foto de perfil visível entre cliente e prestador no pedido — 2026-06-29

## Pedido

O prestador tem foto no perfil; ao solicitar/atender um serviço, a foto deve aparecer
"de um para o outro": o **cliente vê a foto do prestador** e o **prestador vê a foto do
cliente**. Atualizar o que for necessário (regras Firebase etc.).

## O que já existia (não precisou criar)

- **Upload de foto de perfil**: `ProviderProfileFragment` (prestador) e `ProfileActivity`
  (cliente) sobem a imagem para Storage `profile_images/{uid}/...` via
  `FirebaseImageManager` e gravam o **download URL** em `profileImageUrl`.
- **Persistência**: a foto do prestador é gravada em **`users/{uid}.profileImageUrl`**
  e **`providers/{uid}.profileImageUrl`** (mesma foto nos dois perfis); a do cliente em
  `users/{uid}.profileImageUrl`.
- **Cliente vê o prestador**: o card "Prestador Atribuído" da tela de detalhes do pedido
  (`OrderDetailsActivity`, `cardProvider` + `ivProviderPhoto`) já carregava a foto do
  prestador (`loadProviderImage`), e o **chat** já mostrava a foto da contraparte.

## Lacuna corrigida

Na visão do **prestador**, o card de detalhes mostrava o **próprio prestador** (nome do
`assignedProvider`), nunca o **cliente**. Ou seja, o prestador não via a foto/nome de quem
ia atender.

## Mudanças

### App — `OrderDetailsActivity.kt`
- O card da contraparte passou a ser **direcional**:
  - **cliente** → mostra o **prestador** (comportamento atual);
  - **prestador** → mostra o **cliente**: cabeçalho "Cliente", nome = `order.clientName`,
    foto de `users/{clientId}.profileImageUrl` (`loadClientImage`) e a reputação do cliente
    de `users/{clientId}.clientRating` (`loadClientRating`).
- Novos métodos `loadClientImage` / `loadClientRating` (espelham `loadProviderImage` /
  `loadProviderRating`, mas leem a coleção `users`).

### Layout — `activity_order_details.xml`
- O cabeçalho do card ganhou `android:id="@+id/tvCounterpartHeader"` para alternar entre
  "Prestador Atribuído" e "Cliente".

### Regras de Storage — `storage.rules` (publicada)
- `profile_images/{userId}/{fileName}`: **`allow read`** mudou de `isOwner(userId)` para
  **`isSignedIn()`**. Antes, só o dono podia ler a própria foto pela regra; as fotos da
  contraparte só carregavam por causa do token embutido no download URL (frágil). Agora
  qualquer usuário autenticado pode exibir a foto do outro no pedido/chat. **Escrita
  continua exclusiva do dono** (`ownerCanModifyImage`).
- **Publicada** via `firebase deploy --only storage` (service account
  `firebase-adminsdk-fbsvc@…`) → "released rules storage.rules to firebase.storage".

### Regras de Firestore
- Nenhuma mudança necessária: `users` e `providers` já têm `allow read: if isSignedIn()`,
  então cliente e prestador já podem ler o `profileImageUrl`/nota um do outro.

## Como testar

1. Prestador define foto em Perfil; cliente define foto em Perfil.
2. Cliente cria um pedido; quando atribuído, abre o detalhe → card "Prestador Atribuído"
   com **foto do prestador**.
3. Prestador abre o mesmo pedido → card "Cliente" com **foto e nome do cliente** (+ nota
   do cliente, se houver).
4. Chat entre os dois mostra a foto da contraparte (já funcionava).

## Arquivos alterados

- `app/src/main/java/com/aquiresolve/app/OrderDetailsActivity.kt`
- `app/src/main/res/layout/activity_order_details.xml`
- `storage.rules` (publicada em produção)

APK: `app/build/outputs/apk/debug/app-debug.apk` (debug). Exige novo APK nos aparelhos.
