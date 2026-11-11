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
| `grafana.inventariumifpe.me` | Grafana (VM privada) | Público, com login obrigatório e limite de tentativas por IP (seção 4.13) |

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

Quatro arquivos são **gerados na VM** e nunca vão para o Git: `/etc/nginx/inventarium/generated/admin-allowlist.conf` (de `allowed_admin_cidrs`), `upstreams.conf` (IP privado da observabilidade) e, para o Load Balancer (seção 4.14), `real-ip.conf` e `lb-allowlist.conf` (de `LB_CIDR`). Veja os exemplos em `infra/nginx/app-vm/generated.example/`.

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

O resultado (por exemplo `198.51.100.7`) vira `198.51.100.7/32` em `allowed_ssh_cidrs` e `allowed_admin_cidrs`. Internet residencial costuma ter IP dinâmico; se o seu mudar, você perde o SSH e o Portainer até atualizar a lista (veja 4.6); o Grafana é público com login (seção 4.13). Dá para listar mais de um CIDR, por exemplo casa e IFPE.

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
   # Opcional: contas de teste (uma senha por conta, mínimo 12 caracteres). Vazias = desligadas.
   # Ligue só se quiser que pessoas testem em produção; senhas fortes (openssl rand -base64 24).
   TEST_VIEWER_PASSWORD=
   TEST_OPERATOR_PASSWORD=
   TEST_MANAGER_PASSWORD=
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

### 4.10 VM de observabilidade (Prometheus, Loki, Tempo, Grafana e SonarQube)

A VM privada roda a stack de `infra/observability/` (ADR 0007). **Conceitos:** *métricas* são números ao longo do tempo (CPU, memória, requisições) guardados pelo **Prometheus**; *logs* são as mensagens dos containers, guardadas pelo **Loki**; *traces* mostram o caminho de uma requisição pelo sistema, guardados pelo **Tempo**; o **Grafana** é a tela que lê os três; o **SonarQube** analisa a qualidade do código.

**Estado atual:** esta seção instala a stack na VM de observabilidade e abre a rede. Sem coletor o Grafana abre com os três *datasources* funcionando, mas sem dados; o coletor Alloy (seção 4.11) envia o consumo e os logs das VMs. As métricas da aplicação e os traces estão na seção 4.12.

**1. Aplicar o Terraform** (aumenta a VM para 2 OCPUs / 8 GB e abre as portas no NSG para a subnet da aplicação):

```powershell
cd infra\terraform\oci
terraform plan -out tfplan
terraform apply tfplan
```

Confira no plano que a VM de observabilidade só é **redimensionada** (`update in-place`), nunca recriada (`replace`). O redimensionamento reinicia a VM.

**2. Instalar a stack** (na raiz do repositório, na sua máquina; o SSH passa pela VM da aplicação):

```powershell
bash infra/scripts/deploy-observability.sh 137.131.249.225
```

O script envia os arquivos, ajusta o kernel para o SonarQube (`vm.max_map_count`), gera os segredos em `/opt/inventarium-observability/.env` (0600) na primeira vez, valida o Compose e sobe os containers. Pode ser repetido para atualizar. Para ver a senha do Grafana:

```powershell
ssh -J opc@137.131.249.225 opc@10.42.0.74 "sudo grep GRAFANA_ADMIN_PASSWORD /opt/inventarium-observability/.env"
```

**3. Como testar**

```bash
# Na VM da aplicação (ssh opc@<IP>): confere as portas pela rede privada (esperado: respostas OK)
curl -s http://10.42.0.74:9090/-/ready        # Prometheus: "Prometheus Server is Ready."
curl -s http://10.42.0.74:3100/ready          # Loki: "ready" (pode levar ~15 s)
curl -s http://10.42.0.74:3000/api/health     # Grafana: "database": "ok"
curl -s http://10.42.0.74:9000/api/system/status   # SonarQube: "status":"UP" (leva alguns minutos)

# Enviar um log de teste e ler de volta
curl -s -X POST http://10.42.0.74:3100/loki/api/v1/push -H 'Content-Type: application/json' \
  -d "{\"streams\":[{\"stream\":{\"job\":\"teste\"},\"values\":[[\"$(date +%s)000000000\",\"ola observabilidade\"]]}]}"
```

