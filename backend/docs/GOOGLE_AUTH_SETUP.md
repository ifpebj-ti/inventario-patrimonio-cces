# Configurando as variáveis do login com Google

Guia prático para configurar do zero as três variáveis que o login com Google precisa:
`SECURITY_TOKEN_SECRET`, `GOOGLE_OAUTH_CLIENT_ID` e `GOOGLE_ALLOWED_DOMAINS`. Para entender
**como** o fluxo de login funciona e **por quê** cada decisão foi tomada, veja
[`AUTHENTICATION.md`](./AUTHENTICATION.md) — este documento é só o "como configurar".

## Índice

- [Resumo rápido](#resumo-rápido)
- [1. Gerar o `SECURITY_TOKEN_SECRET`](#1-gerar-o-security_token_secret)
- [2. Criar o `GOOGLE_OAUTH_CLIENT_ID` no Google Cloud Console](#2-criar-o-google_oauth_client_id-no-google-cloud-console)
- [3. Definir o `GOOGLE_ALLOWED_DOMAINS`](#3-definir-o-google_allowed_domains)
- [Onde cada valor precisa estar](#onde-cada-valor-precisa-estar)
- [Verificando que funcionou](#verificando-que-funcionou)
- [Erros comuns](#erros-comuns)

---

## Resumo rápido

| Variável | O que é | Onde conseguir |
|---|---|---|
| `SECURITY_TOKEN_SECRET` | Chave que assina o JWT da aplicação | Você mesmo gera, com `openssl` |
| `GOOGLE_OAUTH_CLIENT_ID` | Identifica o app perante o Google | Google Cloud Console (gratuito) |
| `GOOGLE_ALLOWED_DOMAINS` | Domínios de e-mail que podem criar conta | Você mesmo decide (ex.: `ifpe.edu.br`) |

Todas as três vão no arquivo **`.env` na raiz do repositório** (mesmo nível do
`docker-compose.yml`). Ele é ignorado pelo git — cada pessoa/ambiente tem o seu. Comece
copiando o modelo:

```bash
cp .env.example .env
```

---

## 1. Gerar o `SECURITY_TOKEN_SECRET`

É a chave HMAC que assina o JWT da própria aplicação (não tem relação com o Google). Gere um
valor aleatório com:

```bash
openssl rand -hex 32
```

Isso imprime uma string hexadecimal de 64 caracteres, por exemplo:

```
cf222d795015b026266ee0cd02ef22cc28ab9f3a2b7fc7525fd704b4a922465d
```

Cole no `.env`:

```
SECURITY_TOKEN_SECRET=cf222d795015b026266ee0cd02ef22cc28ab9f3a2b7fc7525fd704b4a922465d
```

Pontos de atenção:

- **Não existe valor padrão.** Se a variável não estiver definida, o backend recusa subir —
  de propósito, para nunca rodar com um segredo previsível.
- **Um valor por ambiente.** Não reaproveite o mesmo segredo entre sua máquina local, staging e
  produção — um vazamento em um ambiente de teste não pode comprometer produção.
- **Trocar o valor desloga todo mundo na hora.** Toda sessão ativa (todo JWT já emitido) para de
  validar assim que o backend sobe com um segredo novo. Normal na primeira configuração; evite
  fazer isso em produção fora de um horário de baixo uso.

---

## 2. Criar o `GOOGLE_OAUTH_CLIENT_ID` no Google Cloud Console

Não é segredo — é um identificador público que vai inclusive no bundle JavaScript do frontend.
**Qualquer conta Google serve para criá-lo** (Gmail pessoal, conta institucional, tanto faz):
quem cria o Client ID não tem nenhuma relação com quem depois consegue logar no app — isso é
controlado à parte, pelo `GOOGLE_ALLOWED_DOMAINS` (seção 3).

### 2.1 Criar/selecionar um projeto

Acesse **console.cloud.google.com**. No seletor de projeto, no topo da página, clique em
**Novo projeto** (ou selecione um já existente). Pode dar qualquer nome, ex. "Inventarium".

### 2.2 Configurar a tela de consentimento OAuth

No menu lateral, **APIs e serviços → Tela de permissão OAuth** (em consoles mais novos isso
aparece como **Google Auth Platform**; se for o seu caso, clique em **Vamos começar** e siga o
assistente — o conteúdo pedido é o mesmo, só a navegação muda de tela).

Você vai preencher, em ordem:

1. **Informações do app** — nome do app (ex. "Inventarium") e e-mail de suporte ao usuário.
2. **Público-alvo** — escolha **Externo**. Isso permite login com qualquer conta Google,
   incluindo Gmail pessoal e contas `@ifpe.edu.br`.
3. **Informações de contato** — um e-mail de contato do desenvolvedor (pode ser o seu).
4. **Concluir** — aceitar os termos de uso da política de dados do Google.

Depois de criado, ainda na seção **Público-alvo**, role até **Usuários de teste** e adicione
o(s) e-mail(s) que vão testar o login. **Isso é obrigatório**: enquanto o app estiver em modo
**Testing** (o padrão inicial, antes de publicar), só as contas cadastradas ali conseguem
logar — qualquer outra conta é recusada pelo próprio Google, antes mesmo de chegar no backend.

> Este app usa só os escopos `openid`, `email` e `profile` (identidade básica) — o Google
> classifica isso como não sensível, então **não é necessário passar pelo processo de
> verificação** do app para usar em produção com poucos usuários. Verificação só passa a ser
> exigida em cenários de escala/escopo maiores que o deste projeto.

### 2.3 Criar o Client ID

No menu lateral, vá em **Credenciais** (ou, no console novo, na seção **Clientes** dentro do
Google Auth Platform) → **+ Criar credenciais** → **ID do cliente OAuth**.

Preencha:

- **Tipo de aplicativo**: **Aplicativo da Web** (não escolha Android/iOS/Desktop).
- **Nome**: qualquer coisa, ex. "Inventarium Web".
- **Origens JavaScript autorizadas**: clique em **+ Adicionar URI** e adicione, uma por linha:
  ```
  http://localhost:3000
  ```
  E, quando existir, a URL pública de produção (ex. `https://inventarium.ifpe.edu.br`).
- **URIs de redirecionamento autorizados**: **deixe vazio**. O fluxo usado por este app é o de
  ID token (o botão do Google entrega o token direto ao frontend), não o de authorization code
  — não há redirect algum para configurar.

Clique em **Criar**. Um modal mostra o **Client ID** (termina em
`.apps.googleusercontent.com`) e um **Client secret**. **Copie só o Client ID** — o secret não é
usado neste projeto, porque o fluxo de ID token não faz troca de código no servidor.

O aviso "pode levar de cinco minutos a algumas horas para propagar" é normal; na prática costuma
ficar pronto em menos de um minuto.

### 2.4 Colar no `.env`

```
GOOGLE_OAUTH_CLIENT_ID=<o-que-você-copiou>.apps.googleusercontent.com
```

---

## 3. Definir o `GOOGLE_ALLOWED_DOMAINS`

Lista de domínios de e-mail, separados por vírgula, que podem **criar conta nova**. Não tem
relação com o Google Cloud Console — é uma regra só desta aplicação.

```
GOOGLE_ALLOWED_DOMAINS=ifpe.edu.br
```

Cada entrada casa com o domínio **e seus subdomínios**: `ifpe.edu.br` já libera
`@ifpe.edu.br`, `@discente.ifpe.edu.br` e `@docente.ifpe.edu.br` de uma vez, sem precisar
listar os três.

- **Vazio libera qualquer domínio** — útil só em ambiente de desenvolvimento isolado; não use
  vazio em produção.
- A verificação roda **apenas na criação da conta**. Quem já tem conta continua entrando mesmo
  que a lista mude depois.
- Isso é **independente** da lista de "usuários de teste" do passo 2.2. Enquanto o app Google
  estiver em modo Testing, as DUAS restrições valem ao mesmo tempo: o e-mail precisa estar
  cadastrado como usuário de teste **e** o domínio precisa estar na allowlist. Depois que o app
  for publicado no Google (saindo do modo Testing), só a restrição de domínio continua valendo.

---

## Onde cada valor precisa estar

### Rodando com Docker (`docker compose up`)

Só o `.env` da raiz do repositório. O `docker-compose.yml` já repassa as três variáveis para o
backend, e repassa `GOOGLE_OAUTH_CLIENT_ID` também para o **build** do frontend (ver próxima
seção — isso tem uma pegadinha).

### Rodando o frontend fora do Docker (`npm run dev`)

O backend em Docker lê o `.env` da raiz normalmente, mas o frontend rodando local com
`npm run dev`/`npm run build` **não lê o `.env` da raiz** — ele só lê arquivos dentro da própria
pasta `frontend/`. Crie `frontend/.env.local` (também ignorado pelo git):

```
NEXT_PUBLIC_GOOGLE_CLIENT_ID=<o-mesmo-client-id-do-.env-da-raiz>
```

### A pegadinha do build do frontend

O Next.js **inlina** variáveis `NEXT_PUBLIC_*` no bundle JavaScript **durante o build**, não em
tempo de execução. Isso significa:

- Só mudar o `.env` e reiniciar o container (`docker compose restart` /
  `docker compose up -d`, sem `--build`) **não tem efeito nenhum** — o bundle já foi congelado
  com o valor antigo (ou vazio).
- Toda vez que o `GOOGLE_OAUTH_CLIENT_ID` mudar, é preciso **reconstruir a imagem do frontend**:
  ```bash
  docker compose build inventarium-front
  docker compose up -d
  ```
- O mesmo vale para o script `config/builder-docker-frontend.sh`, que lê o Client ID direto do
  `.env` da raiz e passa como build arg — também precisa ser executado de novo após qualquer
  troca do valor.

Se o Client ID estiver ausente na hora do build, o próprio build falha com uma mensagem
explícita (configurado em `frontend/next.config.ts`), em vez de gerar silenciosamente uma
imagem com o botão de login quebrado.

---

## Verificando que funcionou

```bash
# 1. As três variáveis estão preenchidas?
grep -E "SECURITY_TOKEN_SECRET|GOOGLE_OAUTH_CLIENT_ID|GOOGLE_ALLOWED_DOMAINS" .env

# 2. Subir tudo
docker compose up -d --build

# 3. O client id foi realmente para dentro do bundle do frontend?
docker exec inventarium-front grep -rl "$(grep GOOGLE_OAUTH_CLIENT_ID .env | cut -d= -f2)" \
  /opt/docker/app/.next/static

# 4. O backend está de pé e aceitando o endpoint de login?
curl -s -o /dev/null -w "%{http_code}\n" -X POST localhost:8080/auth/google \
  -H 'Content-Type: application/json' -d '{}'
# esperado: 400 (credential ausente) — prova que o endpoint responde e não é 500
# de client id ausente
```

Depois disso, abra `http://localhost:3000`, clique no botão do Google e entre com um e-mail
que esteja **ao mesmo tempo**: (a) cadastrado como usuário de teste no Google Cloud Console
(passo 2.2) e (b) dentro de um domínio liberado em `GOOGLE_ALLOWED_DOMAINS` (passo 3) — ou
qualquer conta, se a lista estiver vazia.

---

## Erros comuns

| Sintoma | Causa provável |
|---|---|
| `POST /auth/google` responde **500** | `GOOGLE_OAUTH_CLIENT_ID` está vazio no ambiente do backend. |
| `POST /auth/google` responde **401** com qualquer conta, mesmo uma válida | O Client ID usado pelo **frontend** (`NEXT_PUBLIC_GOOGLE_CLIENT_ID`) é diferente do usado pelo **backend** (`GOOGLE_OAUTH_CLIENT_ID`). Os dois precisam ser idênticos — o backend confere o claim `aud` do token contra o próprio valor. |
| Botão do Google não aparece, ou aparece mas dá erro ao clicar | Origem (`http://localhost:...` ou o domínio de produção) não está em **Origens JavaScript autorizadas** no Client ID (passo 2.3). |
| "Acesso bloqueado: [app] não concluiu o processo de verificação do Google" ao tentar logar | Seu e-mail não está na lista de **usuários de teste** (passo 2.2), e o app ainda está em modo Testing. |
| Mudou o Client ID, mas o app continua usando o antigo | Esqueceu de reconstruir a imagem do frontend — ver ["A pegadinha do build"](#a-pegadinha-do-build-do-frontend) acima. |
| App recusa iniciar, log menciona `security.token.secret` | `SECURITY_TOKEN_SECRET` não está definido no ambiente. |
| Todo mundo foi deslogado de uma vez | `SECURITY_TOKEN_SECRET` mudou — comportamento esperado, não é bug. |
