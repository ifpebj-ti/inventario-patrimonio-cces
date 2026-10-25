# Modelagem de Ameaças

Está página documenta a modelagem de ameaças STRIDE do Inventarium e referencia os arquivos importáveis no OWASP Threat Dragon.

O modelo atual cobre a aplicação Web, a API Spring Boot, o PostgreSQL, o login com Google e o envio de planilhas por e-mail. O aplicativo mobile foi tratado como fora do escopo deste modelo porque está descontinuado no momento. A infraestrutura de produção na OCI (Nginx, TLS, Portainer, backups e observabilidade) já está definida e suas ameaças estão tabeladas na seção "Ameaças da infraestrutura de produção (OCI)", mais abaixo; elas ainda precisam ser desenhadas em um segundo diagrama do Threat Dragon.

## Arquivos do Threat Dragon e versionamento

O artefato importável é versionado por data no nome do arquivo, no formato `inventarium-threat-model-stride-AAAA-MM-DD.json`. A data identifica a revisão do modelo e permite consultar facilmente a evolução na wiki; o histórico de commits do Git preserva também as alterações dentro de cada revisão.

Não se deve sobrescrever uma revisão já publicada. Ao revisar a superfície de ataque, crie uma cópia com a data da nova revisão, atualize este índice para apontar a mais recente e mantenha as versões anteriores como evidência histórica.

| Revisão | Arquivo | Situação |
| --- | --- | --- |
| 2026-09-14 | [inventarium-threat-model-stride-2026-09-14.json](./assets/inventarium-threat-model-stride-2026-09-14.json) | Modelo inicial versionado; mantido como evidência histórica. |
| 2026-10-11 | [inventarium-threat-model-stride-2026-10-11.json](./assets/inventarium-threat-model-stride-2026-10-11.json) | Revisão atual: ameaça 15 (CORS) passa a `Mitigated`. As ameaças de infraestrutura estão na seção abaixo e ainda não fazem parte do diagrama. |

Para abrir:

1. Acesse o OWASP Threat Dragon.
2. Escolha a opcao de abrir/importar modelo existente.
3. Selecione o arquivo da revisão desejada em `docs/wiki/assets/`.
4. Revise o diagrama `Inventarium - Application Data Flow`.
5. Use a lista de ameaças do próprio Threat Dragon para atualizar status, severidade, mitigacoes e responsáveis.

## Escopo

Elementos modelados:

- usuário institucional no navegador;
- frontend Web em Next.js;
- backend API em Spring Boot;
- PostgreSQL;
- Google Identity Services;
- Gmail/SMTP;
- fluxos HTTPS, Bearer JWT, uploads XLSX, PDF/XLSX e JDBC.

Fora do escopo deste diagrama:

- diagrama da infraestrutura de produção no Threat Dragon (as ameaças estão tabeladas abaixo);
- pipeline CI/CD e supply chain de imagens;
- politicas de backup e restauração;
- operação e observabilidade;
- aplicativo mobile descontinuado.

## Resumo dos riscos

O modelo possui 21 ameaças registradas:

- 4 críticas abertas;
- 7 altas abertas;
- 7 medias abertas;
- 2 altas mitigadas;
- 1 media mitigada (CORS).

As ameaças críticas abertas estao concentradas em autorização por objeto e exposicao de dados:

- alteracao de itens ou inventários sem validação consistente de ownership;
- consulta, PDF ou exportação de inventário de outro usuário;
- elevacao de privilegio por BOLA/IDOR;
- exfiltracao de inventário para e-mail arbitrario.

## Ameaças registradas