Depois abra `https://grafana.inventariumifpe.me` (só IP de admin), entre com `admin` e a senha acima, vá em *Connections → Data sources* e clique em **Test** em Prometheus, Loki e Tempo (os três devem dizer "successfully"), e em *Explore → Loki* consulte `{job="teste"}`. O SonarQube abre em `https://sonar.inventariumifpe.me`; o login inicial é `admin` / `admin` e **a senha deve ser trocada no primeiro acesso**.

Na VM de observabilidade: `sudo docker ps` (todos `Up`) e `sudo docker stats --no-stream` (memória de cada serviço dentro do limite).

**Limitações:** Loki, Tempo e Prometheus não têm autenticação (só o NSG protege); os dados não têm backup; a retenção é de 15 dias (métricas), 7 dias (logs) e 3 dias (traces). Se o SonarQube reiniciar em loop, confirme `sysctl vm.max_map_count` (deve ser 262144).


### 4.11 Coletor Grafana Alloy (consumo e logs das VMs)

O **Alloy** é o "carteiro": roda em cada VM (container `alloy`, ver `infra/alloy/`), lê o consumo do servidor (CPU, memória, disco, rede), o consumo de cada container e os logs dos containers, e **envia** tudo para a VM de observabilidade (métricas para o Prometheus, logs para o Loki). Ele não abre porta para fora (a interface dele escuta só em `127.0.0.1:12345`). O dashboard **Inventarium - Infraestrutura** (pasta *Inventarium* no Grafana) mostra esses dados e tem um seletor de VM (`app` ou `observability`).

As métricas da aplicação (requisições, JVM) e os traces vêm do backend e estão na seção 4.12.

**Instalar** (na raiz do repositório, no Git Bash; o Alloy só funciona depois da stack da seção 4.10):

```bash
# 1) Levar o dashboard novo ao Grafana (idempotente; o script reinicia o Grafana para ele ler o provisionamento)
bash infra/scripts/deploy-observability.sh 137.131.249.225
# 2) Alloy na VM da aplicação (envia para 10.42.0.74)
bash infra/scripts/deploy-alloy.sh app 137.131.249.225
# 3) Alloy na VM de observabilidade (envia para ela mesma, 127.0.0.1)
bash infra/scripts/deploy-alloy.sh observability 137.131.249.225
```

**Como testar**

- Em cada VM: `sudo docker ps` mostra o container `alloy` como `Up`, e `sudo docker logs alloy --tail 30` não deve ter erros (um aviso sobre `udev` é inofensivo).
- No Grafana, abra *Dashboards → Inventarium → Inventarium - Infraestrutura* e escolha a VM no seletor: os medidores de CPU, memória e disco e os gráficos aparecem em 1 a 2 minutos.
- Em *Explore → Prometheus*, a consulta `node_memory_MemTotal_bytes` deve devolver uma série por VM (rótulo `vm`). Em *Explore → Loki*, `{vm="app"}` mostra os logs dos containers (backend, front, Postgres, Portainer).

**Se faltar algo:** os painéis *CPU/Memória por container* dependem do cAdvisor embutido, que lê os cgroups do Docker; se ficarem vazios, confira `sudo docker logs alloy` e abra uma issue (a VM usa Docker com `overlay2` e cgroup v1, o caso suportado). O Alloy precisa do socket do Docker (somente leitura) e de leitura do host: esse acesso equivale ao que o Portainer já tem e está registrado no modelo de ameaças (I-15). O consumo de memória do Alloy é limitado a 512 MB.


### 4.12 Métricas e traces do backend

