package clp.inventory.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados globais da documentação OpenAPI (Swagger UI em {@code /swagger-ui.html}, contrato em
 * {@code /v3/api-docs}): descrição da API, esquema de segurança Bearer JWT e as respostas 401/403
 * comuns a todo endpoint autenticado.
 */
@Configuration
public class OpenApiConfig {

  public static final String BEARER_SCHEME = "bearerAuth";

  /** Rotas públicas, espelho do permitAll do SecurityConfig: não pedem Bearer. */
  private static final Set<String> PUBLIC_PATHS = Set.of("/auth/google", "/auth/login");

  @Bean
  OpenAPI inventoryOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Inventarium — API do Inventário de Patrimônio")
                .version("v1")
                .description(
                    """
                    API do sistema de inventário de patrimônio do IFPE (CCES).

                    ## Autenticação
                    Entre com `POST /auth/google` (ID token do Google Identity Services) ou, só para as \
                    contas de sistema/teste, com `POST /auth/login` (usuário e senha). Ambos devolvem um \
                    **JWT da aplicação (HS256, 8h)**. Nos demais endpoints envie-o no cabeçalho \
                    `Authorization: Bearer <token>`; no Swagger UI use o botão **Authorize**.

                    ## Autorização
                    - **Administrador**: acesso a tudo.
                    - **Cargo por setor**: cada usuário é alocado a setores com um cargo, e o cargo carrega \
                    permissões (`VIEW_SECTOR`, `MANAGE_SECTOR`, ...). Cada endpoint descreve o que exige.
                    - Sem permissão, a resposta é **403**; token ausente, inválido, expirado ou de conta \
                    desativada, **401**.

                    ## Convenções
                    - Valores monetários de entrada (CSV e cadastro avulso) são tratados em **centavos**; as \
                    respostas de item trazem o preço já formatado em reais (`R$ 1.234,56`).
                    - Erros de regra de negócio usam o corpo padrão do Spring (`timestamp`, `status`, \
                    `error`, `message`, `path`).
                    """)
                .contact(new Contact().name("IFPE — CCES")))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT devolvido por `/auth/google` ou `/auth/login`.")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
        .tags(
            List.of(
                new Tag().name("Autenticação").description("Login e dados da sessão atual."),
                new Tag().name("Usuários").description("Usuários, alocações em setores e status."),
                new Tag().name("Setores").description("Setores, membros e alocação em lote."),
                new Tag().name("Salas").description("Salas de cada setor."),
                new Tag().name("Cargos").description("Cargos e as permissões que carregam."),
                new Tag()
                    .name("Permissões")
                    .description("Permissões do sistema (somente leitura)."),
                new Tag()
                    .name("Inventários")
                    .description("Inventários, importação de planilha CSV e listagem de itens."),
                new Tag()
                    .name("Itens")
                    .description(
                        "Itens do patrimônio: validação, observações, etiquetas e planilha."),
                new Tag()
                    .name("Auditoria de itens")
                    .description("Histórico de validações e movimentações de itens.")));
  }

  /**
   * Marca as rotas públicas como sem segurança (o contrato global exige Bearer) e adiciona 401 e
   * 403 a todo endpoint autenticado que ainda não os declarou, para que as anotações dos
   * controllers falem só do que é específico de cada rota.
   */
  @Bean
  OpenApiCustomizer securityDefaults() {
    return openApi ->
        openApi
            .getPaths()
            .forEach(
                (path, item) ->
                    item.readOperations()
                        .forEach(
                            op -> {
                              if (PUBLIC_PATHS.contains(path)) {
                                op.setSecurity(List.of());
                                return;
                              }
                              var responses = op.getResponses();
                              responses.putIfAbsent(
                                  "401",
                                  new ApiResponse()
                                      .description(
                                          "Token ausente, inválido, expirado ou conta desativada."));
                              responses.putIfAbsent(
                                  "403",
                                  new ApiResponse()
                                      .description(
                                          "O usuário não tem permissão para esta operação."));
                            }));
  }
}
