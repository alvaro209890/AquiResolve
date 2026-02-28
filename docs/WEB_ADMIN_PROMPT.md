# 🎯 PROMPT PARA CURSOR.AI - PÁGINA WEB DE ADMINISTRAÇÃO

## 📋 **CONTEXTO DO PROJETO**

Você precisa criar uma **página web de administração completa** para um aplicativo Android de prestação de serviços. O aplicativo conecta **clientes** que precisam de serviços com **prestadores** que oferecem esses serviços.

### 🏗️ **ARQUITETURA DO SISTEMA**

**Aplicativo Android:**
- **Clientes**: Usuários que solicitam serviços
- **Prestadores**: Profissionais que oferecem serviços
- **Firebase**: Backend (Auth, Firestore, Storage, Analytics)
- **Funcionalidades**: Login, cadastro, pedidos, chat, pagamentos, avaliações

**Página Web de Administração:**
- **Dashboard**: Visão geral do sistema
- **Gestão de Usuários**: Clientes e prestadores
- **Gestão de Pedidos**: Monitoramento e controle
- **Relatórios**: Analytics e métricas
- **Configurações**: Sistema e segurança

---

## 🎨 **DESIGN E INTERFACE**

### **Tecnologias Recomendadas:**
- **Frontend**: React.js + TypeScript
- **UI Framework**: Material-UI (MUI) ou Ant Design
- **Estilização**: CSS Modules ou Styled Components
- **Responsividade**: Mobile-first design
- **Tema**: Escuro/Claro toggle

### **Paleta de Cores:**
```css
/* Cores principais */
--primary: #2196F3;      /* Azul principal */
--secondary: #FF9800;    /* Laranja secundário */
--success: #4CAF50;      /* Verde sucesso */
--warning: #FFC107;      /* Amarelo aviso */
--error: #F44336;        /* Vermelho erro */
--info: #00BCD4;         /* Ciano info */

/* Cores neutras */
--background: #FAFAFA;
--surface: #FFFFFF;
--text-primary: #212121;
--text-secondary: #757575;
--border: #E0E0E0;
```

### **Layout:**
- **Sidebar**: Navegação principal (colapsável)
- **Header**: Logo, notificações, perfil admin
- **Main Content**: Área de trabalho principal
- **Footer**: Informações do sistema

---

## 📱 **FUNCIONALIDADES PRINCIPAIS**

### **1. 🔐 SISTEMA DE AUTENTICAÇÃO**

**Login Administrativo:**
```typescript
interface AdminLogin {
  email: string;
  password: string;
  rememberMe?: boolean;
}
```

**Recursos:**
- Login seguro com JWT
- Recuperação de senha
- Sessão persistente
- Logout automático por inatividade
- Autenticação de dois fatores (opcional)

### **2. 📊 DASHBOARD PRINCIPAL**

**Métricas em Tempo Real:**
- Total de usuários (clientes + prestadores)
- Pedidos ativos/pendentes/concluídos
- Receita total do mês
- Taxa de satisfação média
- Novos cadastros (últimos 7 dias)
- Pedidos por categoria de serviço

**Gráficos Interativos:**
- Gráfico de linha: Pedidos por dia/semana/mês
- Gráfico de pizza: Distribuição por categoria
- Gráfico de barras: Top prestadores
- Mapa de calor: Atividade por região

### **3. 👥 GESTÃO DE USUÁRIOS**

**Lista de Clientes:**
```typescript
interface Client {
  id: string;
  name: string;
  email: string;
  phone: string;
  cpf: string;
  address: string;
  createdAt: Date;
  lastLogin: Date;
  status: 'active' | 'inactive' | 'blocked';
  totalOrders: number;
  totalSpent: number;
}
```

**Lista de Prestadores:**
```typescript
interface Provider {
  id: string;
  name: string;
  email: string;
  phone: string;
  cpf: string;
  address: string;
  serviceCategories: string[];
  experience: string;
  isVerified: boolean;
  rating: number;
  totalOrders: number;
  totalEarnings: number;
  status: 'active' | 'inactive' | 'pending' | 'blocked';
  documents: Document[];
}
```

