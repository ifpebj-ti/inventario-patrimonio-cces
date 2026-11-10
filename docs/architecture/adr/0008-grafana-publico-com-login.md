# ADR 0008 - Grafana público na internet com login obrigatório

## Status

Accepted

## Contexto

O ADR 0006 restringiu o Grafana aos IPs de `allowed_admin_cidrs`, aplicados pelo Nginx da VM da aplicação. Na prática isso exige que quem consulta os dashboards tenha IP fixo: com internet residencial, cada troca de IP pede `terraform apply` (o NSG, para o SSH) e a edição do `edge.env` na VM (a allowlist do Nginx), e o professor e os colegas não conseguem abrir o Grafana sem que a equipe libere o IP deles.

O ambiente é de homologação, para estudos, e o professor autorizou expor o Grafana desde que exija usuário e senha. O Sonar já segue esse modelo (público, com `sonar.forceAuthentication`).

## Decisao

1. **O vhost `grafana.inventariumifpe.me` deixa de incluir a allowlist de IPs.** O Portainer continua restrito a `allowed_admin_cidrs`, porque controla o Docker da VM e não tem esse motivo de uso.
2. **Login obrigatório, configurado por variáveis `GF_*`** em `infra/observability/docker-compose.observability.yml`: acesso anônimo desligado, cadastro desligado, dashboards públicos e snapshots externos desligados, `/metrics` desligado, cookie com `Secure` e `SameSite=lax`, Gravatar desligado e criação de organização desligada. Um teste do CI garante esses valores.
3. **Rate limit por IP no Nginx**, em duas zonas: `grafana_login` (10 por minuto, burst 5) só em `location = /login`, e `grafana` (30 por segundo, burst 100) no resto, mais folgada que a zona `api` porque uma tela de dashboard dispara dezenas de requisições. Testes do CI garantem as zonas e que a do login vale só no `/login`.
4. **Defesa contra força bruta em camadas:** o bloqueio de usuário do próprio Grafana, o limite por IP do Nginx e a senha do `admin` aleatória (`openssl rand -hex 16`) gerada na VM, fora do Git.
5. **Usuários de consulta têm o papel Viewer.** O `admin` não é compartilhado; o procedimento de criar o usuário está na seção 4.13 do guia de deploy.
6. **A decisão vale enquanto o ambiente for de homologação.** Se virar produção real, voltar à allowlist ou usar VPN ou login corporativo.

## Consequencias

- O Grafana abre de qualquer rede sem mexer no Terraform nem na VM, e o professor consegue consultar os dashboards.
- O endereço fica exposto a varreduras e a vulnerabilidades do próprio Grafana. A mitigação é a imagem com versão fixa, que precisa ser atualizada manualmente.
- O acesso depende só de senha, sem segundo fator. O usuário `admin` é um nome conhecido; a defesa é a senha forte, o bloqueio por usuário e o limite do Nginx.
- O Grafana expõe `/api/health` sem autenticação (é o comportamento dele); ele não traz dados da telemetria.
- Menos operação: a lista de IPs de admin passa a importar só para SSH e Portainer.

## Alternativas Consideradas

- **Manter a allowlist de IPs:** mais seguro, mas é a fonte do problema (IP dinâmico e liberação manual a cada troca).
- **VPN mesh (Tailscale):** tira a dor do IP e fecha o SSH da internet, mas exige instalar o agente em cada pessoa que consulta e uma mudança maior no Terraform e no Nginx. Boa opção para SSH e Portainer, fica como evolução.
- **Cloudflare Tunnel com Cloudflare Access:** login antes do Grafana sem porta de entrada, mas exige mover o DNS para a Cloudflare.
- **OAuth Google no Grafana (domínio do IFPE):** reaproveita o login que o projeto já usa, mas exige credenciais OAuth novas e configuração própria; fica como evolução.
- **OCI Bastion:** sessões temporárias manuais; não resolve o acesso do professor.

## Riscos

- Vulnerabilidade do Grafana explorável sem autenticação: acompanhar os avisos da versão fixada e atualizar a imagem.
- Força bruta distribuída (muitos IPs) contornando o limite por IP: o bloqueio por usuário do Grafana continua valendo. Se houver abuso, voltar à allowlist (rollback na seção 4.13) ou pôr o Cloudflare na frente.
- Compartilhamento indevido da senha do `admin`: usar usuários Viewer nominais e trocar a senha do `admin` se vazar.

## Referencias

- Issue #261
- `infra/nginx/app-vm/sites-tls/32-grafana.conf`, `infra/nginx/app-vm/conf.d/00-hardening.conf`, `infra/observability/docker-compose.observability.yml`
- `docs/wiki/Deploy-em-Producao-OCI.md` (seção 4.13) e `docs/wiki/Modelagem-de-Ameacas.md` (I-13 e I-17)

## Decisões Relacionadas

- ADR 0006: define o acesso por allowlist, que esta decisão substitui só para o Grafana.
- ADR 0007: define a stack de observabilidade.
