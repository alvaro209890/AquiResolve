# Correções de compatibilidade Android e Play — 14/07/2026

## Contexto

A verificação de qualidade do APK identificou erros no lint que não interrompiam a
compilação porque o projeto estava configurado com `abortOnError false`. Esta manutenção
elimina todos os itens classificados como **erro** no relatório de lint do módulo `app`.

## Correções aplicadas

1. **AlertForegroundService em Android 7/7.1 (API 24–25)**
   - `startForegroundService`, canais de notificação e o construtor de `Notification`
     com canal são APIs do Android 8 (API 26).
   - O serviço agora usa `startService` antes do Android 8, cria canal somente a partir
     da API 26 e monta a notificação com `NotificationCompat`.
   - Isso evita falha ao iniciar alertas em dispositivos compatíveis com o `minSdk 24`.

2. **Tela Novo Pedido**
   - `android:fillViewport` e `tools:context` estavam fora da tag de abertura do
     `ScrollView`, sendo interpretados como texto inesperado.
   - Os atributos foram devolvidos à tag correta, restaurando o comportamento esperado
     do `ScrollView`.

3. **Tintas de ícones**
   - Os 21 usos de `android:tint` foram migrados para `app:tint` nos layouts afetados.
   - Assim, os ícones usam a camada AppCompat e preservam a cor em versões antigas do
     Android suportadas pelo app.

4. **Câmera opcional no ChromeOS**
   - Foi declarada a feature de câmera como `required=false`; o app pode ser instalado
     em dispositivos sem câmera, mantendo a funcionalidade de seleção de mídia.

## Validação obrigatória

```bash
./gradlew lint test assembleDebug bundleRelease --no-daemon \\
  -Dorg.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64
```

Critérios esperados:

- Nenhum item com severidade `Error` em `app/build/reports/lint-results-debug.xml`.
- Testes unitários, APK debug e AAB release gerados com sucesso.
- O manifesto mesclado continua sem permissões `READ_MEDIA_*` ou
  `READ_EXTERNAL_STORAGE`, conforme a política de mídia da Google Play.

## Observação

O relatório de lint ainda contém avisos não bloqueantes (por exemplo, textos
hardcoded, acessibilidade e otimizações de layout). Eles não eram erros de execução
nem de publicação e devem ser tratados em uma manutenção de qualidade separada.
