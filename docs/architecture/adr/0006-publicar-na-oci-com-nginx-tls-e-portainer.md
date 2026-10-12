# ADR 0006 - Publicar o Inventarium na OCI com Nginx, TLS Let's Encrypt e Portainer

## Status

Accepted

## Contexto

O Terraform já provisiona a rede e duas VMs Oracle Linux 8 na OCI: uma VM de aplicação (subnet pública) e uma VM de observabilidade (subnet privada, sem IP público). Faltava definir como a aplicação chega ao ar: quem termina o TLS, como o domínio é servido, como as imagens do GHCR chegam à VM, onde ficam os segredos, como se faz backup e como o SonarQube e a observabilidade são alcançados.

Restrições:

- o Google Identity Services exige origem HTTPS com hostname (não aceita IP);
- o front e a API são publicados em origens diferentes (`inventariumifpe.me` e `api.inventariumifpe.me`), então o CORS precisa ser restrito às origens configuradas;
- `NEXT_PUBLIC_API_URL` é embutida no build do front, então a URL da API é decidida antes da release;
- segredos não podem passar pelo Terraform, pelo user-data nem pelo Git;
- a equipe de infraestrutura é pequena: a operação precisa ser reproduzível e testável sem acesso à OCI.

## Decisao

1. **Domínio e hosts.** `inventariumifpe.me` (front), `api.` (API), `portainer.`, `sonar.` e `grafana.`, todos apontando para um **IP público reservado** da VM de aplicação. O DNS fica na Namecheap.
2. **Nginx no host da VM** faz o proxy reverso e termina o TLS. A configuração é versionada em `infra/nginx/app-vm/` e instalada por `install-nginx-config.sh`, que valida com `nginx -t` e restaura o backup se falhar. Back e front escutam apenas em `127.0.0.1`.
3. **TLS com Let's Encrypt (HTTP-01)**, um único certificado SAN para os seis nomes, emitido e renovado pelo `certbot` **em container** (o pacote do Oracle Linux 8 tem conflito de dependências Python). Renovação por `systemd timer`, duas vezes ao dia.
4. **Headers de segurança no Nginx**: HSTS (`includeSubDomains`, sem `preload`), `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy`, COOP e CSP. A CSP do front começa em `Report-Only` e passa a ser imposta após a validação. O Nginx é a fonte única desses headers e descarta os que o Spring repete.
5. **Portainer CE (versão fixa)** escuta só em `127.0.0.1:9443` e é publicado por `portainer.<domínio>` com **allowlist de IPs de admin** gerada a partir do Terraform. Somente o caminho do webhook de stack (`POST`, com rate limit) é público.
6. **Entrega da aplicação por GitOps.** O script `bootstrap-portainer-stack.sh` cria no Portainer uma stack Git que lê o `docker-compose.production.yml` da `main`; os segredos entram por `scp` de um `.env` apagado em seguida. A cada release o pipeline chama o webhook da stack, que baixa as imagens do GHCR (públicas). O rollback é trocar `IMAGE_TAG`.
7. **Sonar e Grafana** rodam na VM privada de observabilidade e são alcançados pelo Nginx da VM de aplicação (IP privado fixo). O Sonar é público com login obrigatório (o scanner do GitHub precisa chegar nele); o Grafana só para IPs de admin.
8. **Backups.** `pg_dump` diário para o Object Storage com *instance principal* (sem chaves na VM), retenção por *lifecycle policy*, e restore drill documentado.
9. **Terraform não recria a VM por mudança de cloud-init.** O Nginx e os scripts viajam no `user_data` apenas no primeiro boot; `ignore_changes` impede que edições posteriores recriem a VM que guarda o volume do Postgres. Atualizações usam `infra/scripts/deploy-nginx.sh`.

## Consequencias

- Uma única origem pública de entrada (portas 80/443); a 9443 deixa de existir no NSG.
- A configuração do Nginx e dos scripts é revisada por PR e coberta por testes: invariantes de segurança, `nginx -t`, execução no Oracle Linux 8, `shellcheck`, `terraform fmt/validate`.
- O Portainer concentra controle do Docker e os segredos da stack; a proteção por allowlist de IP é essencial e exige manutenção quando o IP de admin muda.
- O Sonar fica exposto à internet (protegido por login), em troca de permitir análise no CI sem VPN.
- O deploy depende do webhook (segredo). Se ele vazar, alguém pode disparar um redeploy, mas apenas das imagens da própria organização.
- A tag `latest` é mutável; auditoria e rollback dependem das tags de versão (`vX.Y.Z`).
- A primeira aplicação do Terraform recria as VMs se elas já existirem (mudança de rede/cloud-init); depois disso o `ignore_changes` as protege.

## Alternativas Consideradas

- **Load Balancer da OCI com certificado gerenciado:** menos operação de TLS, mas custo recorrente e menos controle sobre os headers. Descartado neste momento.
- **Certificado do GitHub Student Pack:** válido por um ano e renovação manual; o Let's Encrypt é automático e gratuito.
- **Traefik/Caddy em container:** emissão de certificado embutida, porém a equipe já usa Nginx e o requisito inicial era Nginx.
- **DNS-01:** permitiria restringir 80/443, mas exige automatizar o DNS do provedor (credenciais de API extras); como o site é público, o HTTP-01 basta.
- **Mesmo domínio com prefixo `/api`:** um único origin e sem CORS, mas o time preferiu `api.` por separar as superfícies e permitir cookies `SameSite=Strict` entre os subdomínios.
- **Deploy por SSH a partir do GitHub Actions:** exigiria chave de deploy e SSH aberto para a internet; o webhook evita isso.
- **Segredos no Vault da OCI:** melhor rotação e auditoria, porém mais complexidade agora; fica como evolução.

## Riscos

- Portainer com `docker.sock`: comprometimento dele equivale a root na VM.
- IP residencial dinâmico do admin pode travar o acesso ao Portainer/Grafana até atualizar a allowlist.
- Falha de renovação do certificado passa despercebida sem alerta (a observabilidade ainda precisa de uma regra).
- O payload da API do Portainer (stack Git com webhook) depende da versão fixada; deve ser validado a cada atualização do Portainer.
- Rollback de imagem não reverte migrações do Liquibase.

## Referencias

- Runbook: `docs/wiki/Deploy-em-Producao-OCI.md`
- Modelagem de ameaças: `docs/wiki/Modelagem-de-Ameacas.md` (seção de infraestrutura)
- Código: `infra/terraform/oci/`, `infra/nginx/app-vm/`, `infra/scripts/`, `docker-compose.production.yml`
- Issues: #207, #208, #210, #211, #212, #216

## Decisões Relacionadas

- [ADR 0002](./0002-definir-autenticacao-com-google.md): o login Google exige HTTPS.
- [ADR 0005](./0005-adotar-api-rest-stateless.md): a revisão de CORS e HTTPS pedida por ele é tratada aqui e no CORS configurável do backend.
