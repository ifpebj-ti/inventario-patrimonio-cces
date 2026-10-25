# ADR 0007 - Stack de observabilidade e SonarQube na VM privada

## Status

Accepted

## Contexto

A VM de observabilidade (subnet privada, sem IP público) só tinha Docker e Nginx instalados pelo cloud-init. O ADR 0006 definiu que o Sonar e o Grafana moram nela e são alcançados pelo Nginx da VM da aplicação, mas não havia nada rodando: sem métricas de consumo, sem logs centralizados, sem traces e sem SonarQube. Depois da queda da VM da aplicação, a equipe precisa enxergar consumo de CPU, memória e disco e correlacionar erros da API com logs e traces.

Restrições: projeto acadêmico com custo baixo, uma VM de 2 OCPUs / 8 GB, sem Kubernetes, segredos fora do Git e do Terraform, operação reproduzível sem acesso à OCI.

## Decisao

1. **Stack LGTM em modo monolítico, em Docker Compose** (`infra/observability/docker-compose.observability.yml`): Prometheus (métricas), Loki (logs), Tempo (traces) e Grafana (visualização), mais SonarQube Community com PostgreSQL próprio. Todas as imagens com **versão fixa**, nunca `latest`.
2. **Push, não pull.** O coletor (Grafana Alloy) da VM da aplicação **envia** métricas (Prometheus remote write), logs (Loki) e traces (Tempo, OTLP) para a VM de observabilidade. Assim as portas do backend e do frontend continuam só em loopback e a VM privada não precisa alcançar a VM pública.
3. **Rede.** As portas 3000, 3100, 4317, 4318, 9000 e 9090 ficam abertas no NSG **somente para a subnet da aplicação** (`observability_stack_ports` em `locals.tf`). Um teste automatizado garante que essas portas são exatamente as publicadas pelo Compose.
4. **Limite de memória em todos os serviços** (soma de ~6,8 GB, SonarQube com 3 GB). Sem limite, o SonarQube consome a VM inteira. Um teste automatizado impede serviço sem limite e soma acima de 7 GB.
5. **Retenção curta por causa do disco:** métricas 15 dias (teto de 8 GB), logs 7 dias, traces 3 dias.
6. **Segredos** (senha do Grafana e do Postgres do Sonar) são gerados na VM em `/opt/inventarium-observability/.env` (0600) na primeira execução; nunca passam pelo Git, Terraform ou user-data.
7. **Instalação por script, não por cloud-init.** `infra/scripts/deploy-observability.sh` copia a stack por SSH (via a VM da aplicação como salto) e roda `bootstrap-observability.sh`, que ajusta `vm.max_map_count` para o SonarQube, valida o Compose e sobe os containers. É idempotente e atualiza uma VM que já existe sem recriá-la, no mesmo critério do `deploy-nginx.sh`.
8. **Tempo 2.10 e não 3.x**, porque a série 3 mudou a arquitetura de ingestão e a configuração monolítica validada é a da 2.x.

## Consequencias

- É possível ver consumo, logs e traces num só lugar (Grafana) e analisar qualidade de código no SonarQube.
- Loki, Tempo e Prometheus **não têm autenticação**: a proteção é só o NSG (acesso restrito à subnet da aplicação). Qualquer processo comprometido na VM da aplicação pode escrever (e ler) telemetria.
- A VM passa a ser um ponto único de falha da observabilidade; os dados vivem em volumes Docker na própria VM e **não têm backup** (aceito: telemetria é descartável; o SonarQube pode ser reanalisado).
- Memória apertada: SonarQube + LGTM em 8 GB deixam pouca folga.

## Alternativas Consideradas

- **Prometheus puxando (scrape) da VM da aplicação:** exigiria abrir o node_exporter, o cAdvisor e o backend para a VM privada. Descartado em favor do push.
- **Stack ELK / OpenSearch:** consome mais memória que Loki e não cabe junto do SonarQube em 8 GB.
- **Serviço gerenciado (OCI Monitoring/Logging):** custo e dependência maior; a equipe quer aprender e controlar a pilha.
- **Instalar tudo no cloud-init:** só roda no primeiro boot e a VM já existe; o script versionado atualiza a VM existente.

## Riscos

- Estouro de disco por retenção mal calculada: acompanhar o uso do volume e ajustar a retenção.
- Estouro de memória do SonarQube: o container é reiniciado pelo limite; aumentar a VM ou o limite se ocorrer.
- Telemetria sem autenticação: não colocar dados sensíveis em logs e traces (ver a política do backend).

## Referencias

- `infra/observability/`, `infra/scripts/deploy-observability.sh`, `infra/scripts/observability-vm/bootstrap-observability.sh`
- ADR 0006 e `docs/wiki/Deploy-em-Producao-OCI.md` (seção 4.10)
- Issue #211