**Funcionalidades:**
- Busca e filtros avançados
- Visualização de perfil completo
- Edição de dados
- Bloqueio/desbloqueio de usuários
- Verificação de documentos
- Histórico de atividades
- Exportação de dados (CSV/Excel)

### **4. 📋 GESTÃO DE PEDIDOS**

**Lista de Pedidos:**
```typescript
interface Order {
  id: string;
  clientId: string;
  clientName: string;
  providerId?: string;
  providerName?: string;
  serviceCategory: string;
  description: string;
  status: 'pending' | 'assigned' | 'in_progress' | 'completed' | 'cancelled';
  priority: 'low' | 'medium' | 'high' | 'urgent';
  budget: number;
  location: string;
  createdAt: Date;
  assignedAt?: Date;
  completedAt?: Date;
  rating?: number;
  payment: PaymentInfo;
}
```

**Funcionalidades:**
- Filtros por status, categoria, data
- Atribuição manual de prestadores
- Acompanhamento em tempo real
- Chat integrado
- Histórico de mudanças
- Relatórios de performance

### **5. 💰 GESTÃO FINANCEIRA**

**Transações:**
```typescript
interface Transaction {
  id: string;
  orderId: string;
  clientId: string;
  providerId: string;
  amount: number;
  commission: number;
  status: 'pending' | 'completed' | 'failed' | 'refunded';
  paymentMethod: string;
  createdAt: Date;
  completedAt?: Date;
}
```

**Funcionalidades:**
- Dashboard financeiro
- Relatórios de receita
- Gestão de comissões
- Histórico de pagamentos
- Exportação de relatórios
- Integração com sistemas de pagamento

### **6. 📈 RELATÓRIOS E ANALYTICS**

**Relatórios Disponíveis:**
- Performance de prestadores
- Satisfação do cliente
- Categorias mais solicitadas
- Análise de receita
- Crescimento de usuários
- Análise geográfica
- Tempo médio de atendimento

**Exportação:**
- PDF personalizado
- Excel com gráficos
- CSV para análise externa
- Agendamento de relatórios

### **7. ⚙️ CONFIGURAÇÕES DO SISTEMA**

**Configurações Gerais:**
- Informações da empresa
- Configurações de email
- Taxas de comissão
- Categorias de serviço
- Regiões de atendimento

**Configurações de Segurança:**
- Política de senhas
- Configurações de sessão
- Logs de auditoria
- Backup automático

---

## 🔧 **INTEGRAÇÃO COM FIREBASE**

### **Estrutura do Firestore:**
```javascript
// Coleções principais
/users/{userId}           // Dados dos usuários
/providers/{providerId}   // Dados dos prestadores
/orders/{orderId}         // Pedidos de serviço
/transactions/{txId}      // Transações financeiras
/ratings/{ratingId}       // Avaliações
/chats/{chatId}           // Conversas
/documents/{docId}        // Documentos dos prestadores
/admin/{adminId}          // Dados administrativos
```

### **APIs Necessárias:**
```typescript
// Autenticação
POST /api/auth/login
POST /api/auth/logout
POST /api/auth/refresh

// Usuários
GET /api/users?page=1&limit=20&search=...
GET /api/users/{id}
PUT /api/users/{id}
DELETE /api/users/{id}

// Prestadores
GET /api/providers?status=active&category=...
GET /api/providers/{id}
PUT /api/providers/{id}/verify
PUT /api/providers/{id}/block

// Pedidos
GET /api/orders?status=pending&date=...
GET /api/orders/{id}
PUT /api/orders/{id}/assign
PUT /api/orders/{id}/status

// Relatórios
GET /api/reports/dashboard
GET /api/reports/users
GET /api/reports/orders
GET /api/reports/financial
```

---

## 🚀 **REQUISITOS TÉCNICOS**

### **Performance:**
- Carregamento inicial < 3 segundos
- Navegação entre páginas < 1 segundo
- Suporte a 1000+ usuários simultâneos
- Cache inteligente de dados

### **Segurança:**
- Autenticação JWT
- HTTPS obrigatório
- Validação de entrada
- Sanitização de dados
- Rate limiting
- Logs de auditoria

### **Responsividade:**
- Desktop: 1200px+
- Tablet: 768px - 1199px
- Mobile: < 768px
- Touch-friendly em mobile

