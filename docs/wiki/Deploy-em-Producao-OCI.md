# Deploy em Produção na OCI

Este runbook descreve como o Inventarium é publicado na Oracle Cloud Infrastructure (OCI): arquitetura, o passo a passo do primeiro go-live, a operação do dia a dia (deploy, rollback, certificados, Nginx, backup e restauração) e a solução dos problemas mais comuns. A decisão que originou este desenho está no ADR 0006 (veja [Architecture Decision Records](./Architecture-Decision-Records)); as ameaças e os controles estão em [Modelagem de Ameaças](./Modelagem-de-Ameaças).

> Nenhum valor secreto aparece neste documento. Senhas, tokens e a URL do webhook são gerados por você e guardados em um gerenciador de senhas.

## 1. Visão geral

```text
                         Internet
                            │  80 / 443
                ┌───────────▼────────────┐
                │  IP público RESERVADO  │   DNS (Namecheap): inventariumifpe.me e subdomínios
                └───────────┬────────────┘
   ┌────────────────────────▼─────────────────────────────┐   subnet pública 10.42.0.0/26
   │ VM da aplicação (Oracle Linux 8)        10.42.0.10   │
   │  Nginx (TLS Let's Encrypt, headers, rate limit)      │
   │   ├─ inventariumifpe.me      → front   127.0.0.1:3000│
   │   ├─ api.inventariumifpe.me  → back    127.0.0.1:8080│
   │   ├─ portainer.…             → Portainer 127.0.0.1:9443 (só IPs de admin)
   │   ├─ sonar.…  ───────────────┐                       │
   │   └─ grafana.… (só admin) ───┤                       │
   │  Docker: postgres + back + front (stack do Portainer)│
   └──────────────────────────────┼───────────────────────┘
                                  │ rede privada da VCN
   ┌──────────────────────────────▼───────────────────────┐   subnet privada 10.42.0.64/26
   │ VM de observabilidade (sem IP público)  10.42.0.74   │
   │  SonarQube :9000 · Grafana :3000 (stack da issue de observabilidade)
   └──────────────────────────────────────────────────────┘
```

| Host | Destino | Quem acessa |
| --- | --- | --- |
| `inventariumifpe.me` | Frontend (Next.js) | Público |
| `www.inventariumifpe.me` | Redireciona (301) para o domínio sem `www` | Público |
| `api.inventariumifpe.me` | Backend (Spring Boot); só `/actuator/health` do Actuator | Público (CORS só para `https://inventariumifpe.me`) |
| `portainer.inventariumifpe.me` | Portainer CE | Só IPs de `allowed_admin_cidrs`; apenas o webhook de stack é público |
| `sonar.inventariumifpe.me` | SonarQube (VM privada) | Público, com login obrigatório (o scanner do GitHub precisa chegar) |
| `grafana.inventariumifpe.me` | Grafana (VM privada) | Só IPs de `allowed_admin_cidrs` |

## 2. O que está no repositório e como chega à VM

| Caminho | O que é |
| --- | --- |
| `infra/terraform/oci/` | Rede, NSGs, VMs, IP público reservado, bucket de backup e IAM |
| `infra/nginx/app-vm/` | **Configuração do Nginx versionada** (`nginx.conf`, `conf.d/`, `sites-tls/`, `snippets/`) |
| `infra/scripts/app-vm/` | Scripts que rodam na VM (bootstrap, certificados, Portainer, backup/restore) e unidades `systemd` |
| `infra/scripts/deploy-nginx.sh` | Roda **na sua máquina** e atualiza Nginx e scripts na VM sem recriá-la |
| `docker-compose.production.yml` | Stack da aplicação (postgres, back, front) lida pelo Portainer a partir da `main` |

**O Nginx está no repositório e viaja para a VM em dois momentos:**

1. **Primeiro boot:** o Terraform embute os arquivos de `infra/nginx/app-vm/` e `infra/scripts/app-vm/` no `user_data` (cloud-init, comprimido). O cloud-init grava tudo em `/opt/inventarium-app/src/` e executa `bootstrap-vm.sh`, que instala Nginx, Docker e Portainer e instala a configuração. Sem certificado ainda, só a porta 80 fica ativa.
2. **Depois:** o Terraform **não** recria a VM quando esses arquivos mudam (`ignore_changes` em `user_data`, porque a VM guarda o banco). Para atualizar o Nginx: merge do PR e `infra/scripts/deploy-nginx.sh <ip>`. O script valida com `nginx -t` e restaura o backup se a configuração for inválida.

