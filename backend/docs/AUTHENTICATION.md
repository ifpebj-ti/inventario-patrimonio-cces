# Autenticação

O Inventarium usa **exclusivamente login com Google** (OAuth 2.0 / OpenID Connect, fluxo de ID
token). Não existe usuário/senha: não há cadastro próprio, verificação de e-mail nem recuperação
de senha. Toda conta nasce e se autentica pelo Google.

Este documento explica como o fluxo funciona, onde cada peça está no código, e o que falta
configurar para rodar em produção.

## Índice

- [Visão geral do fluxo](#visão-geral-do-fluxo)
- [Onde cada peça está no código](#onde-cada-peça-está-no-código)
- [Configuração](#configuração)
- [Modelo de dados](#modelo-de-dados)
- [Contrato HTTP](#contrato-http)
- [Decisões de segurança e por quê](#decisões-de-segurança-e-por-quê)
- [Checklist para produção](#checklist-para-produção)
- [Limitações conhecidas](#limitações-conhecidas)

---

## Visão geral do fluxo

```
Frontend (Google Identity Services)
        │
        │ 1. Usuário clica no botão do Google
        │ 2. Google autentica e devolve um ID token (JWT assinado pelo Google)
        ▼
POST /auth/google  { "credential": "<ID token>" }
        │
        │ 3. Backend valida a assinatura contra o JWKS do Google
        │    e confere iss, aud, exp e email_verified
        │ 4. Localiza o usuário pelo e-mail, ou cria um novo
        │    (se o domínio for permitido)
        │ 5. Emite o JWT próprio da aplicação (HS256, 24h)
        ▼
200 { "token": "<JWT da aplicação>", "user": { id, name, email } }
        │
        │ 6. Frontend guarda o token num cookie e passa a
        │    enviá-lo como Authorization: Bearer em cada requisição
        ▼
GET /auth/me, GET /inventory/..., etc.
        │
        │ 7. SecurityFilter valida o JWT da aplicação (não mais o do Google)
        ▼
Requisição autenticada
```

Dois tokens diferentes estão em jogo, e é importante não confundi-los:

| | Emitido por | Algoritmo | Validade | Onde é validado |
|---|---|---|---|---|
| ID token do Google | Google | RS256 | ~1h, mas usado uma vez só | `GoogleTokenProvider` (só no `POST /auth/google`) |
| JWT da aplicação | Backend (`AuthenticationService`) | HS256 | 24h | `JWTProvider` / `SecurityFilter` (em toda outra rota protegida) |

O ID token do Google nunca é reenviado pelo frontend depois do login — ele é trocado uma vez pelo
JWT da aplicação, que é o que sustenta a sessão pelas próximas 24h.

---

## Onde cada peça está no código

| Peça | Arquivo | Responsabilidade |
|---|---|---|
| Endpoint de login | [`controller/AuthController.java`](../src/main/java/clp/inventory/controller/AuthController.java) | `POST /auth/google` e `GET /auth/me` — os dois únicos endpoints de autenticação. |
| Validação do ID token do Google | [`providers/GoogleTokenProvider.java`](../src/main/java/clp/inventory/providers/GoogleTokenProvider.java) | Assinatura via JWKS do Google (`NimbusJwtDecoder`), `iss`, `aud`, `exp`/`nbf` (60s de tolerância) e `email_verified`. |
| Emissão e validação do JWT da aplicação | [`service/auth/AuthenticationService.java`](../src/main/java/clp/inventory/service/auth/AuthenticationService.java) (emite) / [`providers/JWTProvider.java`](../src/main/java/clp/inventory/providers/JWTProvider.java) (valida no filtro) | HS256 assinado com `security.token.secret`, `iss=inventory`, `sub=<id do usuário>`, expiração de 24h. |
| Find-or-create do usuário | [`service/UserService.java`](../src/main/java/clp/inventory/service/UserService.java) `findOrCreateGoogleUser` | Busca por e-mail (normalizado em minúsculas); cria conta nova se o domínio for permitido. |
| Cadeia de segurança HTTP | [`security/SecurityConfig.java`](../src/main/java/clp/inventory/security/SecurityConfig.java) | `permitAll` só em `/error` e `/auth/google`; todo o resto exige Bearer válido. `SessionCreationPolicy.STATELESS`. |
| Filtro de autenticação | [`security/SecurityFilter.java`](../src/main/java/clp/inventory/security/SecurityFilter.java) | Roda em toda requisição, exceto `/auth/google` (`shouldNotFilter`); valida o Bearer e popula o `SecurityContext`. |
| Entidade de usuário | [`model/User.java`](../src/main/java/clp/inventory/model/User.java) | `id`, `name`, `email`, `googleId` (`NOT NULL`, `@JsonIgnore`), `createdAt`, `updatedAt`. |
| Schema | [`db/changelog/changes/0.0.xml`](../src/main/resources/db/changelog/changes/0.0.xml) | Tabela `im_user` — sem colunas de senha, sem tabela de tokens de verificação/reset. |

---

## Configuração

> Para o passo a passo de como gerar cada valor (incluindo a criação do Client ID no Google
> Cloud Console, clique a clique), veja [`GOOGLE_AUTH_SETUP.md`](./GOOGLE_AUTH_SETUP.md). Esta
> seção documenta **o que** cada variável faz; aquele guia documenta **como** obtê-la.

Três variáveis de ambiente, documentadas em [`.env.example`](../../.env.example) na raiz do
repositório:

| Variável | Obrigatória | Efeito se ausente |
|---|---|---|
| `SECURITY_TOKEN_SECRET` | Sim | A aplicação **recusa subir** (`security.token.secret=${SECURITY_TOKEN_SECRET}`, sem default). |
| `GOOGLE_OAUTH_CLIENT_ID` | Sim, para o login funcionar | `POST /auth/google` responde **500** (`IllegalStateException` em `GoogleTokenProvider`). A aplicação sobe normalmente, só o login que fica indisponível. |
| `GOOGLE_ALLOWED_DOMAINS` | Não | Lista vazia libera **qualquer** domínio de e-mail para criar conta nova. |

### `SECURITY_TOKEN_SECRET`

Chave HMAC que assina o JWT da própria aplicação. Gerar com:

```bash
openssl rand -hex 32
```

Não existe default nem em `application.properties` nem em `docker-compose.yml` — é proposital.
Um segredo fraco ou previsível permite forjar um token para qualquer `sub` (qualquer usuário),
tornando a validação cuidadosa do Google inteiramente inócua.

### `GOOGLE_OAUTH_CLIENT_ID`

O client ID OAuth do tipo **Web application**, criado no Google Cloud Console. **Não é segredo** —
ele é público por design e também vai para o bundle do frontend
(`NEXT_PUBLIC_GOOGLE_CLIENT_ID`, ver seção de produção). O que protege a aplicação é a
assinatura do token, não o sigilo do client ID.

Backend e frontend **precisam usar o mesmo valor**: o `GoogleTokenProvider` confere o claim
`aud` do ID token contra esta variável, e se o frontend usar um client ID diferente, todo login
falha com `401 Invalid Google token` — sintoma indistinguível de um ataque real, então esse é o
primeiro lugar a checar quando o login não funciona.

### `GOOGLE_ALLOWED_DOMAINS`

Lista de domínios separados por vírgula. Cada entrada casa com o domínio **e seus subdomínios**:
`ifpe.edu.br` cobre `@ifpe.edu.br`, `@discente.ifpe.edu.br` e `@docente.ifpe.edu.br`, sem precisar
listar os três. A regra em código (`UserService.isDomainAllowed`) é:

```java
domain.equals(allowed) || domain.endsWith("." + allowed)
```

O `"."` antes do domínio permitido é o que impede `@fakeifpe.edu.br` de casar com `ifpe.edu.br`.

A verificação roda **apenas na criação da conta**. Quem já tem conta continua entrando mesmo que
a lista mude depois — não existe um mecanismo de "desativar usuário" no sistema, então revalidar
o domínio em todo login só trocaria um risco (conta indevida criada) por outro (erro de digitação
na config trancando todo mundo de uma vez).

---

## Modelo de dados

A tabela `im_user` tem seis colunas:

```
id          bigint        PK
name        varchar(100)  NOT NULL
email       varchar(255)  NOT NULL, UNIQUE
google_id   varchar(255)  NOT NULL, UNIQUE
created_at  timestamp     NOT NULL
updated_at  timestamp     NOT NULL
```

Não existem colunas de senha, verificação ou telefone, nem tabela de tokens de e-mail — esse
modelo foi removido junto com o login por senha (não há dado legado; o sistema nunca chegou a ser
lançado com ele).

`google_id` (o claim `sub` do ID token) é `NOT NULL` porque `findOrCreateGoogleUser` é o único
caminho de inserção em `im_user`. Vale registrar o que essa constraint garante e o que não
garante:

- **Garante:** se algum dia surgir um segundo caminho de criação de usuário (script de seed,
  endpoint de admin, outro provedor de login) e ele esquecer de preencher `google_id`, o INSERT
  falha na hora, na linha de código culpada. É proteção contra bug, não contra ataque.
- **Não garante:** que o valor seja um `sub` real do Google. Quem tem acesso de escrita ao banco
  sempre pode inserir qualquer string ali. **A coluna nunca é consultada para autenticar** — quem
  autentica é a assinatura do ID token, verificada a cada login pelo `GoogleTokenProvider`.

---

## Contrato HTTP

### `POST /auth/google`

Sem autenticação prévia.

```json
// Request
{ "credential": "<ID token do Google>" }
```

```json
// 200
{
  "token": "<JWT da aplicação, HS256, 24h>",
  "user": { "id": 2, "name": "Fulano de Tal", "email": "fulano@ifpe.edu.br" }
}
```

| Status | Corpo | Causa |
|---|---|---|
| 400 | `Missing Google credential` | Corpo ausente ou `credential` vazio/em branco. |
| 401 | `Invalid Google token` | Assinatura inválida, `iss` não é do Google, `aud` diferente do client ID configurado, token expirado, `email_verified: false`, ou domínio de e-mail não permitido (só na criação). |
| 500 | Corpo padrão do Spring | `GOOGLE_OAUTH_CLIENT_ID` não configurado. |

A mensagem de 401 é **deliberadamente a mesma** para token inválido e para domínio não
permitido — diferenciar essas respostas revelaria a um atacante qual verificação falhou, e no
caso de domínio revelaria se um e-mail específico existe ou não no sistema.

### `GET /auth/me`

Requer `Authorization: Bearer <JWT da aplicação>`.

```json
// 200
{ "id": 2, "name": "Fulano de Tal", "email": "fulano@ifpe.edu.br" }
```

Retorna 400 se o header estiver ausente, malformado, ou se o token não validar.

---

## Decisões de segurança e por quê

Registro aqui as decisões cujo motivo não é óbvio olhando só o código — vale ler antes de "arrumar"
algo que parece estranho à primeira vista.

**Por que `aud` é a checagem mais importante do `GoogleTokenProvider`.** O JWKS do Google é
global: as mesmas chaves assinam ID tokens para *qualquer* client ID registrado no mundo, e
registrar um client ID é gratuito. Um atacante pode criar seu próprio site com "Login com
Google", capturar um ID token genuíno emitido *para o site dele*, e tentar reenviá-lo para o
`/auth/google` desta aplicação. Toda checagem (assinatura, `iss`, `exp`, `email_verified`) passa,
menos uma: o `aud` do token é o client ID do atacante, não o desta aplicação. É essa comparação
que fecha o ataque — e é por isso que o backend e o frontend precisam usar exatamente o mesmo
`GOOGLE_OAUTH_CLIENT_ID`.

**Por que o `aud` é comparado como `List<String>` e não como `String`.** O Spring normaliza o
claim `aud` para uma coleção mesmo quando o Google manda uma string simples. Comparar como
`String` faria o validador nunca casar — e um validador que nunca casa vira, na prática, um
validador que sempre rejeita (ou, dependendo de como for escrito, um que nunca é de fato
exercitado). Ver o comentário em `GoogleTokenProvider.java`.

**Por que `SecurityFilter.shouldNotFilter` não pode virar `/auth/**`.** `/auth/me` exige
autenticação e depende deste mesmo filtro para popular o `SecurityContext` a partir do JWT da
aplicação. Só `/auth/google` pode ser público — generalizar o padrão "quebraria" o `/auth/me`
silenciosamente (o filtro simplesmente não rodaria nele).

**Por que não há `HttpMethod.POST` no matcher de `/auth/google`.** O navegador manda um
preflight `OPTIONS` antes do `POST` real (CORS). Restringir o matcher ao método `POST` faz o
`OPTIONS` cair em `anyRequest().authenticated()` e ser rejeitado — o login funciona via `curl`
(que não manda preflight) mas quebra no navegador. Esse é um erro fácil de reintroduzir "por
limpeza" sem perceber o efeito.

**Por que existe `SessionCreationPolicy.STATELESS`.** Autenticação é inteiramente via Bearer
token; não há cookie de sessão do lado do servidor. Isso, somado ao CSRF desabilitado, só é seguro
porque não há nenhuma credencial ambiente (cookie de sessão) que o navegador anexe
automaticamente a uma requisição cross-site.

---

## Checklist para produção

O que está pronto hoje cobre desenvolvimento e um ambiente de teste. Para produção, falta:

### 1. Credenciais reais no Google Cloud Console

- Criar um **OAuth 2.0 Client ID** do tipo **Web application** (não confundir com o tipo usado
  em apps mobile/desktop).
- Em **Authorized JavaScript origins**, listar a URL pública do frontend em produção
  (ex.: `https://inventarium.ifpe.edu.br`). **Não configurar redirect URI** — o fluxo usado é o
  de ID token (`GoogleLogin` do frontend), não o de authorization code.
- Se o domínio de produção mudar, ou se o app trocar de projeto no Google Cloud, o Client ID
  muda, e as três variáveis abaixo precisam ser atualizadas juntas.

### 2. Variáveis de ambiente

```bash
SECURITY_TOKEN_SECRET=<gerado com openssl rand -hex 32, único por ambiente>
GOOGLE_OAUTH_CLIENT_ID=<client id do Google Cloud Console>
GOOGLE_ALLOWED_DOMAINS=ifpe.edu.br
```

`SECURITY_TOKEN_SECRET` **não pode ser reaproveitado** entre ambientes (dev/staging/produção) —
um segredo vazado em um ambiente de teste não pode comprometer produção.

### 3. O client ID também precisa chegar ao frontend, e isso é uma armadilha

O Next.js **inlina** variáveis `NEXT_PUBLIC_*` no bundle JavaScript **em tempo de build**, não
de execução. Isso já está resolvido no `docker-compose.yml` (o valor de `GOOGLE_OAUTH_CLIENT_ID`
é passado como *build arg* para a imagem do frontend), mas é importante entender a consequência
operacional: **definir ou trocar a variável no ambiente de execução do container não tem efeito
nenhum**. Qualquer mudança no client ID exige reconstruir a imagem do frontend
(`docker compose build inventarium-front`), não apenas reiniciar o container.

O sintoma de esquecer isso é sutil: a aplicação sobe normalmente, a tela de login renderiza, e
só o botão do Google falha silenciosamente ou usa um client ID desatualizado.

### 4. HTTPS obrigatório

O Google Identity Services **exige origem servida por HTTPS** em produção (localhost é a única
exceção liberada para desenvolvimento). Sem TLS configurado na frente do frontend, o botão de
login do Google simplesmente não funciona.

### 5. CORS

Hoje `AuthController` usa `@CrossOrigin(origins = "*")` — aceitável em desenvolvimento, mas em
produção vale restringir à origem real do frontend, em vez de aceitar qualquer origem. Isso não
é uma falha de autenticação (o JWT continua sendo a única credencial validada, e não há cookie
de sessão ambiente para um CSRF explorar), mas é uma superfície desnecessariamente ampla para
manter aberta.

### 6. Sem revogação de sessão

Não há como invalidar um JWT da aplicação antes da expiração natural de 24h. Se a instituição
desativar uma conta Google (ex.: aluno formado, servidor desligado), a pessoa continua com acesso
ao Inventarium por até 24h depois — o Google para de emitir novos ID tokens para ela, mas o JWT
da aplicação já emitido continua válido até expirar. Aceitável para a maioria dos casos de uso
institucionais, mas vale que quem for operar o sistema em produção esteja ciente do limite. Uma
correção completa exigiria uma coluna de versão de token checada no filtro a cada requisição —
fora do escopo desta implementação.

### 7. Backup do `SECURITY_TOKEN_SECRET`

Se o segredo for perdido ou precisar ser rotacionado, **toda sessão ativa é invalidada
instantaneamente** — não há chave secundária de transição. Trocar o segredo em produção
desloga todo mundo ao mesmo tempo; planeje a rotação para um horário de baixo uso.

