package clp.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

/** Corpo de {@code POST /roles} e {@code PUT /roles/{id}}. */
@Schema(description = "Corpo de criação e de edição de cargo.")
public record RoleRequest(
    @Schema(description = "Nome do cargo. Obrigatório.", example = "Auditor") String name,
    @Schema(description = "Descrição do cargo.", example = "Apenas consulta os dados do setor.")
        String description,
    @Schema(description = "Ids das permissões do cargo.", example = "[1]")
        Set<Long> permissionIds) {}
