# A fazer amanhã — AquiResolve

Passos manuais que ficaram pendentes após a auditoria de 2026-06-13
(ver `docs/AUDITORIA_E_CORRECOES_2026-06-13.md`). Tudo que era código/regras/variáveis
já foi corrigido, testado e enviado ao `main`. Estes 3 itens dependem de você.

---

## 1. ⭐ Trocar o `google-services.json` real (obrigatório para rodar no aparelho)

O arquivo `app/google-services.json` no repositório é um **placeholder de 722 bytes** — só
serve para compilar. Com ele o app **não conecta** ao Firebase real (login, Firestore, push).

**O que fazer:**
1. Firebase Console → projeto **aplicativoservico-143c2** → ⚙️ Configurações do projeto → aba
   **Seus apps** → app Android `com.aquiresolve.app`.
2. Botão **"google-services.json"** (download).
3. Substituir o arquivo em `app/google-services.json` pelo baixado.
4. Gerar o APK e instalar no aparelho:
   ```bash
   cd ~/Documentos/app
   ./gradlew :app:assembleDebug
   # APK em app/build/outputs/apk/debug/app-debug.apk
   ```

> ⚠️ **Não** comitar o `google-services.json` real — ele já está no `.gitignore`.

---

## 2. (Opcional, segurança) Ativar o segredo do webhook Pagar.me

Hoje o webhook funciona **sem** segredo compartilhado, e o pagamento já é confirmado pelo
polling de 5s do app (não há risco de pedido pago ficar sem confirmar). O segredo só adiciona
uma camada extra de segurança.

> ⚠️ **Cuidado:** defina o segredo nos **dois lados ao mesmo tempo**. Se colocar só no Render,
> o backend passa a **rejeitar (401)** todos os webhooks do Pagar.me.

**O que fazer:**
1. Escolher um segredo forte (ex.: gerar com `openssl rand -hex 24`).
2. **Render** → serviço do backend → Environment → adicionar:
   `PAGARME_WEBHOOK_SECRET=<seu-segredo>` → salvar (redeploy automático).
3. **Painel Pagar.me** → Configurações → Webhooks → cadastrar/editar a URL:
   ```
   https://aquiresolve.onrender.com/api/payments/webhook/pagarme?secret=<seu-segredo>
   ```
   (eventos: `charge.paid`, `charge.payment_failed`, `charge.refunded`, `order.paid`).

---

## 3. Publicação na Play Store (quando quiser lançar)

Precisa da sua conta do **Google Play Console**.

**Gerar o AAB assinado:**
```bash
cd ~/Documentos/app
./gradlew bundleRelease
# AAB em app/build/outputs/bundle/release/app-release.aab
```
(o keystore de assinatura já existe em `keystore/`.)

**Depois, no Play Console:** subir o AAB, preencher ficha da loja (descrição, screenshots),
política de privacidade hospedada e enviar para revisão.

---

## Já resolvido (não precisa fazer nada) ✅

- Segurança do catálogo (escrita só via Admin SDK) — corrigido e regras já no ar.
- Reembolso no painel, reconciliação de webhook, chat na Central Operacional.
- Catálogo dinâmico no app + semeado no Firestore.
- Variáveis do Render conferidas (todas corretas).
- Builds do painel e do APK verdes; tudo no `main`.
