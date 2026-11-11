# ADR 0009 - Load Balancer e WAF da OCI na frente da aplicação

## Status

Accepted. A implantação é feita em três etapas (PR1: Nginx e certificado; PR2: Terraform do LB e do WAF em modo de detecção; PR3: fechar o acesso direto e passar o WAF para bloqueio). Este ADR é atualizado a cada etapa.

## Contexto

A VM da aplicação recebe 80 e 443 direto da internet e o Nginx termina o TLS com um certificado Let's Encrypt. Antes do Nginx não há nenhum filtro de ataques à aplicação (SQL injection, XSS, path traversal, varreduras). O ADR 0006 descartou um Load Balancer da OCI por custo e por perder o controle dos cabeçalhos; o WAF da OCI, porém, só funciona ligado a um Load Balancer de camada 7, que passa a ser o pré-requisito para ter proteção na camada de aplicação com a franquia gratuita do WAF (1 instância e 10 milhões de requisições por mês, a confirmar na calculadora).

Restrições: projeto acadêmico com custo baixo; uma VM só; segredos fora do Git e do Terraform state; o login Google, a API (CORS), a importação de CSV, o deploy por webhook do Portainer e o smoke test do CI não podem quebrar.

## Decisao

1. **Load Balancer flexível público de camada 7, em subnet própria**, com IP público reservado e NSG próprio. O NSG da VM passa a aceitar 80, 9080 e 9081 **somente do NSG do LB**, para ninguém contornar o WAF acessando a VM direto.
2. **O TLS termina no LB**, com o certificado Let's Encrypt de seis nomes (`@`, `www`, `api`, `portainer`, `sonar`, `grafana`). O WAF só consegue inspecionar a requisição porque ela chega ao LB já decifrada.
3. **HTTP simples entre o LB e o Nginx**, na porta 9080 da VM, restrita ao NSG do LB dentro da VCN. O Nginx continua escutando na 443 (rollback para o modo direto). O esquema que o backend enxerga (`X-Forwarded-Proto: https`) vem da **porta** (`map $server_port`), e não de um cabeçalho que o cliente possa forjar.
4. **Health check na porta 9081** (`/healthz`, sem depender de Host, SNI nem de upstream), aceito só da subnet do LB.
5. **IP real do cliente:** o LB acrescenta o cliente ao `X-Forwarded-For`. O Nginx só confia nesse cabeçalho quando a conexão vem de `LB_CIDR` (chave opcional do `edge.env`, gerada em `generated/real-ip.conf`) e usa `real_ip_recursive on`. Assim o rate limit por IP, a allowlist do Portainer e o log continuam enxergando o cliente, e um `X-Forwarded-For` forjado por quem chega direto na 443 é ignorado. Sem `LB_CIDR` nada muda.
6. **Renovação automática do certificado no LB.** O certbot continua na VM (HTTP-01 pela porta 80 do LB). `push-lb-certificate.sh`, chamado depois de cada renovação, compara o hash do certificado e, se mudou, usa a CLI da OCI em container, com **instance principal** e política de menor privilégio, para criar o certificado novo no LB, trocar o do listener HTTPS e apagar os antigos. A **chave privada nunca passa pelo Terraform nem pelo state**: o Terraform cria o listener HTTPS só depois do primeiro certificado existir e ignora mudanças no nome do certificado do listener.
7. **WAF em modo de detecção primeiro.** A política tem as regras OWASP (SQL injection, XSS, path traversal, RCE, protocolo e scanners) com a ação que só registra; depois de alguns dias revisando os logs e ajustando falsos positivos, as regras passam para bloqueio. O corpo é inspecionado só até o limite do WAF (8 KB) e o restante não é barrado, para não bloquear o CSV e os envios ao Sonar. A resposta de bloqueio leva o cabeçalho CORS estático do front.
8. **Portas e arquivos gerados.** `install-nginx-config.sh` rotula as portas 9080 e 9081 como `http_port_t` no SELinux, libera-as no firewalld só para `LB_CIDR` (sem `--reload`, que derrubaria as regras do Docker) e, depois do reload, confere que o Nginx escuta nelas, restaurando a configuração anterior se não escutar.

