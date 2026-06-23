# Controle de acesso administrativo

O painel usa permissões em duas camadas:

1. acesso geral aos módulos, como pedidos, usuários, financeiro e relatórios;
2. ações sensíveis, como publicar banners, alterar catálogo, operar pagamentos e gerenciar outros administradores.

As permissões ficam em `adminmaster/master/usuarios/{uid}`. O `uid` deve ser o mesmo usuário do Firebase Authentication. A API valida o ID token do Firebase e busca esse documento no servidor; ausência de perfil, erro de leitura ou permissão falsa negam o acesso. Mesmo a conta cujo email coincide com o Master precisa desse perfil administrativo ativo para entrar no painel comum.

## Banners

Os banners possuem cinco autoridades independentes:

- visualizar;
- criar rascunhos;
- editar conteúdo;
- publicar/despublicar;
- excluir.

Criar um banner já ativo exige simultaneamente `criarBanners` e `publicarBanners`. Toda criação, edição, publicação e exclusão registra o responsável em `admin_audit_logs`.

## Proteção no servidor

Ocultar um botão não é considerado autorização. As APIs de conteúdo, usuários, pedidos, chats, notificações, relatórios financeiros e Pagar.me validam novamente a identidade e a permissão no servidor. Ações como bloquear usuário, cancelar pedido, reembolsar cobrança e pagar prestador também ocultam seus controles para perfis somente de consulta.

## Compatibilidade

Perfis antigos sem campos granulares herdam temporariamente as ações equivalentes dos módulos antigos. Uma negação granular explícita sempre prevalece. `gerenciarAdministradores` nunca é herdada e precisa ser concedida pelo Master.

## Área Master

A Área Master usa cookie de sessão `HttpOnly`, `SameSite=Strict`, com validade de oito horas. Configure no ambiente do servidor:

```env
MASTER_SESSION_SECRET=um-segredo-longo-e-aleatorio
ADMIN_SETUP_TOKEN=outro-token-longo-e-aleatorio
```

O setup inicial não sobrescreve um Master existente. Em produção, ele exige `ADMIN_SETUP_TOKEN`.

## Implantação

Após publicar esta versão:

1. configure os dois segredos no ambiente do painel;
2. entre em `/master`;
3. revise cada administrador e salve as permissões granulares;
4. confirme com uma conta de teste que menus, URLs diretas e APIs negam ações não autorizadas.