### **Acessibilidade:**
- WCAG 2.1 AA compliance
- Navegação por teclado
- Screen reader support
- Alto contraste
- Tamanhos de fonte ajustáveis

---

## 📁 **ESTRUTURA DE ARQUIVOS SUGERIDA**

```
src/
├── components/
│   ├── layout/
│   │   ├── Sidebar.tsx
│   │   ├── Header.tsx
│   │   ├── Footer.tsx
│   │   └── Layout.tsx
│   ├── dashboard/
│   │   ├── MetricsCard.tsx
│   │   ├── Chart.tsx
│   │   └── RecentActivity.tsx
│   ├── users/
│   │   ├── UserList.tsx
│   │   ├── UserCard.tsx
│   │   └── UserModal.tsx
│   ├── orders/
│   │   ├── OrderList.tsx
│   │   ├── OrderCard.tsx
│   │   └── OrderDetails.tsx
│   └── common/
│       ├── Loading.tsx
│       ├── ErrorBoundary.tsx
│       └── ConfirmDialog.tsx
├── pages/
│   ├── Dashboard.tsx
│   ├── Users.tsx
│   ├── Providers.tsx
│   ├── Orders.tsx
│   ├── Reports.tsx
│   └── Settings.tsx
├── services/
│   ├── api.ts
│   ├── auth.ts
│   ├── users.ts
│   ├── orders.ts
│   └── reports.ts
├── hooks/
│   ├── useAuth.ts
│   ├── useUsers.ts
│   └── useOrders.ts
├── utils/
│   ├── constants.ts
│   ├── helpers.ts
│   └── validators.ts
├── types/
│   ├── user.ts
│   ├── order.ts
│   └── api.ts
└── styles/
    ├── globals.css
    ├── theme.ts
    └── components.css
```

---

## 🎯 **ENTREGÁVEIS ESPERADOS**

### **1. Código Fonte Completo:**
- React.js + TypeScript
- Componentes reutilizáveis
- Hooks customizados
- Serviços de API
- Tipagem completa

### **2. Documentação:**
- README.md detalhado
- Guia de instalação
- Documentação da API
- Guia de deploy

### **3. Funcionalidades:**
- ✅ Sistema de login
- ✅ Dashboard com métricas
- ✅ Gestão de usuários
- ✅ Gestão de pedidos
- ✅ Relatórios básicos
- ✅ Configurações

### **4. Qualidade:**
- Código limpo e bem documentado
- Testes unitários
- Tratamento de erros
- Loading states
- Feedback visual

---

## 🔥 **BÔNUS E EXTRAS**

### **Funcionalidades Avançadas:**
- Notificações em tempo real (WebSocket)
- Chat integrado para suporte
- Upload de documentos
- Sistema de backup automático
- Integração com WhatsApp Business
- App mobile para admin (React Native)

### **Melhorias de UX:**
- Animações suaves
- Skeleton loading
- Infinite scroll
- Drag & drop
- Keyboard shortcuts
- Dark mode toggle

### **Analytics Avançados:**
- Google Analytics 4
- Heatmaps
- User journey tracking
- A/B testing
- Predictive analytics

---

## 📞 **INFORMAÇÕES DE CONTATO**

**Dados do Projeto:**
- **Nome**: AppServiço
- **Tipo**: Aplicativo de prestação de serviços
- **Backend**: Firebase (Auth, Firestore, Storage)
- **Usuários**: Clientes e Prestadores
- **Idioma**: Português (Brasil)

**Credenciais Firebase:**
- **Project ID**: gasprojeto-b6797
- **Storage Bucket**: gasprojeto-b6797.appspot.com
- **Web App ID**: 1:700301197838:web:1234567890abcdef

---

## 🎨 **INSPIRAÇÃO DE DESIGN**

**Referências:**
- Google Admin Console
- Firebase Console
- Stripe Dashboard
- Shopify Admin
- Notion

**Estilo Visual:**
- Minimalista e profissional
- Foco na usabilidade
- Cores neutras com acentos
- Tipografia clara
- Espaçamento generoso

---

**🚀 Crie uma interface administrativa moderna, funcional e escalável que permita gerenciar todo o ecossistema do aplicativo de forma eficiente e intuitiva!** 