Dois arquivos são **gerados na VM** e nunca vão para o Git: `/etc/nginx/inventarium/generated/admin-allowlist.conf` (de `allowed_admin_cidrs`) e `upstreams.conf` (IP privado da observabilidade). Veja os exemplos em `infra/nginx/app-vm/generated.example/`.

## 3. Go-live: primeiro deploy

Passos marcados com ⭐ dependem de você (acesso à Namecheap, ao Google Cloud, ao GitHub e à OCI). Os demais são automáticos.

### ⭐ 3.1 Namecheap: privacidade e limpeza do DNS (10 minutos)

1. Acesse namecheap.com, entre na sua conta e abra **Domain List**.
2. Ao lado de `inventariumifpe.me`, clique em **Manage**.
3. Na aba **Domain**, ative **Withheld for Privacy** (antigo WhoisGuard). É gratuito e impede que seu nome, e-mail e endereço apareçam publicamente no WHOIS.
4. Na mesma aba, em **Nameservers**, confirme **Namecheap BasicDNS**.
5. Abra a aba **Advanced DNS**. Em **Host Records**, **apague** os registros padrão: `CNAME Record | www | parkingpage.namecheap.com` e `URL Redirect Record | @`. Eles conflitam com os registros que você vai criar. Ainda não crie os novos: falta o IP.

> O domínio gratuito vale por 1 ano. Anote o vencimento (em torno de outubro de 2027) e decida a renovação antes.

### ⭐ 3.2 Descobrir seu IP de admin (2 minutos)

No PowerShell:

```powershell
curl.exe -s https://ifconfig.me
```

O resultado (por exemplo `198.51.100.7`) vira `198.51.100.7/32` em `allowed_ssh_cidrs` e `allowed_admin_cidrs`. Internet residencial costuma ter IP dinâmico; se o seu mudar, você perde o SSH, o Portainer e o Grafana até atualizar a lista (veja 4.6). Dá para listar mais de um CIDR, por exemplo casa e IFPE.

### ⭐ 3.3 Terraform: aplicar (15 minutos)

1. Em `infra/terraform/oci/terraform.tfvars` (arquivo local, ignorado pelo Git), ajuste:

   ```hcl
   allowed_ssh_cidrs   = ["SEU_IP/32"]
   allowed_web_cidrs   = ["0.0.0.0/0"]        # obrigatório: site público e validação do Let's Encrypt na porta 80
   allowed_admin_cidrs = ["SEU_IP/32"]        # nunca 0.0.0.0/0 (o Terraform recusa)
   acme_email          = "seu-email@exemplo.com"
   ```

2. Rode:

   ```powershell
   cd infra/terraform/oci
   terraform init -backend-config=backend.oci.hcl
   terraform plan -out tfplan
   ```

3. **Leia o plano.** Se as VMs já existiam, elas aparecerão como *replace* (a rede e o cloud-init mudaram): isso é esperado **desta primeira vez**. Pare e peça ajuda se qualquer **bucket** (`inventarium-terraform-state`) aparecer como *destroy*.
4. Se o plano estiver correto: `terraform apply tfplan` e depois `terraform output app_public_ip`. Esse é o **IP reservado**; anote.

> Se o seu usuário não puder criar *dynamic groups* e *policies*, defina `manage_backup_iam = false` e peça a um administrador da tenancy que crie o grupo dinâmico da VM e a policy `manage objects` no bucket de backup.

### ⭐ 3.4 Namecheap: criar os registros (5 minutos + propagação)