O backend passa a expor **métricas Prometheus** e a enviar **traces OpenTelemetry**. As métricas dão números da aplicação (requisições por segundo, erros 5xx, latência, memória da JVM, conexões com o banco); os traces mostram o caminho de uma requisição e quanto tempo cada etapa levou. Eles aparecem no dashboard **Inventarium - Backend** (métricas) e em *Explore → Tempo* (traces); o log de uma requisição leva o `traceId` entre colchetes e o Grafana oferece o link para o trace.

**Porta de gerenciamento (8081).** O Actuator inteiro (health e `/actuator/prometheus`) existe **somente** na porta 8081 do backend, publicada em `127.0.0.1` da VM. Na porta pública da API (8080) ele não existe (responde 403). O Alloy lê as métricas em `127.0.0.1:8081`, e o Nginx encaminha à 8081 **apenas** o health (`/actuator/health`, `/liveness`, `/readiness`); `/actuator/prometheus` nunca é encaminhado e o resto de `/actuator/` devolve 404. O healthcheck do container também usa a 8081.

**Ordem de atualização.** O `upstream` do Nginx prefere a 8081 e usa a 8080 como reserva enquanto o backend antigo ainda estiver no ar. Por isso **atualize o Nginx primeiro**: Nginx novo com backend antigo funciona (usa a reserva), mas Nginx antigo com backend novo perde o health público, porque o Nginx antigo ainda consulta a 8080, onde o Actuator não existe mais. Com o Nginx novo, ele leva até 10 segundos para passar da reserva para a 8081 depois que o backend novo sobe. Rode, na ordem:

```bash
bash infra/scripts/deploy-nginx.sh 137.131.249.225            # Nginx (health para a 8081)
bash infra/scripts/deploy-alloy.sh app 137.131.249.225        # Alloy passa a ler o backend em 127.0.0.1:8081
```

O backend novo chega pelo webhook da release. A **porta 8081 só é publicada** pelo compose novo (o webhook baixa o compose da `main`, então a stack a recebe no redeploy).

**Ligar os traces** (vêm desligados). No Portainer, em *Stacks → inventarium → Environment variables*, acrescente e faça o redeploy:

| Variável | Valor |
| --- | --- |
| `TRACING_EXPORT_ENABLED` | `true` |
| `TRACING_OTLP_ENDPOINT` | `http://10.42.0.74:4318/v1/traces` |
| `TRACING_SAMPLING_PROBABILITY` | `1.0` (todas as requisições; reduza se o disco do Tempo apertar) |

**Como testar**

```bash
# Na VM da aplicação: as métricas existem na porta de gerenciamento (loopback)...
curl -s http://127.0.0.1:8081/actuator/prometheus | head -5
# ...e NÃO existem na porta pública nem pela internet (esperado: 403 e 404)
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8080/actuator/prometheus
curl -s -o /dev/null -w "%{http_code}\n" https://api.inventariumifpe.me/actuator/prometheus
```

No Grafana, abra *Dashboards → Inventarium → Inventarium - Backend* (escolha a VM `app`): os medidores e gráficos enchem em 1 a 2 minutos. Para ver traces, faça algumas ações no sistema e abra *Explore → Tempo → Search* (serviço `inventory`); há um trace por requisição, com nome como `http get /sectors`. Em *Explore → Loki*, `{container="inventarium-back"}` mostra os logs; clique no `traceId` para abrir o trace.

**Limitações:** `/actuator/prometheus` não tem autenticação (a proteção é estar só em loopback; outros containers da rede `inventarium-net` alcançam `inventarium-back:8081`); o `/actuator/health` agregado também testa o envio de e-mail (SMTP) e pode demorar segundos se o Gmail estiver lento (o healthcheck do container usa só o `liveness`, que não depende do Gmail); logs e traces não podem conter dados sensíveis, pois o Loki e o Tempo não têm autenticação; as leituras do Alloy e os spans do filtro do Spring Security são descartados antes do Tempo para não poluí-lo.

### 4.13 Grafana público com login (ADR 0008)

