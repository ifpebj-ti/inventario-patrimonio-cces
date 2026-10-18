# Finalização de inventários

Na tela `/inventory/[id]`, o quadro **Situação do inventário** mostra o andamento e,
após a finalização, a data e a hora registradas. A ação **Finalizar inventário** usa
o botão, o badge e o modal de confirmação existentes. A lista e os indicadores do
Dashboard são atualizados após o sucesso.

O backend autoriza a finalização para quem tem `ADMIN` ou `MANAGE_SECTOR` e vínculo
com o setor do inventário. O criador não ganha essa autorização automaticamente.
Inventários antigos sem setor podem ser finalizados por um admin. Admins veem todos
os inventários; os demais perfis veem os dos setores aos quais estão vinculados.

Depois de finalizado, o inventário não aceita escaneamento (validação de itens),
importação, remoção nem limpeza de itens, na interface e na API (`409`).

- `GET /inventory/{id}` retorna `{ inventory, canFinalize }` para admin ou qualquer
  usuário vinculado ao setor; `canFinalize` só é verdadeiro para admin e gestor.
- `POST /inventory/{id}/finalize` registra a data no servidor e retorna o inventário.
  Responde com `403` sem autorização, `404` se não existir e `409` se já finalizado.
- O status persistido começa em `EM_ANDAMENTO` e passa a `CONCLUIDO` somente por
  finalização explícita, independentemente da validação dos itens. `VENCIDO` continua
  sendo um indicador calculado no DTO para inventários abertos fora do prazo.
- `completedAt` é a data e hora efetiva, separada de `plannedEndDate`, o prazo previsto.
  O formato enviado é ISO local, seguindo as datas `LocalDateTime` do backend.
- Não há reabertura nem bloqueio adicional de edição dos itens nesta funcionalidade.
  Nenhuma entidade ou tabela de log é criada.

A migration `1.0.xml` acrescenta o changeset `20261013-03` com `status`, `completed_at`
e uma restrição de consistência. O changeset anterior permanece intacto. Os registros
existentes começam em andamento, pois não possuem uma finalização explícita registrada.
A migration é aplicada pelo Liquibase na próxima inicialização do backend.

A finalização usa transação e bloqueio de escrita no inventário, preservando a data
original e impedindo duas finalizações concorrentes.

Validação: `InventoryTest`, `InventoryCompletionTest`, `InventoryCompletionE2ETest`
(PostgreSQL via Testcontainers) e `tests/e2e/inventory-completion.spec.ts` (Playwright).