| ID | Severidade | Status | STRIDE | Ponto | Ameaca |
| --- | --- | --- | --- | --- | --- |
| 1 | High | Mitigated | Spoofing | Google Identity / Services | Token de identidade Google falsificado ou adulterado |
| 2 | High | Open | Information disclosure | Frontend Web / Next.js / Browser | Exposicao do JWT da aplicação a scripts do navegador |
| 3 | High | Open | Spoofing | Backend API / Spring Boot | Vinculacao de conta apenas pelo e-mail do Google |
| 4 | Critical | Open | Tampering | Backend API / Spring Boot | Alteracao de itens ou inventários sem validação de ownership |
| 5 | Critical | Open | Information disclosure | Backend API / Spring Boot | Consulta, PDF ou exportação de inventário de outro usuário |
| 6 | High | Open | Repudiation | Backend API / Spring Boot | Ausencia de trilha de auditoria para operações críticas |
| 7 | High | Open | Denial of service | Backend API / Spring Boot | Exaustao de recursos por importação e geração de arquivos |
| 8 | Critical | Open | Elevation of privilege | Backend API / Spring Boot | Elevacao de privilegio por BOLA/IDOR |
| 9 | High | Open | Information disclosure | PostgreSQL | Exposicao do volume ou backup do PostgreSQL |
| 10 | High | Open | Tampering | PostgreSQL | Alteracao direta do banco apos comprometimento de credenciais |
| 11 | Critical | Open | Information disclosure | Planilha XLSX + mensagem | Exfiltracao de inventário para endereco de e-mail arbitrario |
| 12 | Medium | Open | Denial of service | Planilha XLSX + mensagem | Abuso do servico de e-mail por requisições repetidas |
| 13 | High | Mitigated | Tampering | REST API + Bearer JWT + uploads | Interceptacao ou alteracao do trafego cliente-API |
| 14 | Medium | Open | Spoofing | Backend API / Spring Boot | JWT da aplicação sem revogacao ou rotacao de sessão |
| 15 | Medium | Mitigated | Tampering | Backend API / Spring Boot | CORS permissivo para origens não confiáveis |
| 16 | Medium | Open | Information disclosure | Backend API / Spring Boot | Enumeracao de IDs por respostas e tempos diferentes |
| 17 | High | Open | Denial of service | REST API + Bearer JWT + uploads | Upload XLSX malformado ou zip bomb contra o parser |
| 18 | Medium | Open | Information disclosure | JWT + dados + PDF/XLSX | Formula injection em planilhas exportadas |
| 19 | Medium | Open | Information disclosure | Consultas e persistência | Trafego JDBC sem criptografia explicita no modelo |
| 20 | Medium | Open | Elevation of privilege | PostgreSQL | Privilegio excessivo da credencial da aplicação no banco |
| 21 | Medium | Open | Tampering | Frontend Web / Next.js / Browser | Ausencia de baseline CSP para reduzir impacto de XSS |

## Achados adicionados nesta revisão

Os itens 14 a 21 foram adicionados ao JSON versionado a partir da leitura do código e dos fluxos ja modelados:

- token da aplicação sem revogacao server-side;
- CORS permissivo em controllers;
- possibilidade de enumeracao de IDs por respostas diferentes;
- risco de zip bomb ou arquivo XLSX malformado no upload;
- formula injection em planilhas exportadas;
- trafego JDBC sem criptografia explicita no modelo;
- privilegio excessivo da credencial de runtime no banco;
- falta de baseline CSP para reduzir impacto de XSS.

## Ameaças da infraestrutura de produção (OCI)

Revisão de 2026-10-11, referente ao ADR 0006 (veja [Architecture Decision Records](./Architecture-Decision-Records)) e ao [runbook de deploy](./Deploy-em-Produção-OCI). Estas ameaças ainda não estão no JSON do Threat Dragon; entrarão em um segundo diagrama da infraestrutura. Cada linha traz o controle já implementado e o risco que continua aberto.

