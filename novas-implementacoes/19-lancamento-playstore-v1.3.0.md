# Lançamento Play Store v1.3.0 (2026-07-04)

## Resumo

AAB enviado para análise da Google Play Store em 04/07/2026.

## Versão

| Campo | Valor |
|---|---|
| versionCode | 20260704 |
| versionName | 1.3.0 |
| applicationId | com.aquiresolve.app |

## Build

- `./gradlew clean :app:bundleRelease`
- Assinado com upload-keystore.jks
- R8 + shrinkResources ativos
- Tamanho: 9,4 MB

## Status dos Webhooks Pagar.me

**Não incluídos neste lançamento.** O webhook (`POST /api/payments/webhook/pagarme`) não está configurado (URL não cadastrada no dashboard Pagar.me, env var `PAGARME_WEBHOOK_SECRET` ausente no Render).

Impacto sem webhook:
- Cartão de crédito: funciona 100% (backend sincroniza instantaneamente)
- PIX com app aberto: funciona (polling a cada 5s)
- PIX após fechar app: **quebra** — pedido fica `awaiting_payment` para sempre
- Chargeback/reembolso: não sincroniza automaticamente

Recomendação: ativar webhook o quanto antes.

## Arquivo

`/home/acer/Documentos/Aqui_Resolve/app/build/outputs/bundle/release/app-release.aab`
