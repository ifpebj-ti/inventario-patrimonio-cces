# Inventarium

Bem-vindo a Wiki do projeto **Inventarium**.

O Inventarium e uma plataforma academica desenvolvida para apoiar o processo de tombamento e gerenciamento dos patrimonios do IFPE. A solucao combina uma aplicacao web de gerenciamento, um aplicativo mobile para operacoes em campo e uma API backend responsavel pelas regras de negocio, autenticacao e persistencia dos dados.

O projeto foi desenvolvido inicialmente para atender a demanda de Marcia Bandeira no contexto da disciplina de Engenharia de Software e, a partir dessa base, busca evoluir para uma solucao capaz de abranger o processo de gestao patrimonial em todo o IFPE.

---

## Visao Geral

O processo de tombamento patrimonial exige organizacao, rastreabilidade e facilidade de consulta. O Inventarium busca reduzir atividades manuais e centralizar informacoes sobre bens patrimoniais, permitindo que usuarios autorizados registrem, consultem e acompanhem itens por meio de recursos web e mobile.

Entre as capacidades previstas e implementadas no ecossistema do projeto estao:

- gerenciamento de itens patrimoniais;
- apoio ao tombamento de bens do IFPE;
- identificacao de itens por codigo de barras e QR Code;
- uso de aplicativo mobile para leitura e consulta em campo;
- interface web para administracao e acompanhamento;
- backend com API REST, autenticacao e integracao com banco de dados;
- geracao de materiais auxiliares, como etiquetas, PDFs ou planilhas, conforme suporte da API.

---

## Objetivo

O objetivo do Inventarium e oferecer uma ferramenta integrada para tornar o processo de tombamento e controle patrimonial mais eficiente, padronizado e acessivel para os setores envolvidos no IFPE.

---

## Publico-Alvo

A documentacao e o sistema sao voltados para:

- servidores e equipes responsaveis pelo patrimonio institucional;
- usuarios que realizam levantamento, consulta ou atualizacao de bens;
- equipe de desenvolvimento e manutencao do projeto;
- docentes, orientadores e avaliadores vinculados ao contexto academico do projeto.

---

## Aplicacoes do Projeto

| Aplicacao | Descricao | Tecnologia principal |
| --- | --- | --- |
| Backend | API responsavel por regras de negocio, autenticacao, persistencia e geracao de artefatos auxiliares. | Java, Spring Boot, Gradle, PostgreSQL |
| Frontend | Aplicacao web para gerenciamento e acompanhamento dos dados patrimoniais. | Next.js, React, TypeScript |
| Mobile | Aplicativo para operacoes em campo, incluindo consulta e leitura de identificadores patrimoniais. | Expo, React Native, TypeScript |

---

## Documentacao

Esta Wiki sera evoluida de forma incremental. A fonte oficial dos arquivos esta no repositorio principal, dentro de `docs/wiki`, e a publicacao na Wiki do GitHub e feita automaticamente por GitHub Actions.

Documentos planejados:

1. [Documento de Visao](./Documento-de-Visao)
2. [Documento de Requisitos](./Documento-de-Requisitos)
3. [Documento de Arquitetura C4](./Documento-de-Arquitetura-C4)
4. [Documento de Modelagem de Dados](./Documento-de-Modelagem-de-Dados)
5. [Guia de Execucao e Configuracao](./Guia-de-Execucao-e-Configuracao)
6. [Manual do Usuario](./Manual-do-Usuario)
7. [Guia de Boas Praticas de Desenvolvimento Seguro](./Guia-de-Boas-Praticas-de-Desenvolvimento-Seguro)
8. [Modelagem de Ameacas](./Modelagem-de-Ameacas)
9. [Guia de Testes de Usuario](./Guia-de-Testes-de-Usuario)

---

## Arquitetura

A documentacao arquitetural sera organizada seguindo o modelo C4, priorizando diagramas em Mermaid para facilitar a visualizacao diretamente no GitHub, na Wiki e em outras plataformas compativeis.

Os niveis inicialmente previstos sao:

- Contexto: relacao do Inventarium com usuarios e sistemas externos;
- Containers: distribuicao entre web, mobile, API, banco de dados e servicos auxiliares;
- Componentes: principais modulos internos do backend e das aplicacoes clientes;
- Implantacao: visao de ambientes, infraestrutura e dependencias de execucao.

---

## Manutencao da Wiki

Esta Wiki deve ser tratada como uma documentacao viva. Alteracoes devem ser feitas no repositorio principal, revisadas por Pull Request e publicadas automaticamente apos merge na branch `main`.