| ID | Ameaça | Controle implementado | Risco residual / ação |
| --- | --- | --- | --- |
| I-1 | Quem alcança a UI do Portainer controla o Docker (e, na prática, a VM), pois ele monta `docker.sock` | Portainer escuta só em `127.0.0.1:9443`; o NSG não expõe a 9443; a UI só abre em `portainer.<domínio>` para os IPs de `allowed_admin_cidrs` (Nginx); senha inicial aleatória; versão fixa | Sem MFA no Portainer CE; IP residencial dinâmico pode exigir atualização da allowlist. Avaliar VPN/túnel se o IP de admin não for estável |
| I-2 | Webhook de redeploy exposto à internet | Só `POST` no caminho `/api/stacks/webhooks/<uuid>`, com rate limit; o UUID funciona como segredo; todo o resto do Portainer exige allowlist | Quem obtiver a URL dispara um redeploy (puxa imagens do GHCR da organização, não código do atacante). Se vazar, recriar o webhook no Portainer e atualizar o secret. A URL fica em um secret de repositório do GitHub (mascarada nos logs do job `deploy-production`) |
| I-3 | Interceptação, downgrade ou conteúdo misto | Redirecionamento 80→443, TLS 1.2/1.3, sem tickets de sessão, HSTS com `includeSubDomains` | HSTS começa com `max-age` curto e deve subir para 1 ano após a validação; `preload` não é usado |
| I-4 | Certificado expira sem ninguém perceber | Renovação automática 2x ao dia (`inventarium-certbot-renew.timer`) e e-mail de aviso do Let's Encrypt | Falha de renovação é silenciosa até gerar alerta na observabilidade. Acompanhar `systemctl list-timers` |
| I-5 | SonarQube público (o scanner do GitHub precisa alcançá-lo) | Login obrigatório (`sonar.forceAuthentication`), versão fixa, rate limit no Nginx | Superfície exposta a vulnerabilidades do Sonar; manter a versão atualizada |
| I-6 | Vazamento de segredos de runtime | Segredos nunca passam pelo Terraform nem pelo user-data: o `.env` vai por `scp`, é lido pelo script e apagado (`shred`); as variáveis ficam na stack do Portainer | Quem acessa o Portainer lê os segredos da stack; o volume `portainer_data` deve ser tratado como sensível |
| I-7 | Perda ou vazamento de backup do Postgres | Dump diário em bucket privado do Object Storage (criptografado em repouso), enviado por instance principal com permissão restrita a esse bucket, retenção de 30 dias; cópias locais `0600` por 7 dias | Sem criptografia adicional no lado do cliente; o restore drill precisa ser executado e registrado |
| I-8 | Supply chain: imagem comprometida chega à produção via webhook | Imagens só são publicadas após os scans Trivy do release; imagens do GHCR são da própria organização | Tag `latest` é mutável e só é movida depois dos scans do Trivy; rollback por `IMAGE_TAG` fixo (`vX.Y.Z`). Migrações do Liquibase não são revertidas pelo rollback de imagem |
| I-9 | IP de cliente forjado para burlar o limite de login por IP | O limite por IP é do Nginx (zona `auth`, só no `/auth/login`), que usa o IP real da conexão (`$remote_addr`) e sobrescreve `X-Forwarded-For`; o limite por usuário, no backend, não depende de IP | Se um CDN/WAF for colocado à frente, o `$remote_addr` do Nginx passa a ser o do CDN e a zona precisa ser revista (ex.: `real_ip`) |
| I-10 | Exposição de endpoints internos (Actuator) | Só `/actuator/health` (e probes) é encaminhado pela API; o resto devolve 404 | — |
| I-11 | Acesso remoto indevido à VM | SSH só por chave e só para `allowed_ssh_cidrs`; VM de observabilidade sem IP público | Manter a allowlist atualizada |
| I-12 | XSS no front por política de conteúdo frouxa | CSP publicada em modo `Report-Only` na primeira semana, depois imposta; `frame-ancestors 'none'`, `object-src 'none'`, `base-uri 'self'` | A CSP mantém `'unsafe-inline'` em scripts por causa do Next.js (relacionado à ameaça 21); nonce fica como evolução |
| I-13 | Telemetria sem autenticação (Loki, Tempo e Prometheus) alcançável por quem tomar a VM da aplicação | NSG libera as portas só para a subnet da aplicação; a VM de observabilidade não tem IP público; Grafana só para IPs de admin; SonarQube com login obrigatório | Um processo comprometido na VM da aplicação pode ler ou escrever telemetria. Não registrar dados sensíveis em logs e traces; avaliar autenticação nos receptores |
| I-14 | Esgotamento de memória ou disco da VM de observabilidade | Limite de memória em todos os serviços (testado no CI) e retenção curta (15 dias métricas, 7 dias logs, 3 dias traces) | Dados sem backup; o disco precisa ser acompanhado e a retenção ajustada |

## Priorizacao sugerida

1. Corrigir autorização por ownership em todas as leituras, escritas, exportacoes, PDFs e envio por e-mail.
2. Adicionar testes negativos de BOLA/IDOR entre dois usuários diferentes.
3. Proteger exportacoes e envio por e-mail com ownership, auditoria e política de destinatarios.
4. Endurecer o processamento de XLSX com limites de tamanho, linhas, colunas, timeout e defesa contra zip bomb.
5. Reduzir impacto de XSS com CSP e considerar migracao do token para cookie `Secure`, `HttpOnly` e `SameSite`.
6. Implementar auditoria append-only para operações críticas.
7. Revisar credenciais de banco, TLS entre serviços e política de backup. O CORS foi tratado: a API só aceita as origens de `CORS_ALLOWED_ORIGINS` (ameaça 15), sem credenciais, e recusa `*` na inicialização.

## Critérios de manutenção

Atualize o modelo quando houver:

- novo endpoint autenticado;
- novo fluxo de arquivo, PDF, planilha ou e-mail;
- mudanca no login, token ou armazenamento de sessão;
- alteracao no modelo de permissões;
- mudanca de infraestrutura, rede, banco, backup ou observabilidade;
- correcao de uma ameaca, alterando seu status para `Mitigated` ou `Closed`.

Cada PR que altera a superfície de ataque deve revisar esta página e gerar uma nova versão datada do JSON do Threat Dragon, sem remover as revisões anteriores.
