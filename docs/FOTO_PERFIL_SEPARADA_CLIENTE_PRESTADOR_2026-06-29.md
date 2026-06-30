# Fotos de perfil realmente separadas: cliente x prestador (mesma conta) — 2026-06-29

## Sintoma reportado

Mesmo após a entrega anterior, ao trocar a foto **no perfil do prestador a foto do
cliente também mudava** (e vice-versa). O usuário quer fotos **independentes** na **mesma
conta**: uma para a conta de prestador, outra para a conta de cliente.

## Causa raiz

Cada tela de perfil ainda **gravava nas duas coleções** ("mesma foto para ambos os
perfis"):

- **`ProfileActivity` (cliente)** — `updateProfileImageInFirestore` gravava em
  `users/{uid}.profileImageUrl` **e** chamava `FirebaseProviderManager.updateProfileImage`
  → `providers/{uid}.profileImageUrl`. Logo, trocar a foto de **cliente** sobrescrevia a
  de **prestador**.
- **`ProviderProfileFragment` (prestador)** — já havia sido corrigido para gravar só em
  `providers/`, mas sua tela **exibia** a foto de cliente como *fallback* quando o
  prestador não tinha foto própria (`providerPhoto ?: localUser.profileImageUrl`),
  dando a impressão visual de que as fotos estavam ligadas.

`FirebaseAuthManager.updateUserProfileImage` (usado pelo cliente) grava só em `users/` +
cache local — não era a causa. O cruzamento vinha das telas.

## Correção

- **`ProfileActivity` (cliente)**: `updateProfileImageInFirestore` agora grava **somente
  em `users/{uid}.profileImageUrl`**. Removido o bloco que também escrevia em
  `providers/{uid}`.
- **`ProviderProfileFragment` (prestador)**:
  - upload continua indo para arquivo dedicado `provider_{uid}.jpg` e gravando **só em
    `providers/{uid}.profileImageUrl`** (entrega anterior);
  - a tela **deixou de usar a foto de cliente como fallback**: exibe apenas
    `providers/{uid}.profileImageUrl` (ou o placeholder padrão se vazio). Assim a tela do
    prestador nunca mais reflete a foto de cliente.

### Resultado
| Ação | users/ (cliente) | providers/ (prestador) |
|---|---|---|
| Trocar foto na tela **cliente** | muda | **inalterada** |
| Trocar foto na tela **prestador** | **inalterada** | muda |

Cada conta passa a ter sua própria foto, mesmo sendo o mesmo usuário Firebase.

### Onde o outro lado vê
- **Cliente vê o prestador** no pedido/chat: lê `providers/{uid}.profileImageUrl` e, se
  vazio, cai para `users/{uid}` — *fallback de exibição para terceiros* mantido, para o
  cliente sempre ver um rosto. (Isso não cria vínculo de escrita; é só leitura.)
- **Prestador vê o cliente**: lê `users/{clientId}.profileImageUrl`.

## Backend / regras
**Nenhuma mudança necessária.** A correção é 100% no app (isolamento de escrita):
- Storage `profile_images/{uid}/...` já permite o dono gravar os dois arquivos
  (`profile_{uid}.jpg` e `provider_{uid}.jpg`) — `read: isSignedIn()` / `write` do dono.
- Firestore `users`/`providers` já são `read: isSignedIn()`.
Não houve deploy de Vercel nem de regras nesta entrega.

## Dados existentes
A correção evita cruzamentos **futuros**. Contas que hoje têm a mesma foto nas duas
coleções continuam iguais até que o usuário troque uma delas — a partir daí divergem,
como esperado. Não é necessária migração.

## Arquivos alterados
- `app/src/main/java/com/aquiresolve/app/ProfileActivity.kt`
- `app/src/main/java/com/aquiresolve/app/ProviderProfileFragment.kt`

## Verificação
`:app:assembleDebug` → BUILD SUCCESSFUL.
APK: `app/build/outputs/apk/debug/app-debug.apk` (debug). Exige novo APK nos aparelhos.