## Consequencias

- O tráfego passa a ser filtrado na camada 7 antes de chegar à VM, e o certificado e o TLS ficam no LB.
- O acesso direto à VM deixa de existir depois da etapa 3, o que torna o NSG a peça que garante que o WAF não é contornado.
- O trecho entre o LB e o Nginx é HTTP simples. A segurança dele depende de o NSG aceitar a 9080 e a 9081 somente do NSG do LB, e o WAF não substitui a validação do backend: inspeciona só os primeiros 8 KB do corpo e, em detecção, apenas registra.
- Custo novo: o LB é cobrado por hora e por banda acima do mínimo (a confirmar); o WAF fica na franquia gratuita.
- O DNS no Namecheap passa a apontar para o IP do LB, e o cutover precisa ser planejado e reversível (a VM mantém o IP reservado e o SSH).
- A renovação do certificado ganha uma dependência (API da OCI); uma falha repete a cada 12 horas e deve ser monitorada.

## Alternativas Consideradas

- **Cloudflare (plano gratuito):** não exige LB e dá proteção contra DDoS, mas o conjunto de regras gratuito é restrito e o OWASP Core Ruleset é pago; exige mover o DNS para a Cloudflare.
- **ModSecurity com OWASP CRS no próprio Nginx:** gratuito e com as regras completas, mas exige compilar e manter o módulo para o Nginx do Oracle Linux 8 e consome a mesma VM.
- **WAF Edge da OCI:** é o modelo anterior do serviço e obrigaria a VM a aceitar só os nós da Oracle, uma lista grande e mutável.
- **Reencriptar do LB para o Nginx:443:** daria criptografia ponta a ponta, mas o LB provavelmente não valida o nome do certificado do Nginx, então o ganho de segurança seria pequeno.
- **OCI Certificates (serviço) para a rotação:** troca sozinho as versões, mas exige chave em Vault/KMS e mais recursos; o hook por CLI é mais simples para este porte.

## Riscos

- **Falsos positivos do WAF:** mitigados pela detecção prévia, por exclusões para o webhook do Portainer, o `actuator/health`, o token Google e o campo de senha, e pela revisão dos logs.
- **Certificado vencendo no LB** se o hook falhar: o timer repete a cada 12 horas; conferir a validade no smoke test.
- **LB em subnet única:** sem redundância entre domínios de disponibilidade (aceito para o porte).
- **`X-Forwarded-For` forjado:** tratado por `set_real_ip_from` restrito ao `LB_CIDR` e por `real_ip_recursive`. Se `LB_CIDR` estiver errado ou largo demais, a confiança vaza.
- **Contornar o WAF pelo IP da VM:** tratado pelo NSG; conferir depois do cutover que a VM não responde fora do LB.
- **A imagem da CLI da OCI** só tem a tag `latest` documentada: fixar um digest em `OCI_CLI_IMAGE` assim que possível.
- **Mudança de IP e DNS:** reduzir o TTL dos registros antes do cutover e manter o rollback para o IP da VM.

## Referencias

- Issue #259
- `infra/nginx/app-vm/` (`sites-tls/15-lb-interno.conf`, `conf.d/00-hardening.conf`, `generated.example/`), `infra/scripts/app-vm/` (`install-nginx-config.sh`, `push-lb-certificate.sh`, `lb-rotate-certificate.py`)
- `docs/wiki/Deploy-em-Producao-OCI.md` (seção 4.14) e `docs/wiki/Modelagem-de-Ameacas.md` (I-9 e I-18)

## Decisões Relacionadas

- ADR 0006: descartou o Load Balancer; esta decisão o retoma pelo WAF e o complementa.
- ADR 0008: Grafana público com login, que também passa a ficar atrás do WAF.