O Grafana abre pela internet em `https://grafana.inventariumifpe.me`, **sem lista de IPs**, porque o ambiente é de homologação e o professor aceitou o acesso público desde que exija usuário e senha. O Portainer continua restrito a `allowed_admin_cidrs`, e o Sonar já era público com login. Quem não autentica não vê dashboard, datasource nem dado.

**O que protege o Grafana**

| Camada | O quê |
| --- | --- |
| Login obrigatório | Acesso anônimo, cadastro, dashboards públicos, snapshots externos e `/metrics` desligados (`GF_*` em `docker-compose.observability.yml`); cookie só por HTTPS |
| Bloqueio por usuário | O Grafana bloqueia o usuário por alguns minutos depois de falhas seguidas de senha |
| Nginx, por IP | `POST /login` limitado a 10 tentativas por minuto (zona `grafana_login`, burst 5) e uso geral a 30 requisições por segundo (zona `grafana`, burst 100); acima disso o Nginx devolve `429` |
| Senha forte | A senha do `admin` é gerada na VM com `openssl rand -hex 16` e nunca vai ao Git |

**Colocar no ar** (depois do merge, a partir da raiz do repositório, no Git Bash):

```bash
# 1) Nginx: remove a allowlist do Grafana e cria as zonas de limite (idempotente, valida com nginx -t e volta atrás se falhar)
infra/scripts/deploy-nginx.sh <IP_RESERVADO>

# 2) Grafana: aplica as variáveis de endurecimento (recria o container; os dashboards e o banco do Grafana ficam no volume)
infra/scripts/deploy-observability.sh <IP_RESERVADO>
```

Faça o passo 1 e depois o 2. Valide **sem estar logado**, de qualquer rede:

```bash
curl -sI https://grafana.inventariumifpe.me/ | head -3                          # 302 para /login
curl -s -o /dev/null -w '%{http_code}\n' https://grafana.inventariumifpe.me/api/dashboards/home   # 401
curl -s -o /dev/null -w '%{http_code}\n' https://grafana.inventariumifpe.me/metrics               # nao pode ser 200 com dados
```

**Criar um usuário só de leitura (por exemplo, para o professor).** Não compartilhe o `admin`. Entre como `admin`, abra *Administration → Users and access → Users → New user*, preencha nome, e-mail, usuário e uma senha de pelo menos 12 caracteres, e depois, em *Organization role*, escolha **Viewer** (só consulta; não edita dashboards nem datasources). Mande a senha por um canal privado, nunca por issue, PR ou chat público. Para a senha do `admin`, veja a seção 4.10.

**Rollback (voltar a restringir por IP):** em `infra/nginx/app-vm/sites-tls/32-grafana.conf`, acrescente de novo `include /etc/nginx/inventarium/generated/admin-allowlist.conf;` logo depois dos `include` de cabeçalhos e rode `deploy-nginx.sh`. As variáveis de endurecimento do Grafana podem ficar.

**Limitações conhecidas:** o endereço `grafana.inventariumifpe.me` fica exposto a varreduras e a vulnerabilidades do próprio Grafana, então mantenha a versão da imagem atualizada; o nome de usuário `admin` é conhecido (a defesa é a senha forte, o bloqueio por usuário e o limite do Nginx); o acesso passa a depender só da senha, sem segundo fator. Se o ambiente deixar de ser de homologação, volte à allowlist ou ponha o Grafana atrás de uma VPN ou de um login corporativo.


### 4.14 Preparar a VM para o Load Balancer e o WAF (ADR 0009)

Esta seção cobre a **primeira etapa** da entrada do Load Balancer (LB) da OCI com WAF na frente da aplicação: preparar o Nginx e o certificado, **sem mudar o tráfego atual**. O Terraform do LB e do WAF (etapa 2) e o cutover do DNS (etapa 3) vêm depois. Enquanto `LB_CIDR` e `LB_ID` não existem no `edge.env`, tudo continua como antes.

**O que muda na VM**

