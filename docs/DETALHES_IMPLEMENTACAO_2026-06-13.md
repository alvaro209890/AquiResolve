# Detalhes da Implementação — 13/06/2026

Este documento detalha as alterações realizadas no projeto entre o último commit e a presente data, focando em melhorias de robustez no rastreamento de localização do prestador, validações de integridade de pedidos no app Android e correções na integração com a API Pagar.me v5.

## 📱 App Android (Kotlin)

### 📍 Rastreamento de Localização e Foreground Service
- **Melhoria no Ciclo de Vida do Serviço:** O `ProviderLocationForegroundService` agora realiza verificações rigorosas de permissão e estado do GPS (habilitado/desabilitado) antes de iniciar. Adicionado suporte ao tipo `FOREGROUND_SERVICE_TYPE_LOCATION` para compatibilidade com Android 10+.
- **Gestão de Rastreamento na Home:** A `ProviderHomeActivity` agora gerencia o início/parada do rastreamento de forma inteligente. O rastreamento só ocorre se:
    1. O usuário estiver autenticado.
    2. O prestador estiver com status `APPROVED`.
    3. O prestador estiver marcado como disponível (`isAvailable`) **OU** possuir um pedido ativo (`assigned` ou `in_progress`).
    4. Permissões de localização e notificações (Android 13+) foram concedidas.
    5. O GPS do dispositivo estiver ligado.
- **Cálculo de Rota em Tempo Real:** Na `OrderDetailsActivity`, o cálculo de distância e rota agora solicita permissões de forma contextual e informa o usuário caso o GPS precise ser ativado.

### 🛡️ Validações de Integridade e UX
- **Bloqueio de Pedidos sem Coordenadas:** Impedida a criação de pedidos (`CreateOrderActivity`) e a aceitação de pedidos (`ProviderOrdersFragment`) que não possuam coordenadas geográficas válidas. Isso evita falhas no cálculo de rota e exibição no mapa.
- **Normalização de Status:** No `ProviderOrdersAdapter`, a comparação de status agora é case-insensitive e suporta novos estados de fluxo (`distributing`, `assigned`, `in_progress`), garantindo que a interface reflita corretamente o estado do pedido.
- **Limpeza de Recursos:** Adicionada limpeza explícita do componente de mapa (`onDetach`) no `onDestroy` da `OrderDetailsActivity` para evitar memory leaks.

## 🛠️ Dashboard Administrativo (Next.js / TypeScript)

### 💳 Integração Pagar.me v5
- **Correção de Autenticação:** Alterado o esquema de autenticação da API Pagar.me de `Bearer` para `Basic Auth` (conforme especificação oficial da v5 que exige a chave de API como username e senha vazia, codificados em Base64).
- **Consulta de Saldo por Recebedor:** A função `getBalance` foi corrigida para não consultar o endpoint genérico `/balance` (que muitas vezes retorna erro na v5 se não houver um contexto de conta principal), passando a identificar o ID do recebedor padrão e consultar o saldo específico vinculado a ele (`/recipients/{id}/balance`).
- **Robustez no Parsing de Respostas:** Melhorado o tratamento de respostas da API para lidar com corpos vazios e estruturas de paginação, evitando crashes em casos de retornos inesperados.

## 🚀 Próximos Passos
- Monitorar a taxa de sucesso de rastreamento em background em dispositivos com otimização de bateria agressiva.
- Validar a conciliação bancária automática com o novo fluxo de consulta de saldo no dashboard.
