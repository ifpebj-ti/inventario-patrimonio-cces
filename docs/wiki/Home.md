# Inventarium

Bem-vindo a Wiki do projeto **Inventarium**.

O Inventarium e uma plataforma academica desenvolvida para apoiar o processo de tombamento e gerenciamento dos patrimonios do IFPE. A solucao combina uma aplicação web de gerenciamento e uma API backend responsável pelas regras de negocio, autenticação via Google e persistência dos dados.

O projeto foi desenvolvido inicialmente para atender a demanda de Marcia Bandeira no contexto da disciplina de Engenharia de Software e, a partir dessa base, busca evoluir para uma solucao capaz de abranger o processo de gestão patrimonial em todo o IFPE.

---

## Visão Geral

O processo de tombamento patrimonial exige organização, rastreabilidade e facilidade de consulta. O Inventarium busca reduzir atividades manuais e centralizar informações sobre bens patrimoniais, permitindo que usuários autorizados registrem, consultem e acompanhem itens pela aplicação web.

Entre as capacidades previstas e implementadas no ecossistema do projeto estao:

- gerenciamento de itens patrimoniais;
- apoio ao tombamento de bens do IFPE;
- identificação de itens por código de barras e QR Code;
- interface web para administração e acompanhamento;
- backend com API REST, autenticação via Google e integração com banco de dados;
- geração de materiais auxiliares, como etiquetas, PDFs ou planilhas, conforme suporte da API.

---

## Objetivo

O objetivo do Inventarium e oferecer uma ferramenta integrada para tornar o processo de tombamento e controle patrimonial mais eficiente, padronizado e acessivel para os setores envolvidos no IFPE.

---

## Publico-Alvo

A documentação e o sistema são voltados para:

- servidores e equipes responsáveis pelo patrimônio institucional;
- usuários que realizam levantamento, consulta ou atualização de bens;
- equipe de desenvolvimento e manutenção do projeto;
- docentes, orientadores e avaliadores vinculados ao contexto academico do projeto.

---

## Aplicações do Projeto

| Aplicação | Descrição | Tecnologia principal |
| --- | --- | --- |
| Backend | API responsável por regras de negocio, validação do login com Google, emissão do JWT da aplicação, persistência e geração de artefatos auxiliares. | Java, Spring Boot, Gradle, PostgreSQL |
| Frontend | Aplicação web para login com Google, gerenciamento e acompanhamento dos dados patrimoniais. | Next.js, React, TypeScript |

---

## Documentação

Está Wiki sera evoluida de forma incremental. A fonte oficial dos arquivos está no repositório principal, dentro de `docs/wiki`, e a publicação na Wiki do GitHub e feita automaticamente por GitHub Actions.

Documentos disponiveis:

1. [Mapa da Documentação](./Mapa-da-Documentação)
2. [Documento de Visão](./Documento-de-Visão)
3. [Análise de Concorrência](./Análise-de-Concorrência)
4. [Documento de Modelagem de Dados](./Documento-de-Modelagem-de-Dados)
5. [Requisitos do Sistema](./Requisitos-do-Sistema)
6. [Architecture Decision Records](./Architecture-Decision-Records)
7. [Guia de Execução, Configuração e Operação](./Guia-de-Execução-Configuração-e-Operação)
8. [Guia de Boas Praticas de Desenvolvimento Seguro](./Guia-de-Boas-Praticas-de-Desenvolvimento-Seguro)
9. [Esteira de CI/CD](./Esteira-de-CI-CD)
10. [Modelagem de Ameaças](./Modelagem-de-Ameaças)
11. [C4 - Contexto](./C4-Contexto)
12. [C4 - Containers](./C4-Containers)
13. [C4 - Componentes](./C4-Componentes)

Os documentos pendentes ou parcialmente cobertos estao acompanhados em [Mapa da Documentação](./Mapa-da-Documentação).

---

## Arquitetura

A documentação arquitetural sera organizada seguindo o modelo C4, priorizando diagramas em Mermaid para facilitar a visualização diretamente no GitHub, na Wiki e em outras plataformas compativeis.

Os niveis inicialmente previstos são:

- Contexto: relação do Inventarium com usuários e sistemas externos;
- Containers: distribuicao entre web, API, banco de dados e serviços auxiliares;
- Componentes: principais módulos internos do backend e das aplicações clientes;
- Implantacao: visão de ambientes, infraestrutura e dependências de execução.

As decisões arquiteturais relevantes são registradas como Architecture Decision Records em [Architecture Decision Records](./Architecture-Decision-Records). Esses registros documentam contexto, decisao, consequencias, alternativas e status das escolhas técnicas do projeto.

---

## Manutenção da Wiki

Está Wiki deve ser tratada como uma documentação viva. Alteracoes devem ser feitas no repositório principal, revisadas por Pull Request e publicadas automaticamente apos merge na branch `main`.