| Item | O que faz |
| --- | --- |
| `LB_CIDR` (`edge.env`) | Subnet do LB. O Nginx só confia no `X-Forwarded-For` quando a conexão vem dela e passa a ver o **IP real do cliente** (rate limit por IP, allowlist do Portainer e log). Um cabeçalho forjado por quem chega direto na 443 é ignorado |
| Porta **9080** | HTTP simples vindo do LB, que já terminou o TLS. Todos os vhosts escutam nela, ao lado da 443. O backend continua vendo `X-Forwarded-Proto: https` |
| Porta **9081** | Health check do LB (`/healthz`), aceito só de `LB_CIDR`. Não depende de Host nem de upstream |
| SELinux e firewalld | O `install-nginx-config.sh` rotula 9080 e 9081 como `http_port_t` e as libera no firewalld só para `LB_CIDR` (sem `--reload`, para não derrubar as regras do Docker) |
| `push-lb-certificate.sh` | Depois de cada renovação do certificado, envia o novo ao LB e troca o do listener HTTPS. Sem `LB_ID` não faz nada |

**Chaves novas do `/etc/inventarium/edge.env`** (todas opcionais; o Terraform não as aplica à VM existente, por causa do `ignore_changes`, então são editadas na mão, como `ADMIN_CIDRS` na seção 4.6):

| Chave | Valor | Quando |
| --- | --- | --- |
| `LB_CIDR` | subnet do LB, por exemplo `10.42.0.128/27` | Antes do cutover (etapa 2 cria a subnet) |
| `LB_ID` | OCID do load balancer | Depois que o LB existir |
| `LB_LISTENER_NAME` | nome do listener HTTPS (padrão `https`) | Só se o nome for outro |
| `LB_PUBLIC_IP` | IP público do LB | Para o `issue-certificates.sh` aceitar o DNS apontando para o LB |

**Aplicar a etapa 1** (depois do merge, no Git Bash, na raiz do repositório):

```bash
infra/scripts/deploy-nginx.sh <IP_RESERVADO>
```

O script instala o Nginx novo, rotula as portas e **confere que o Nginx passou a escutar na 9080 e na 9081**; se não escutar, restaura a configuração anterior e falha. Sem `LB_CIDR` o resultado é o mesmo comportamento de antes, mais as duas portas abertas só dentro da VM.

**Validar na VM** (`ssh opc@<IP_RESERVADO>`):

```bash
# O Nginx escuta nas duas portas
sudo ss -ltn | grep -E ':(9080|9081)\b'

# Vhost pela 9080 com Host correto: deve responder 200 (health da API)
curl -s -o /dev/null -w '%{http_code}\n' -H 'Host: api.inventariumifpe.me' http://127.0.0.1:9080/actuator/health

# Host desconhecido na 9080: conexão encerrada sem resposta (curl sai com erro 52)
curl -s -o /dev/null -H 'Host: qualquer.exemplo' http://127.0.0.1:9080/; echo "curl=$?"

# Health do LB: de 127.0.0.1 deve dar 403, porque só LB_CIDR é aceito
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:9081/healthz
```

**Certificado no LB.** Com `LB_ID` definido, o `renew-certificates.sh` (timer das 04:00 e 16:00) e o `issue-certificates.sh` chamam o `push-lb-certificate.sh`. Para o primeiro envio (o listener HTTPS só pode ser criado depois do primeiro certificado, então o modo `--bootstrap` cria só o certificado e imprime o nome para o Terraform; veja a seção 4.15):

```bash
sudo /opt/inventarium-app/bin/push-lb-certificate.sh --bootstrap
```

Ele usa a CLI da OCI em container com **instance principal** (política em `edge_iam.tf`, etapa 2). O estado fica em `/var/lib/inventarium/lb-cert.sha256`; se o envio falhar, o hash não é gravado e a próxima execução tenta de novo. A imagem é `ghcr.io/oracle/oci-cli:latest` (a Oracle só documenta essa tag): fixe uma versão ou digest em `OCI_CLI_IMAGE` quando puder.

