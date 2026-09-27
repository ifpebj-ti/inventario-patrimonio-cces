# Modelagem de Ameaças

Está pagina documenta a modelagem de ameaças STRIDE do Inventarium e referencia o arquivo importavel no OWASP Threat Dragon.

O modelo atual cobre a aplicação Web, a API Spring Boot, o PostgreSQL, o login com Google e o envio de planilhas por e-mail. O aplicativo mobile foi tratado como fora do escopo deste modelo porque está descontinuado no momento. A infraestrutura de produção ainda deve receber um diagrama próprio quando WAF, VMs, backups, observabilidade e rede estiverem definidos.

## Arquivo do Threat Dragon

Arquivo versionado:

[inventarium-threat-model-stride.json](./assets/inventarium-threat-model-stride.json)

Para abrir:

1. Acesse o OWASP Threat Dragon.
2. Escolha a opcao de abrir/importar modelo existente.
3. Selecione o arquivo `docs/wiki/assets/inventarium-threat-model-stride.json`.
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

- infraestrutura final de produção;
- pipeline CI/CD e supply chain de imagens;
- politicas de backup e restauração;
- operação e observabilidade;
- aplicativo mobile descontinuado.

## Resumo dos riscos

O modelo possui 21 ameaças registradas:

- 4 críticas abertas;
- 7 altas abertas;
- 8 medias abertas;
- 2 altas mitigadas.

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
| 15 | Medium | Open | Tampering | Backend API / Spring Boot | CORS permissivo para origens não confiáveis |
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

## Priorizacao sugerida

1. Corrigir autorização por ownership em todas as leituras, escritas, exportacoes, PDFs e envio por e-mail.
2. Adicionar testes negativos de BOLA/IDOR entre dois usuários diferentes.
3. Proteger exportacoes e envio por e-mail com ownership, auditoria e política de destinatarios.
4. Endurecer o processamento de XLSX com limites de tamanho, linhas, colunas, timeout e defesa contra zip bomb.
5. Reduzir impacto de XSS com CSP e considerar migracao do token para cookie `Secure`, `HttpOnly` e `SameSite`.
6. Implementar auditoria append-only para operações críticas.
7. Revisar CORS, credenciais de banco, TLS entre serviços e política de backup.

## Critérios de manutenção

Atualize o modelo quando houver:

- novo endpoint autenticado;
- novo fluxo de arquivo, PDF, planilha ou e-mail;
- mudanca no login, token ou armazenamento de sessão;
- alteracao no modelo de permissões;
- mudanca de infraestrutura, rede, banco, backup ou observabilidade;
- correcao de uma ameaca, alterando seu status para `Mitigated` ou `Closed`.

Cada PR que altera superficie de ataque deve revisar está pagina e o JSON do Threat Dragon.
