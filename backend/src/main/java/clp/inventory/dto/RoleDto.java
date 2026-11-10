package clp.inventory.dto;

import clp.inventory.model.Permission;
import clp.inventory.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code management}: o cargo tem MANAGE_SECTOR. {@code allocationCount}/{@code userCount}: em
 * quantas alocações e para quantos usuários o cargo vale. {@code deletable}: sem alocação e sem
 * permissão, o único caso em que pode ser excluído.
 */
@Schema(description = "Cargo, com as permissões e a contagem de uso.")
public record RoleDto(
    @Schema(description = "Id do cargo.", example = "2") long id,
    @Schema(description = "Nome do cargo.", example = "Gestor de Setor") String name,
    @Schema(description = "Descrição do cargo.") String description,
    @Schema(description = "Ids das permissões do cargo.", example = "[1, 2, 3]")
        Set<Long> permissionIds,
    @Schema(description = "Verdadeiro se o cargo tem MANAGE_SECTOR (cargo de gestão).")
        boolean management,
    @Schema(description = "Em quantas alocações o cargo é usado.", example = "3")
        long allocationCount,
    @Schema(description = "Quantos usuários distintos têm o cargo.", example = "3") long userCount,
    @Schema(
            description =
                "Verdadeiro quando o cargo não tem alocação nem permissão — o único caso em que pode ser excluído.")
        boolean deletable,
    @Schema(description = "Data e hora de criação (ISO-8601).", example = "2026-01-15T10:00:00")
        String createdAt,
    @Schema(
            description = "Data e hora da última alteração (ISO-8601).",
            example = "2026-02-01T08:30:00")
        String updatedAt) {

  public static RoleDto from(Role role, boolean management, long allocationCount, long userCount) {
    return new RoleDto(
        role.id(),
        role.name(),
        role.description(),
        role.permissions().stream().map(Permission::id).collect(Collectors.toSet()),
        management,
        allocationCount,
        userCount,
        allocationCount == 0 && role.permissions().isEmpty(),
        role.createdAt() != null ? role.createdAt().toString() : null,
        role.updatedAt() != null ? role.updatedAt().toString() : null);
  }
}