Em **Advanced DNS → Add New Record**, crie seis registros **A Record** com **Value = o IP reservado** e **TTL = Automatic**, nos hosts: `@`, `www`, `api`, `portainer`, `sonar` e `grafana`. Crie também um **CAA Record**: Host `@`, Flag `0`, Tag `issue`, Value `letsencrypt.org` (só a Let's Encrypt poderá emitir certificados para o domínio).

Confira a propagação (de 5 a 30 minutos). Cada host deve devolver o IP reservado:

```powershell
foreach ($h in 'inventariumifpe.me','www','api','portainer','sonar','grafana') {
  $n = if ($h -eq 'inventariumifpe.me') { $h } else { "$h.inventariumifpe.me" }
  "{0,-34} {1}" -f $n, (Resolve-DnsName $n -Server 1.1.1.1 -Type A).IPAddress
}
```

### ⭐ 3.5 Google Cloud Console: origem autorizada (5 minutos)

1. Acesse console.cloud.google.com, selecione o projeto do Inventarium e vá em **APIs e serviços → Credenciais**.
2. Abra o **ID do cliente OAuth 2.0** usado pelo app (o mesmo de `GOOGLE_OAUTH_CLIENT_ID`).
3. Em **Origens JavaScript autorizadas**, clique em **Adicionar URI** e informe `https://inventariumifpe.me`. Mantenha `http://localhost:3000` para desenvolvimento. Não há *URI de redirecionamento*: o fluxo usa ID token.
4. Em **Tela de consentimento OAuth**, confira se o app está publicado ou se os usuários de teste incluem quem vai entrar. Em modo *Testing* só os usuários de teste conseguem logar.

### ⭐ 3.6 GitHub: variável de build (10 minutos)

O endereço da API é **embutido no build do frontend**. Hoje a variável do repositório aponta para um IP antigo por HTTP; sem trocá-la, a próxima release geraria um front que chama o endereço errado.

1. No repositório, abra **Settings → Secrets and variables → Actions → Variables**.
2. Edite `NEXT_PUBLIC_API_URL` para `https://api.inventariumifpe.me` (sem barra no final). Confirme que `NEXT_PUBLIC_GOOGLE_CLIENT_ID` está preenchida.
3. O secret do webhook entra no passo 3.10 (secret de repositório; o projeto tem um único ambiente e não usa *Environments* do GitHub).
4. **Gere uma release depois de trocar a variável** (a release publica as imagens com a URL nova). Qualquer PR com tipo de release `patch` ou `minor` servirá. Anote a tag publicada (por exemplo `v5.0.2`): ela vai em `IMAGE_TAG`.

> O GHCR é público e o repositório também: o Portainer baixa as imagens e lê o compose sem credenciais.

### 3.7 Primeiro boot da VM

O cloud-init roda sozinho depois do `terraform apply`. Aguarde de 5 a 10 minutos e confirme (⭐ via SSH):

```powershell
ssh opc@<IP_RESERVADO>
sudo cloud-init status --wait          # deve terminar em "status: done"
sudo tail -n 20 /var/log/inventarium-bootstrap.log   # termina com "bootstrap concluido"
sudo docker ps                         # container "portainer" rodando
curl -sI -H 'Host: inventariumifpe.me' http://localhost | head -1   # 301 (redireciona para https)
```

Se o log mostrar "sem internet", o IP reservado ainda não foi associado; o bootstrap espera até ~15 minutos. Para reexecutar: `sudo bash /opt/inventarium-app/src/scripts/bootstrap-vm.sh`.

### ⭐ 3.8 Emitir o certificado TLS

Só depois de o DNS (3.4) estar propagado. O script confere o DNS antes de pedir o certificado, para não gastar o limite do Let's Encrypt.

```bash
sudo /opt/inventarium-app/bin/issue-certificates.sh --staging   # ensaio (certificado de teste, não confiável)
sudo /opt/inventarium-app/bin/issue-certificates.sh             # definitivo
```

O segundo comando troca o certificado de teste pelo real e liga os *vhosts* HTTPS do Nginx. Teste: `curl -sI https://inventariumifpe.me | head -3` (a resposta será 502 até a stack subir; é esperado). A renovação é automática: `systemctl list-timers inventarium-certbot-renew.timer`.

### ⭐ 3.9 Portainer e a stack da aplicação

1. Descubra a senha inicial do Portainer, na VM: `sudo cat /opt/inventarium-app/secrets/portainer-admin-password`. Ela é gerada no primeiro boot e só existe nesse arquivo.
2. **Não troque a senha ainda:** o script do passo 4 se autentica com a senha deste arquivo. Se você já trocou (ou for trocar antes de rodá-lo), atualize o arquivo com a senha nova.
3. **No seu PC (Git Bash)**, crie um arquivo `.env.production` **fora do repositório** (nunca faça commit dele):

   ```bash
   POSTGRES_USER=inventarium
   POSTGRES_PASSWORD=<openssl rand -base64 32>
   POSTGRES_DB=inventory_management
   SECURITY_TOKEN_SECRET=<openssl rand -hex 32>       # valor NOVO, nunca o de desenvolvimento
   GOOGLE_OAUTH_CLIENT_ID=<o mesmo client id do front>
   GOOGLE_ALLOWED_DOMAINS=ifpe.edu.br
   SPRING_MAIL_USERNAME=<conta Gmail do envio>
   SPRING_MAIL_PASSWORD=<senha de app do Gmail>
   SYSTEM_ADMIN_USERNAME=admin
   SYSTEM_ADMIN_PASSWORD=<openssl rand -base64 24>    # mínimo 12 caracteres
   CORS_ALLOWED_ORIGINS=https://inventariumifpe.me
   FORWARD_HEADERS_STRATEGY=native
   IMAGE_TAG=<tag da release do passo 3.6, ex.: v5.0.2>
   ```

   A senha de app do Gmail se cria em **Conta Google → Segurança → Verificação em duas etapas (ativada) → Senhas de app**.
4. Teste sem efeito colateral e depois crie a stack. O script apaga o `.env` da VM ao terminar:

   ```powershell
   scp .env.production opc@<IP_RESERVADO>:/tmp/inventarium.env
   ssh opc@<IP_RESERVADO> "sudo /opt/inventarium-app/bin/bootstrap-portainer-stack.sh /tmp/inventarium.env --dry-run --keep-env-file"
   ssh opc@<IP_RESERVADO> "sudo /opt/inventarium-app/bin/bootstrap-portainer-stack.sh /tmp/inventarium.env"
   ```

   O script imprime a **URL do webhook** e a guarda em `/opt/inventarium-app/secrets/portainer-stack-webhook-url`. Ela é um segredo.
5. Apague o `.env.production` do seu PC (ou guarde-o apenas no gerenciador de senhas).
6. Agora abra `https://portainer.inventariumifpe.me` **do seu IP de admin**, entre com usuário `admin` e a senha do arquivo, e **troque a senha** (menu *My account*). Guarde-a no gerenciador de senhas e, se for rodar o script de novo, atualize o arquivo de senha da VM.
7. Acompanhe a subida no Portainer (*Stacks → inventarium*) ou com `sudo docker ps`. O back leva cerca de 1 minuto para ficar saudável.

> Se a API do Portainer recusar a criação da stack (o formato do payload pode mudar entre versões), o script informa o erro. A alternativa é criar a stack pela UI: *Stacks → Add stack → Repository*, URL do repositório, referência `refs/heads/main`, arquivo `docker-compose.production.yml`, ative *GitOps updates → Webhook* e *Re-pull image*, e preencha as variáveis. Copie a URL do webhook gerada.

### ⭐ 3.10 Webhook de deploy no GitHub

Em **Settings → Secrets and variables → Actions → Secrets → New repository secret**, crie `PORTAINER_STACK_WEBHOOK_URL` com a URL do passo anterior. O job `deploy-production` do workflow `release.yml` usa esse secret para atualizar a stack a cada release: ele chama o webhook (`POST`, sem parâmetros) e depois faz um *smoke test*, repetindo `GET` em `https://api.inventariumifpe.me/actuator/health` e `https://inventariumifpe.me/` por até 5 minutos. Sem o secret, o job termina sem falhar e o deploy continua manual. A URL é mascarada nos logs.

> **Por que `IMAGE_TAG=latest`:** testamos localmente (Portainer 2.39.5) e o webhook **ignora** parâmetros na URL, como `?IMAGE_TAG=vX.Y.Z`: ele só redeploya com as variáveis já salvas na stack. Por isso a stack roda com `IMAGE_TAG=latest`. Cada release publica a mesma imagem com duas tags, `vX.Y.Z` e `latest` (esta só depois dos scans do Trivy), e o webhook, com *Re-pull image* ligado, baixa o `latest` novo.
>
> **Qual versão está no ar?** O `latest` não diz a versão, mas a imagem leva o label `org.opencontainers.image.version`: `sudo docker inspect inventarium-back --format '{{index .Config.Labels "org.opencontainers.image.version"}}'`.

### ⭐ 3.11 Validar

- `curl.exe -s https://api.inventariumifpe.me/actuator/health` → `{"status":"UP"}`.
- `curl.exe -sI https://api.inventariumifpe.me/actuator/env` → `404`.
- Abra `https://inventariumifpe.me`, entre com uma conta `@ifpe.edu.br` e teste o leitor de QR (permissão de câmera), uma exportação e um upload de planilha.
- Login do administrador por senha (`POST /auth/login`): errar a senha mais de 5 vezes deve responder **429**.
- No DevTools (F12) → *Console*: confira se há avisos de CSP (modo *Report-Only*, veja 4.8) e anote-os.
- Teste de TLS e headers: ssllabs.com/ssltest (meta: A ou A+) e securityheaders.com (meta: A).

### ⭐ 3.12 Backup e restore drill

```bash
sudo systemctl start inventarium-backup.service
sudo journalctl -u inventarium-backup.service -n 20 --no-pager     # "enviado ao bucket inventarium-backups"
ls -lh /var/backups/inventarium/
sudo /opt/inventarium-app/bin/restore-postgres.sh /var/backups/inventarium/<arquivo>.dump   # restore drill
```

Confira também o objeto no bucket pelo Console da OCI (*Object Storage → Buckets → inventarium-backups → postgres/*). O drill restaura num banco temporário, mostra a contagem de linhas por tabela e o remove; não toca na produção.

## 4. Operação

### 4.1 Novo deploy (a cada release)

O pipeline chama o webhook; o Portainer baixa o compose da `main` e as imagens `IMAGE_TAG` do GHCR (`pull_policy: always`) e recria os containers. Acompanhe em *Stacks → inventarium* ou `sudo docker ps`. Se o secret `PORTAINER_STACK_WEBHOOK_URL` não existir, o deploy é manual: no Portainer, *Stacks → inventarium → Pull and redeploy* (com *Re-pull image*). Em caso de falha do job `deploy-production`, veja o log do workflow (a URL do webhook nunca aparece) e `docker logs portainer`.

### 4.2 Rollback

No Portainer: *Stacks → inventarium → Environment variables*, troque `IMAGE_TAG` para a versão anterior (por exemplo `v5.0.1`), marque *Re-pull image and redeploy* e atualize. Para voltar ao fluxo normal, ponha `IMAGE_TAG=latest` de novo (senão os próximos webhooks continuam redeployando a versão fixada). **O rollback de imagem não reverte migrações do Liquibase**: se a versão nova alterou o schema de forma incompatível, restaure o backup (4.5).

### 4.3 Atualizar o Nginx ou os scripts

Depois do merge de um PR em `infra/nginx/` ou `infra/scripts/`, na raiz do repositório atualizado (Git Bash):

```bash
infra/scripts/deploy-nginx.sh <IP_RESERVADO>
```

O script valida (`nginx -t`) e restaura a configuração anterior se falhar. Para testar a configuração antes: `bash infra/scripts/test-nginx-config.sh` (requer Docker).

O freio por IP do login (zona `auth`, só no `location = /auth/login` de `21-api.conf`) e o `429` com CORS e `Retry-After` (`snippets/login-rate-limited.conf`) fazem parte desta configuração: o merge de um PR que os altere só vale em produção depois deste `deploy-nginx.sh`. O backend limita por usuário e não depende do IP.

### 4.4 Certificados

A renovação é automática (2x ao dia, só renova com menos de 30 dias). Para forçar ou investigar:

```bash
systemctl list-timers inventarium-certbot-renew.timer
sudo journalctl -u inventarium-certbot-renew.service -n 50 --no-pager
sudo /opt/inventarium-app/bin/renew-certificates.sh
```

### 4.5 Backup e restauração

- Backups diários às 03:00 UTC; local por 7 dias, bucket por 30 dias (`backup_retention_days`).
- **Restaurar em produção** (procedimento manual, com indisponibilidade):

  ```bash
  # 1. baixe o dump do bucket (ajuste NAMESPACE e o nome do arquivo)
  sudo docker run --rm --user 0 -e OCI_CLI_AUTH=instance_principal -v /var/backups/inventarium:/backups \
    ghcr.io/oracle/oci-cli:latest os object get --namespace-name NAMESPACE \
    --bucket-name inventarium-backups --name postgres/ARQUIVO.dump --file /backups/ARQUIVO.dump
  # 2. valide antes (restore drill)
  sudo /opt/inventarium-app/bin/restore-postgres.sh /var/backups/inventarium/ARQUIVO.dump
  # 3. pare a aplicação, recrie o banco e restaure
  sudo docker stop inventarium-front inventarium-back
  sudo docker exec inventarium-postgres sh -c 'dropdb -U "$POSTGRES_USER" "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'
  sudo docker exec -i inventarium-postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --exit-on-error' < /var/backups/inventarium/ARQUIVO.dump
  # 4. suba de novo
  sudo docker start inventarium-back inventarium-front
  ```

- O volume do banco chama-se `inventarium_postgres-data` e o projeto `inventarium` (fixos no compose). Não recrie a stack com outro nome.

### 4.6 IP de admin mudou

1. Atualize `allowed_ssh_cidrs` e `allowed_admin_cidrs` em `terraform.tfvars` e rode `terraform apply` (o NSG atualiza sem recriar a VM; o `ignore_changes` evita recriar a VM por causa do cloud-init).
2. Atualize a allowlist do Nginx na VM: edite `ADMIN_CIDRS` em `/etc/inventarium/edge.env` e rode `sudo /opt/inventarium-app/bin/install-nginx-config.sh /opt/inventarium-app/src/nginx`.
3. Se você perdeu o SSH, use o *Console connection* da OCI (Compute → instância → Console connection) para entrar.

### 4.7 Senha do Portainer perdida

```bash
sudo docker stop portainer
sudo docker run --rm -v portainer_data:/data portainer/helper-reset-password
sudo docker start portainer
```

O comando imprime uma senha nova para o usuário `admin`. Troque-a logo depois de entrar.

### 4.8 Endurecer HSTS e passar a impor a CSP (depois de ~1 semana)

1. HSTS: em `infra/nginx/app-vm/snippets/headers-common.conf`, troque `max-age=300` por `max-age=31536000`. Só faça isso quando todos os subdomínios estiverem estáveis em HTTPS.
2. CSP: em `snippets/headers-front.conf`, troque o nome do header `Content-Security-Policy-Report-Only` por `Content-Security-Policy`, depois de navegar pelo app (login Google, leitor de QR, exportações, upload) sem avisos no console.
3. Abra um PR, faça o merge e rode `deploy-nginx.sh` (4.3).

### 4.9 Rotação de segredos

- `SECURITY_TOKEN_SECRET`: gere outro (`openssl rand -hex 32`), atualize na stack do Portainer e redeploy. **Todos os usuários serão deslogados.**
- Senha do Postgres: altere no banco (`ALTER USER`) e na stack ao mesmo tempo.
- Webhook: no Portainer, desative e reative *GitOps updates → Webhook* para gerar outro e atualize o secret do GitHub.

## 5. Solução de problemas

| Sintoma | Causa provável | O que fazer |
| --- | --- | --- |
| `issue-certificates.sh` diz que o DNS não bate | Registros A ainda não propagaram ou apontam para outro IP | Aguarde e confira com `Resolve-DnsName`; compare com `terraform output app_public_ip` |
| Let's Encrypt falha no desafio | Porta 80 fechada para a internet ou CAA bloqueando | Confira `allowed_web_cidrs = ["0.0.0.0/0"]` e o registro CAA |
| `https://…` responde 502 | Stack ainda não subiu ou o container está reiniciando | `sudo docker ps`, `sudo docker logs inventarium-back --tail 50` |
| `portainer.…` responde 403 | Seu IP não está em `allowed_admin_cidrs` | Veja 4.6 |
| Login Google falha com erro de origem | Origem não cadastrada no Google | Passo 3.5 |
| Front chama a URL errada da API | A release foi gerada antes de trocar `NEXT_PUBLIC_API_URL` | Troque a variável (3.6) e gere nova release |
| Navegador bloqueia chamadas à API (CORS) | `CORS_ALLOWED_ORIGINS` diferente da origem do front | Ajuste na stack do Portainer e redeploy |
| `install-nginx-config.sh` falha e restaura | Configuração inválida | Veja a mensagem do `nginx -t`; corrija e rode `deploy-nginx.sh` de novo |
| Backup falha | Dynamic group/policy ausentes ou OCI CLI sem permissão | `journalctl -u inventarium-backup.service`; confira `manage_backup_iam` |

## 6. Limitações conhecidas

- O Portainer concentra o controle do Docker e os segredos da stack; a proteção principal é a allowlist de IPs.
- A CSP do frontend mantém `'unsafe-inline'` em scripts por causa do Next.js (evolução: nonce).
- A tag `latest` é mutável; para auditoria e rollback use as tags `vX.Y.Z` (ver 4.2). Com `latest` na stack, uma release que falhe no smoke test já está no ar: o rollback é manual.
- O rollback de imagem não desfaz migrações do banco.
- Falhas de renovação do certificado só viram alerta quando a observabilidade tiver uma regra para isso.
- O payload da API do Portainer para criar a stack Git com webhook deve ser revalidado ao atualizar a versão do Portainer.
