# Observabilidade no Inventarium: guia de estudo

Guia didático de como a observabilidade do Inventarium foi pensada, configurada e como os dados andam entre
as máquinas. Está escrito para estudar do zero e para explicar a outra pessoa. Todo número e nome de arquivo
aqui foi conferido no repositório. Quando algo não foi conferido, o texto diz.

Documentação oficial relacionada (mais técnica):
- `docs/architecture/adr/0007-stack-de-observabilidade-na-vm-privada.md` (a decisão e o porquê)
- `docs/wiki/Deploy-em-Producao-OCI.md`, seções 4.10 a 4.12 (como operar)

## Sumário

1. [A ideia em um parágrafo](#1-a-ideia-em-um-parágrafo)
2. [Glossário](#2-glossário)
3. [Visão geral da arquitetura](#3-visão-geral-da-arquitetura)
4. [Cada peça, uma a uma](#4-cada-peça-uma-a-uma)
5. [Quem fala com quem (tabela de fluxos)](#5-quem-fala-com-quem)
6. [Por que o Prometheus recebe por push](#6-por-que-o-prometheus-recebe-por-push-em-vez-de-fazer-pull)
7. [Como o Alloy lê a VM](#7-como-o-alloy-lê-a-vm)
8. [Traces, spans e OTLP](#8-traces-spans-e-otlp)
9. [Como o Grafana liga log e trace](#9-como-o-grafana-liga-log-e-trace)
10. [Rede e segurança](#10-rede-e-segurança)
11. [Onde ficam os dados e por quanto tempo](#11-onde-ficam-os-dados-e-por-quanto-tempo)
12. [Mapa dos arquivos do repositório](#12-mapa-dos-arquivos-do-repositório)
13. [Variáveis de ambiente](#13-variáveis-de-ambiente)
14. [Como verificar se está funcionando](#14-como-verificar-se-está-funcionando)
15. [Limitações conhecidas](#15-limitações-conhecidas)
16. [Como acessar o Grafana sem liberar IP](#16-como-acessar-o-grafana-sem-liberar-ip)
17. [Perguntas e respostas para o professor](#17-perguntas-e-respostas-para-o-professor)
18. [Autoavaliação](#18-autoavaliação)

---

## 1. A ideia em um parágrafo

Observabilidade é conseguir descobrir **o que está acontecendo dentro do sistema olhando só para o que ele
emite**, sem precisar entrar na máquina. O sistema emite três tipos de dados, chamados **pilares**:
**métricas** (números ao longo do tempo), **logs** (texto do que aconteceu) e **traces** (o caminho de uma
requisição). Cada pilar tem uma ferramenta que guarda o dado, e o **Grafana** mostra os três numa tela só.

| Pilar | Pergunta que responde | Ferramenta que guarda |
|---|---|---|
| Métricas | Quanto? Com que frequência? | Prometheus |
| Logs | O que aconteceu, em texto? | Loki |
| Traces | Por onde a requisição passou e onde demorou? | Tempo |

Entre as máquinas e esses bancos existe um **coletor**, o **Grafana Alloy**, que lê os dados da VM e os envia.

Exemplo de investigação real: um usuário diz que o login está lento.
1. **Métrica:** o dashboard mostra o tempo de resposta (p95) subindo às 14h.
2. **Log:** no Loki, você filtra o erro desse horário.
3. **Trace:** você clica no `traceId` do log, abre o trace no Tempo e vê que 120 ms dos 180 ms foram numa query SQL.

A métrica avisa que há problema, o log conta o que houve e o trace mostra onde.

## 2. Glossário

| Termo | Significado simples |
|---|---|
| **Telemetria** | Tudo que o sistema emite sobre si mesmo: métricas, logs e traces. |
| **Métrica** | Um número com nome, rótulos e timestamp. Ex.: `http_server_requests` do endpoint X valia 120 às 14:03:30. |
| **Série temporal** | A sequência dos valores de uma métrica ao longo do tempo. O Prometheus é um banco disso. |
| **Rótulo (label)** | Um par chave=valor que identifica a métrica ou o log. Ex.: `vm=app`, `service=inventarium-back`. |
| **Log** | Uma linha de texto escrita pela aplicação. |
| **Trace** | O registro completo de **uma requisição**, em forma de árvore de etapas. |
| **Span** | Uma etapa dentro do trace (controller, service, query SQL), com início e duração. |
| **traceId** | Identificador do trace. É **o mesmo em todos os spans** da mesma requisição (32 caracteres hexadecimais). |
| **spanId** | Identificador de um span. Cada span tem o seu (16 caracteres hexadecimais). |
| **Scrape** | O ato de ler (via HTTP GET) uma página de métricas. |
| **Pull** | Quem coleta vai buscar o dado em quem o produz. |
| **Push** | Quem produz o dado o envia ao destino. |
| **Remote write** | O jeito de o Prometheus receber métricas por push, via HTTP. |
| **Exporter** | Programa ou componente que transforma dados de algum lugar (Linux, Docker) em métricas. |
| **OTLP** | OpenTelemetry Protocol: protocolo padrão para enviar telemetria. Aqui é usado nos traces. |
| **Loopback (`127.0.0.1`)** | Endereço que só enxerga a própria máquina. Quem está fora não consegue acessar. |
| **NSG** | Network Security Group da OCI: o firewall virtual das VMs, gerenciado pelo Terraform. |
| **Volume Docker** | Disco persistente de um container. Sobrevive a reiniciar o container, mas some se o volume for apagado. |

## 3. Visão geral da arquitetura

São duas VMs na OCI. A da aplicação é pública. A de observabilidade é **privada, sem IP público**.

```
VM DA APLICAÇÃO (pública)                         VM DE OBSERVABILIDADE (privada, 10.42.0.74)
┌────────────────────────────────────┐            ┌─────────────────────────────────────┐
│ Nginx ─ front / back / portainer   │            │ Prometheus  :9090  métricas         │
│                                    │            │ Loki        :3100  logs             │
│ Backend Spring                     │  traces    │ Tempo       :4317/:4318  traces     │
│   :8080 público                    │  (push)    │ Grafana     :3000  telas            │
│   :8081 só loopback ──┐            ├───────────►│ SonarQube   :9000  qualidade        │
│   (métricas)          │ pull 30s   │            │                                     │
│                       ▼ local      │  métricas  │                                     │
│ Alloy (coletor) ◄─ servidor        │  + logs    │  Alloy (coletor da própria VM)      │
│   │            ◄─ containers       ├───────────►│                                     │
│   │            ◄─ logs dos         │  (push)    │                                     │
│   │               containers       │            │                                     │
└───┴────────────────────────────────┘            └─────────────────────────────────────┘
                                                                  ▲
                  Pessoa ─► Nginx (grafana.inventariumifpe.me) ───┘  só IPs liberados
```

Decisões de projeto, em ordem de importância:

1. **A VM de observabilidade não tem IP público.** Nada nela é alcançável da internet. O NSG libera suas portas
   apenas para a subnet da aplicação.
2. **Tudo que sai da VM da aplicação é push.** A VM da aplicação inicia as conexões, e a de observabilidade só
   escuta (seção 6).
3. **As métricas do backend ficam em loopback** (`127.0.0.1:8081`), separadas da API pública.
4. **O acesso humano ao Grafana passa pelo Nginx** da VM da aplicação, com lista de IPs liberados.
5. **Cada serviço tem limite de memória.** O SonarQube sozinho derrubaria a VM de 8 GB.

## 4. Cada peça, uma a uma

### 4.1 Backend Spring Boot (a origem dos dados da aplicação)

Produz dois tipos de dado e tem um terceiro que sai por outro caminho:

- **Métricas** pelo Micrometer, expostas em `/actuator/prometheus`. Incluem requisições HTTP por endpoint e
  status, tempo de resposta, JVM (memória, threads, GC) e pool de conexões com o banco. O histograma
  `http.server.requests` está ligado (`management.metrics.distribution.percentiles-histogram`), o que permite
  calcular percentis como o p95.
- **Traces** pelo Micrometer Tracing, exportados por OTLP/HTTP direto ao Tempo.
- **Logs** escritos na saída do container, onde o Alloy os lê. A linha traz `[traceId-spanId]`.

**Porta de gerenciamento separada.** O Actuator roda na **8081**, e não na 8080 da API. Em produção a 8081 é
publicada só em `127.0.0.1` (`docker-compose.production.yml`). Na porta pública o Actuator **não existe**. O Nginx
encaminha apenas o health público; as métricas nunca passam por ele.

**Filtro de ruído.** O `NoiseFilteringSpanPredicate` descarta spans inúteis (como health checks) para não poluir
o Tempo.

Configuração em `backend/src/main/resources/application.properties`:
`management.server.port`, `management.endpoints.web.exposure.include=health,prometheus` e as propriedades
`management.tracing.*` (liga/desliga e endpoint do Tempo).

### 4.2 Grafana Alloy (o coletor)

Agente da Grafana que **coleta, transforma e encaminha** dados. Roda como container **em cada VM**, com a mesma
configuração, e só mudam duas variáveis (`OBS_HOST` e `VM_NAME`). Faz quatro coletas:

| O que coleta | Componente | Fonte |
|---|---|---|
| Métricas do servidor (CPU, memória, disco, rede) | `prometheus.exporter.unix` | arquivos de `/proc` e `/sys` do host |
| Métricas de cada container | `prometheus.exporter.cadvisor` | socket do Docker e cgroups |
| Métricas do backend | `prometheus.scrape "backend"` | HTTP em `127.0.0.1:8081/actuator/prometheus` |
| Logs dos containers | `loki.source.docker` | socket do Docker |

E dois envios: `prometheus.remote_write` (para o Prometheus) e `loki.write` (para o Loki). Cada métrica e log
recebe o rótulo `vm=app` ou `vm=observability`, usado para filtrar nos dashboards.

**Importante:** os **traces não passam pelo Alloy**. O backend os envia direto ao Tempo.

### 4.3 Prometheus (métricas)

Banco de dados de séries temporais. Guarda os números que o Alloy envia.

- Está configurado com `--web.enable-remote-write-receiver`, para **aceitar** push.
- O `prometheus.yml` é quase vazio: só observa a si mesmo. Não há alvos de aplicação, porque os dados chegam
  por push.
- Retenção: **15 dias ou 8 GB**, o que vier primeiro.

### 4.4 Loki (logs)

Banco de logs. É o "Prometheus dos logs". Ele **não indexa o texto inteiro**, só os rótulos (`container`,
`service`, `vm`), o que o deixa leve e barato. Para achar uma palavra, ele lê os blocos filtrados pelos rótulos.

- Consulta em LogQL, por exemplo `{service="inventarium-back"} |= "ERROR"`.
- Modo único (monolítico), disco local, retenção de **7 dias** aplicada pelo compactor (`loki.yml`).

### 4.5 Tempo (traces)

Banco de traces. Recebe por OTLP em duas portas:
- **4318:** HTTP (é a que o backend usa).
- **4317:** gRPC (aberta, mas sem tráfego hoje).

Disco local, retenção de **72 horas**.

### 4.6 Grafana (as telas)

Só **visualiza**. Não guarda telemetria, apenas configurações e usuários. Tudo é **provisionado por arquivos**,
não por clique, então se o container for recriado, voltam sozinhos:
- `datasources.yml`: liga Prometheus, Loki e Tempo.
- `dashboards/backend.json`: dashboard da aplicação.
- `dashboards/infraestrutura.json`: dashboard do servidor e dos containers.

## 5. Quem fala com quem

| Dado | De → Para | Quem inicia | Protocolo e porta |
|---|---|---|---|
| Métricas do backend | Backend → Alloy | **Alloy** (pull) | HTTP GET, `127.0.0.1:8081`, a cada 30 s |
| Métricas do servidor | Linux → Alloy | Alloy (leitura de arquivo) | `/proc`, `/sys` |
| Métricas dos containers | Docker → Alloy | Alloy (leitura) | socket do Docker |
| Logs dos containers | Docker → Alloy | Alloy (leitura) | socket do Docker |
| Métricas | Alloy → Prometheus | **Alloy** (push) | remote write, HTTP, :9090 |
| Logs | Alloy → Loki | **Alloy** (push) | HTTP, :3100 |
| Traces | Backend → Tempo | **Backend** (push) | OTLP/HTTP, :4318 |
| Consultas | Grafana → os três | **Grafana** | HTTP interno (rede do Docker) |
| Acesso humano | Navegador → Grafana | Pessoa | HTTPS via Nginx, IP liberado |

Só **uma** coisa usa rede para um pull: o Alloy lendo o backend na própria VM, em loopback. Todo o resto que
cruza entre as VMs é push, saindo da VM da aplicação.

## 6. Por que o Prometheus recebe por push em vez de fazer pull

Essa é a pergunta mais provável do professor. No modelo clássico, o Prometheus **busca** (pull) as métricas nos
alvos. Aqui é diferente por causa da rede.

**O problema.** O Prometheus está na VM de observabilidade (privada). As métricas do backend estão em
`127.0.0.1:8081` da VM da aplicação. Para o Prometheus fazer pull, ele teria que alcançar `app-vm:8081`. Isso
exigiria:
- abrir a 8081 para a rede, ou
- expor as métricas pelo Nginx.

As métricas revelam detalhes internos do sistema, então nenhum dos dois caminhos é desejável.

**A solução.** O pull continua existindo, mas **localmente**, dentro da VM da aplicação. Depois o Alloy empurra
o resultado:

```
VM da aplicação
┌──────────────────────────────────────────────┐
│ Backend :8081/actuator/prometheus (loopback) │
│        ▲                                     │
│        │ 1) PULL a cada 30s (127.0.0.1)      │
│      Alloy                                   │
│        │ 2) PUSH (remote write)              │
└────────┼─────────────────────────────────────┘
         ▼
   Prometheus :9090  (VM de observabilidade)
```

**Consequências:**
- **A conexão é sempre de dentro para fora.** A VM da aplicação inicia, a de observabilidade só escuta. As regras
  de rede liberam as portas dela apenas para a subnet da aplicação.
- **Nenhuma porta de métricas fica exposta na rede.**
- **Os dashboards não percebem a diferença.** O Grafana consulta o Prometheus do mesmo jeito.
- **Trade-off:** se o Alloy parar, as métricas somem, porque não há outro coletor.

Por isso o `prometheus.yml` é quase vazio, sem alvos de aplicação.

**Frase pronta:**
> "O Prometheus não alcança a VM da aplicação, porque as métricas ficam em loopback e a VM de observabilidade é
> privada. Então um agente local, o Alloy, lê as métricas por pull dentro da própria VM e as envia ao Prometheus
> por remote write. Assim nenhuma porta de métricas fica exposta na rede."

## 7. Como o Alloy lê a VM

Não existe "a VM envia dados ao Alloy". O Alloy é um container que recebe **permissão de leitura** sobre a VM e
**vai buscar** o que precisa. Está tudo em `infra/alloy/docker-compose.alloy.yml`:

| Configuração | O que permite |
|---|---|
| `network_mode: host` | usa a rede da própria VM, então alcança `127.0.0.1:8081` do backend |
| `/proc`, `/sys`, `/` montados em `/host/*` (somente leitura) | ler CPU, memória, disco e rede do servidor |
| `/var/run/docker.sock` | falar com o Docker: listar containers, ler logs e métricas |
| `/var/lib/docker` e `cgroup: host` | o cAdvisor enxergar o consumo de cada container |
| `pid: host` | enxergar os processos da VM |

Todos os volumes são `:ro` (somente leitura): o Alloy não altera nada.

**Como cada dado é obtido:**
- **Servidor:** o exporter `unix` lê os arquivos que o Linux mantém (ex.: `/proc/meminfo`). Não há rede. É
  leitura de arquivo, convertida em métricas a cada 30 s.
- **Containers:** o `cadvisor` consulta o socket do Docker e os cgroups.
- **Logs:** o `discovery.docker` lista os containers; o `discovery.relabel` coloca os rótulos (`container`,
  `service`, `vm`); o `loki.source.docker` abre o stream de logs de cada um pelo socket e lê as linhas conforme
  são escritas.
- **Backend:** único que usa rede, um HTTP GET em `127.0.0.1:8081/actuator/prometheus`.

**Segurança do socket do Docker.** Acesso ao socket equivale a controle total do Docker, ou seja, root na VM.
Esse risco já existe por causa do Portainer. Para limitar: o Alloy monta o socket em leitura, e a interface
dele escuta só em `127.0.0.1:12345`, sem porta externa.

**A mesma configuração nas duas VMs.** O script `infra/scripts/alloy-vm/bootstrap-alloy.sh` instala o Alloy e
grava `OBS_HOST` e `VM_NAME` num `.env` (sem segredos, só IP e nome). O `backend.alloy` só é instalado na VM
`app`; na outra ele fica vazio, para ela não tentar ler um backend que não existe. Na VM de observabilidade o
`OBS_HOST` é `127.0.0.1`, porque o Alloy envia para os serviços da própria máquina.

## 8. Traces, spans e OTLP

### O que é um span

Um **span** é uma etapa dentro de uma requisição, com nome, início e duração. Um **trace** é o conjunto de todos
os spans de uma requisição, em árvore:

```
traceId = 4bf92f3577b34da6a3ce929d0e0e4736      (o mesmo nos quatro)

[A] POST /auth/login ............................ 180 ms   (raiz, sem pai)
   └─ [B] AuthService.login ...................... 170 ms
        ├─ [C] SELECT ... FROM users ............. 120 ms   ← o gargalo
        └─ [D] gerar JWT .......................... 5 ms
```

Cada span carrega:
- **traceId:** igual em todos da requisição.
- **spanId:** único por span.
- **parentSpanId:** o span que o originou. É isso que monta a árvore.
- Nome, início, duração e atributos (método HTTP, status, a query...).

A métrica diz que houve lentidão; o trace diz **em qual etapa**.

### O que é OTLP

**OTLP** (OpenTelemetry Protocol) é o protocolo padrão para transportar telemetria, independente de
fornecedor. O backend gera os traces nesse formato e o Tempo o entende sem conversão. Se um dia trocarmos o
Tempo por outra ferramenta, a aplicação não muda.

### Como o envio funciona

É o **backend que manda** (push). O Tempo nunca faz requisição ao backend, só escuta.

1. Chega uma requisição, por exemplo `POST /auth/login`.
2. O Spring cria o trace e um span para cada etapa.
3. Os spans terminados ficam num buffer em memória.
4. Em lotes, o exportador OTLP envia o buffer ao Tempo.
5. O Tempo grava em disco.

O envio é **assíncrono**: o usuário não espera. Se o Tempo estiver fora do ar, o backend continua funcionando e
só perde os traces daquele período.

### HTTP ou gRPC?

No projeto é **HTTP**: um `POST http://10.42.0.74:4318/v1/traces`, com o corpo em protobuf (formato binário
compacto). A porta 4317 (gRPC) está aberta no Tempo mas sem uso.

### Controles

| Variável | Efeito |
|---|---|
| `TRACING_EXPORT_ENABLED` | liga ou desliga o envio (padrão: desligado) |
| `TRACING_OTLP_ENDPOINT` | endereço do Tempo |
| `TRACING_SAMPLING_PROBABILITY` | fração das requisições gravadas (1.0 = todas) |

## 9. Como o Grafana liga log e trace

Dois recursos fazem a correlação:

- **Log → trace.** No datasource do Loki existe um *derived field* com o regex `\[(\w{32})-\w{16}\]`. O log do
  Spring escreve `[traceId-spanId]`; o Grafana extrai os 32 primeiros caracteres como traceId e transforma em
  link para o trace no Tempo.
- **Trace → log.** No datasource do Tempo, `tracesToLogsV2` aponta para o Loki, e dá para ir do trace aos logs
  daquela requisição.

Todos os logs de uma mesma requisição trazem o **mesmo traceId**; o spanId muda conforme a etapa em que o log foi
escrito.

## 10. Rede e segurança

| Camada | O que faz |
|---|---|
| **NSG da VM de observabilidade** | Libera as portas (9090, 3100, 4317, 4318, 3000, 9000) só para a subnet da aplicação. A VM não tem IP público. |
| **Loopback no backend** | A 8081 só responde dentro da VM da aplicação. |
| **Nginx da VM da aplicação** | Único caminho humano até Grafana e Portainer, com lista de IPs (`allowed_admin_cidrs` no Terraform, `ADMIN_CIDRS` na VM). |
| **Actuator só na porta de gerenciamento** | Na API pública o Actuator não existe. Testes (`ActuatorExposureE2ETest`) garantem isso. |
| **Alloy** | Volumes somente leitura, interface só em loopback, sem porta externa. |
| **Segredos** | `GRAFANA_ADMIN_PASSWORD` e `SONAR_DB_PASSWORD` vêm de um `.env` gerado na VM (permissão 0600), nunca do Git. |

Pontos de atenção: a lista de IPs do SSH fica no Terraform (NSG), e a do Nginx fica na VM. Se o IP muda, as duas
precisam ser ajustadas, e o NSG deve ser alterado pelo Terraform, para não dessincronizar o estado.

## 11. Onde ficam os dados e por quanto tempo

Todos gravam em **volume Docker** na VM de observabilidade, cada um no seu formato:

| Serviço | Volume | O que guarda | Retenção | Limite de memória |
|---|---|---|---|---|
| Prometheus | `prometheus-data` | séries temporais | 15 dias ou 8 GB | 1 GB |
| Loki | `loki-data` | logs | 7 dias | 768 MB |
| Tempo | `tempo-data` | traces | 72 horas | 768 MB |
| Grafana | `grafana-data` | só configuração e usuários | n/a | 768 MB |
| SonarQube | volumes próprios | análise de qualidade | n/a | 3 GB |
| Alloy | `alloy-data` | estado interno do coletor | n/a | 512 MB |

Se a VM for destruída ou os volumes apagados, o **histórico se perde**. Dashboards e datasources voltam, porque
estão no Git. **Não há backup desses volumes.**

## 12. Mapa dos arquivos do repositório

| Arquivo | Papel |
|---|---|
| `backend/src/main/resources/application.properties` | Actuator na 8081, endpoints, tracing OTLP |
| `backend/.../NoiseFilteringSpanPredicate.java` | descarta spans inúteis |
| `backend/.../ActuatorExposureE2ETest.java` | prova que o Actuator não vaza na API pública |
| `docker-compose.production.yml` | publica a 8081 só em loopback; variáveis `TRACING_*` |
| `infra/alloy/config.alloy` | coletas do servidor, containers e logs; envios ao Prometheus e Loki |
| `infra/alloy/backend.alloy` | scrape do backend (só VM da aplicação) |
| `infra/alloy/docker-compose.alloy.yml` | container do Alloy e seus acessos à VM |
| `infra/scripts/alloy-vm/bootstrap-alloy.sh` | instala o Alloy na VM (`app` ou `observability`) |
| `infra/observability/docker-compose.observability.yml` | Prometheus, Loki, Tempo, Grafana, SonarQube |
| `infra/observability/prometheus/prometheus.yml` | quase vazio (recebe por push) |
| `infra/observability/loki/loki.yml` | modo único, retenção de 7 dias |
| `infra/observability/tempo/tempo.yml` | receptores OTLP, retenção de 72 h |
| `infra/observability/grafana/provisioning/` | datasources e dashboards como código |
| `infra/terraform/oci/security.tf` | NSGs e regras de rede |
| `infra/terraform/oci/locals.tf` | IPs privados fixos e lista das portas da observabilidade |
| `infra/nginx/app-vm/sites-tls/21-api.conf` | upstream de gerenciamento e health público |
| `docs/architecture/adr/0007-...md` | decisão arquitetural registrada |

## 13. Variáveis de ambiente

**Na stack de produção (Portainer), para os traces:**

| Variável | Valor |
|---|---|
| `TRACING_EXPORT_ENABLED` | `true` |
| `TRACING_OTLP_ENDPOINT` | `http://10.42.0.74:4318/v1/traces` |
| `TRACING_SAMPLING_PROBABILITY` | `1.0` (ou menos, para reduzir volume) |

Depois de alterar as variáveis da stack é preciso salvar e fazer *Pull and redeploy*.

**No Alloy (gravadas pelo `bootstrap-alloy.sh`):** `OBS_HOST` (IP da VM de observabilidade) e `VM_NAME` (`app` ou
`observability`). `BACKEND_METRICS_ADDR` tem padrão `127.0.0.1:8081`.

**No Grafana:** `GRAFANA_ADMIN_PASSWORD`, vindo do `.env` da VM de observabilidade.

## 14. Como verificar se está funcionando

Na VM da aplicação:

```bash
# o backend expõe métricas na porta de gerenciamento (loopback)
curl -s http://127.0.0.1:8081/actuator/prometheus | head

# o Alloy está no ar e sem erros
sudo docker logs alloy --tail 30
```

No Grafana (Explore):
- **Prometheus:** consulte `up` e veja se há séries com `vm="app"`.
- **Loki:** `{service="inventarium-back"}` deve trazer logs recentes.
- **Tempo:** busque pelo traceId copiado de um log.

Se algo não aparece: confira se o NSG libera a porta, se `TRACING_EXPORT_ENABLED=true` está na stack e se a stack
foi redeployada.

Os scripts de deploy (executados do seu computador, com Git Bash) são `deploy-nginx.sh`, `deploy-alloy.sh` e
`deploy-observability.sh`. A ordem importa: o Nginx deve ser atualizado antes do backend, porque ele passa a
encaminhar o health para a 8081.

## 15. Limitações conhecidas

- **Alloy é ponto único de coleta.** Se parar, as métricas e logs deixam de chegar (não há outro coletor).
- **Sem logs do sistema da VM.** O Alloy lê só logs de containers. Não há coleta de `/var/log`, journald, SSH ou
  do Nginx do host.
- **Sem backup dos volumes** de observabilidade. O histórico se perde se a VM for destruída.
- **Retenção curta** (72 h de traces, 7 dias de logs, 15 dias de métricas). É o suficiente para investigar
  problemas recentes, não para auditoria.
- **Socket do Docker montado no Alloy**, com o risco explicado na seção 7.
- **Acesso ao Grafana depende de IP liberado**, o que incomoda com IP dinâmico (seção 16).

## 16. Como acessar o Grafana sem liberar IP

Hoje existem duas listas de IP: a do NSG (SSH, via Terraform) e a do Nginx (admin, na VM). Alternativas, em ordem
de recomendação:

1. **Tailscale (VPN mesh).** Instalado na VM e no seu computador, com login Google/GitHub e plano gratuito. A VM
   ganha um IP privado (`100.x.x.x`). Você fecha o SSH da internet e libera no Nginx só a faixa
   `100.64.0.0/10`. O IP público do seu computador deixa de importar. É a melhor opção para SSH e Portainer.
2. **Cloudflare Tunnel + Cloudflare Access.** Um container `cloudflared` abre uma conexão de saída, sem porta de
   entrada, e o Access pede login (ex.: Google do IFPE) antes de mostrar o Grafana. Exige gerenciar o DNS na
   Cloudflare.
3. **Login Google dentro do Grafana.** O Grafana tem OAuth Google nativo (`allowed_domains = ifpe.edu.br`). Deixa
   o Grafana público com HTTPS. O risco é expor o Grafana a vulnerabilidades dele. **Manter o Portainer fechado**,
   porque ele controla o Docker da VM.
4. **OCI Bastion.** Sessões SSH ou túnel temporárias, sem SSH aberto, mas criadas por CLI ou console (manual).
5. **Reduzir a dor sem mudar a arquitetura.** Um script que descobre o IP atual e roda o `terraform apply` com ele.

Qualquer uma dessas mexe em Terraform, Nginx e documentação, e exigiria ADR e testes.

## 17. Perguntas e respostas para o professor

**O que é observabilidade e por que precisamos dela?**
É descobrir o que acontece dentro do sistema pelo que ele emite (métricas, logs e traces), sem entrar na
máquina. Sem isso, um problema em produção só é descoberto quando o usuário reclama, e a causa exige tentativa e
erro.

**Qual a diferença entre métricas, logs e traces?**
Métricas são números ao longo do tempo (quanto). Logs são texto do que aconteceu. Traces são o caminho de uma
requisição e o tempo de cada etapa. A métrica avisa, o log conta, o trace mostra onde.

**O que é o Prometheus?**
Um banco de séries temporais. Guarda métricas com rótulos e timestamp. No projeto retém 15 dias ou 8 GB.

**O que é o Loki?**
Um banco de logs que indexa só os rótulos, não o texto. Isso o torna leve. Retém 7 dias.

**O que é o Tempo?**
Um banco de traces. Recebe por OTLP e retém 72 horas.

**O que é o Grafana?**
A camada de visualização. Não guarda telemetria. Lê dos três bancos e correlaciona log e trace pelo traceId.

**O que é o Alloy?**
O agente coletor da Grafana. Lê o servidor, os containers, os logs e as métricas do backend, e os envia ao
Prometheus e ao Loki.

**Por que o Prometheus recebe por push se o normal é pull?**
Porque a VM de observabilidade é privada e as métricas do backend ficam em loopback. Para o Prometheus puxar,
teríamos de abrir uma porta ou expor as métricas pelo Nginx. Em vez disso, o Alloy lê as métricas localmente
(pull) e as envia (push) por remote write. A conexão sempre parte da VM da aplicação.

**A porta 8081 é a principal fonte de dados?**
Não. Ela entrega só as métricas da aplicação (requisições, JVM, pool de conexões). Servidor, containers e logs
vêm de arquivos do host e do socket do Docker, sem rede.

**Os traces também passam pelo Alloy?**
Não. O backend os envia direto ao Tempo, por OTLP/HTTP na porta 4318.

**Quem inicia a comunicação dos traces: o backend ou o Tempo?**
O backend. É push. O Tempo só recebe.

**O que é OTLP e por que usar?**
O protocolo padrão do OpenTelemetry para transportar telemetria. Como é padrão, o backend não depende de uma
ferramenta específica: trocar o Tempo por outro destino não exige mudar a aplicação.

**Os traceIds de uma mesma requisição são iguais?**
Sim. O traceId é o mesmo em todos os spans e logs da requisição. O spanId é único por span.

**Como o Alloy acessa os dados da VM?**
Rodando em `network_mode: host` e com `/proc`, `/sys`, `/` e o socket do Docker montados em somente leitura.
Ele busca os dados; a VM não os envia.

**Há algum risco em montar o socket do Docker?**
Sim: equivale a controle do Docker, ou root na VM. Já existia esse risco com o Portainer. Mitigação: leitura
apenas e interface do Alloy só em loopback.

**Como a VM de observabilidade fica protegida?**
Não tem IP público. O NSG libera as portas só para a subnet da aplicação. O acesso humano ao Grafana passa pelo
Nginx, com lista de IPs.

**Os dados são persistentes? E se a VM cair?**
Ficam em volumes Docker, então sobrevivem a reinício de container. Se a VM ou os volumes forem destruídos,
perde-se o histórico (sem backup). Dashboards e datasources voltam, porque estão no Git.

**Qual a limitação mais importante?**
O Alloy é ponto único de coleta, não há backup do histórico e os logs do sistema da VM (fora dos containers) não
são coletados.

**O que fariam como próximo passo?**
Resolver o acesso ao Grafana sem lista de IP (Tailscale ou Cloudflare Access), coletar logs do Nginx do host e
avaliar o WAF da OCI (issue #259).

## 18. Autoavaliação

Responda sem olhar o guia. Se travar, volte à seção indicada.

1. Cite os três pilares e a ferramenta de cada um. (seção 1)
2. Quem inicia cada comunicação da tabela da seção 5? Aponte o único pull.
3. Explique por que o Prometheus não faz pull neste projeto. (seção 6)
4. De onde o Alloy tira cada um dos quatro tipos de dado? (seção 7)
5. Os traces passam pelo Alloy? Por quê? (seção 8)
6. O que é um span e o que muda entre spans da mesma requisição? (seção 8)
7. Como o Grafana vai de um log para o trace correspondente? (seção 9)
8. Quais camadas impedem que alguém da internet acesse o Prometheus? (seção 10)
9. O que se perde se a VM de observabilidade for destruída? (seção 11)
10. Cite duas alternativas para acessar o Grafana sem lista de IP. (seção 16)