**Rollback:** remova `LB_CIDR` (e `LB_ID`) do `edge.env` e rode `deploy-nginx.sh` de novo. O Nginx volta a ignorar o `X-Forwarded-For` e a 9081 volta a negar todos. As regras do firewalld criadas para o LB ficam (são só para a subnet do LB) e podem ser removidas com `firewall-cmd --permanent --remove-rich-rule=...`.

**Limitações conhecidas:** o `keepalive_timeout` do Nginx subiu para 350 s (acima do idle timeout planejado do LB), o que mantém conexões abertas por mais tempo mesmo no modo direto; as regras do firewalld não são removidas sozinhas se `LB_CIDR` mudar; o hook depende de `python3` e do SDK da OCI estarem na imagem da CLI (confirmar na primeira execução).


### 4.15 Criar o Load Balancer e o WAF em detecção (ADR 0009, etapa 2)

Cria no Terraform o **Load Balancer** (LB) e o **WAF**, e liga o HTTPS dele. **Nada muda para o usuário até o DNS apontar para o LB** (etapa 3): o site continua entrando direto pela VM. Pré-requisito: a etapa 1 (seção 4.14) aplicada.

**O que o Terraform cria** (`terraform plan` mostra `21 to add, 0 to change, 0 to destroy`; nada existente é alterado):

| Recurso | Para quê |
| --- | --- |
| Subnet pública `lb_public` (`10.42.0.128/27`) e NSG `lb` | O LB fica fora da subnet da VM. O NSG aceita 80 e 443 da internet e só fala com a VM nas portas 80, 9080 e 9081 |
| Regras no NSG da VM (`app_from_lb`) | A VM aceita 80, 9080 e 9081 **somente do NSG do LB**. A regra atual de `allowed_web_cidrs` continua até a etapa 3 |
| LB flexível (IP público reservado), backend sets `http` e `app`, listener 80 | Porta 80 vai ao Nginx (ACME e redirecionamento); saúde por HTTP na 9081 |
| Listener 443 (só com `lb_https_enabled = true`) | Termina o TLS 1.2/1.3 e fala HTTP com o Nginx na 9080 |
| WAF (`oci_waf_web_app_firewall`) e política | Grupos colaborativos OWASP de SQL injection, XSS, path traversal/LFI e RCE, mais um conjunto curado de regras individuais; tudo com a ação **CHECK** (só registra) |
| Log group e log do WAF | Onde se revisam os falsos positivos antes do bloqueio |
| Policy IAM (`manage_edge_iam = true`, opcional) | A VM pode atualizar certificado e listener **deste** LB (`use`, não `manage`) |

**Custo:** o WAF fica na franquia gratuita (1 instância e 10 milhões de requisições por mês, segundo a Oracle); o **LB flexível é cobrado por hora** e por banda acima do mínimo de 10 Mbps. Some o item *Load Balancer* no Cost Estimator da OCI antes do apply e confirme o valor.

**Passo a passo** (PowerShell, em `infra\terraform\oci`):

1. **Conferir o plano** (só leitura):

   ```powershell
   terraform plan
   ```

   Esperado: `21 to add, 0 to change, 0 to destroy`. Se aparecer qualquer `change` ou `destroy`, pare e investigue. O catálogo de proteção é lido da OCI no plano; se o plano falhar com "catálogo de proteção... SQL injection e XSS", a OCI mudou as chaves e `waf_collaborative_prefixes` em `waf.tf` precisa de revisão.

2. **IAM do certificado.** Se o seu usuário puder criar policies, defina `manage_edge_iam = true` no `terraform.tfvars`. Senão, peça a quem administra a tenancy a policy equivalente:
   `Allow dynamic-group inventarium-app-instances to use load-balancers in compartment id <compartment> where target.loadbalancer.id = '<lb_id>'`. Se a OCI recusar a condição `target.loadbalancer.id`, remova só a parte `where` (há um único LB).

3. **Aplicar** (primeira vez, sem HTTPS):

   ```powershell
   terraform apply
   terraform output lb_public_ip lb_id lb_subnet_cidr
   ```

