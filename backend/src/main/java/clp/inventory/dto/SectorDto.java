package clp.inventory.dto;

import clp.inventory.model.Sector;

public record SectorDto(
    long id,
    String name,
    String code,
    boolean active,
    String createdAt,
    String updatedAt,
    boolean deletable) {

  /** {@code deletable}: o setor não tem usuário, inventário nem auditoria e pode ser excluído. */
  public static SectorDto from(Sector sector, boolean deletable) {
    return new SectorDto(
        sector.id(),
        sector.name(),
        sector.code(),
        sector.active(),
        sector.createdAt() != null ? sector.createdAt().toString() : null,
        sector.updatedAt() != null ? sector.updatedAt().toString() : null,
        deletable);
  }
}
