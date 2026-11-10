package clp.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Corpo de {@code PUT /sectors/{id}/members}: os usuários a alocar e o cargo de todos eles. */
@Schema(description = "Alocação em lote: os usuários e o cargo que todos eles terão no setor.")
public record AllocateMembersDto(
    @Schema(description = "Ids dos usuários a alocar.", example = "[2, 3, 7]") List<Long> userIds,
    @Schema(description = "Id do cargo de todos eles no setor.", example = "2") Long roleId) {}