4. **Na VM** (`ssh opc@<IP_RESERVADO>`), edite `/etc/inventarium/edge.env` e acrescente (sem aspas dentro das aspas):

   ```bash
   LB_CIDR="10.42.0.128/27"
   LB_ID="<saída lb_id>"
   LB_PUBLIC_IP="<saída lb_public_ip>"
   ```

   Depois, do seu computador (Git Bash): `infra/scripts/deploy-nginx.sh <IP_RESERVADO>`. Isso gera o `real-ip.conf`, abre as portas 9080 e 9081 no firewalld só para o LB e confere que o Nginx escuta nelas.

5. **Conferir a saúde do LB:** no console da OCI, *Networking → Load balancers → inventarium-lb → Backend sets*: `http` e `app` devem ficar **OK**. Se ficarem críticos, veja `sudo ss -ltn | grep -E ':(9080|9081)'` na VM e o NSG `app`.

6. **Primeiro certificado no LB** (o listener HTTPS só pode ser criado depois do certificado), na VM:

   ```bash
   sudo /opt/inventarium-app/bin/push-lb-certificate.sh --bootstrap
   ```

   O comando cria o certificado no LB e imprime `lb_bootstrap_certificate_name = "inventarium-AAAAMMDD-xxxxxxxx"`. Se falhar, confira o `OCI_CLI_IMAGE` (a imagem precisa ter `python3` e o SDK `oci`) e a policy do passo 2.

7. **Ligar o HTTPS:** no `terraform.tfvars` ponha `lb_https_enabled = true` e `lb_bootstrap_certificate_name = "<nome impresso>"`, e rode `terraform apply`. A partir daqui as renovações trocam o certificado do listener sozinhas (o Terraform ignora essa mudança).

8. **Testar o LB sem mexer no DNS** (do seu computador):

   ```bash
   curl --resolve inventariumifpe.me:443:<lb_public_ip> -sI https://inventariumifpe.me/ | head -3
   curl --resolve api.inventariumifpe.me:443:<lb_public_ip> -s https://api.inventariumifpe.me/actuator/health
   ```

   Os dois devem responder normalmente. Repita com `grafana.`, `portainer.` (403 se o seu IP não estiver na allowlist, o esperado) e `sonar.`.

9. **Ver o WAF registrando** (em detecção a resposta continua normal):

   ```bash
   curl --resolve api.inventariumifpe.me:443:<lb_public_ip> -s -o /dev/null -w '%{http_code}\n' "https://api.inventariumifpe.me/x?q=%27%20OR%201%3D1--"
   curl --resolve inventariumifpe.me:443:<lb_public_ip> -s -o /dev/null -w '%{http_code}\n' "https://inventariumifpe.me/?q=%3Cscript%3Ealert(1)%3C/script%3E"
   ```

   Depois olhe no console: *Observability & Management → Logging → Log Groups → inventarium-waf-logs → inventarium-waf → Explore Log*. Os eventos mostram a regra que disparou e a ação `CHECK`. Use o sistema normalmente por alguns dias (login Google, importar CSV, salvar observação, deploy) e anote o que o WAF registrou de legítimo: são os falsos positivos a tratar antes do bloqueio.

**Desfazer a etapa 2:** como o tráfego ainda não passa pelo LB, basta não apontar o DNS. Para remover tudo, `terraform destroy -target=oci_waf_web_app_firewall.main` e depois os demais recursos novos, ou reverta o commit e rode `terraform apply`. O certificado criado no LB some junto com ele.

**Limitações conhecidas:** a semântica exata de `CHECK` e o catálogo foram lidos da documentação e do plano, mas só o primeiro apply mostra se a OCI aceita tudo (limite de regras por política, por exemplo); o WAF inspeciona só os primeiros 8 KB do corpo; as exceções do bloqueio (webhook do Portainer, `actuator/health`, token Google, campo de senha) ainda não existem, porque dependem dos falsos positivos reais, e são a etapa 3; a imagem da CLI da OCI usa a tag `latest`.


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
