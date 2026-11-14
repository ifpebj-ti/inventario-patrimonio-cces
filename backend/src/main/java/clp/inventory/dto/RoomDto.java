package clp.inventory.dto;

import clp.inventory.model.Room;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Sala de um setor. {@code itemCount} são os itens cadastrados nela; {@code deletable} é falso
 * quando algum item (cadastrado ou encontrado) ou registro de auditoria aponta para ela.
 */
@Schema(description = "Sala de um setor.")
public record RoomDto(
    @Schema(description = "Id da sala.", example = "7") long id,
    @Schema(description = "Nome da sala.", example = "Sala 101") String name,
    @Schema(description = "Setor da sala.", example = "1") long sectorId,
    @Schema(description = "Itens cadastrados nela.", example = "12") long itemCount,
    @Schema(
            description =
                "Falso quando algum item (cadastrado ou encontrado) ou registro de auditoria aponta para a sala.")
        boolean deletable) {

  public static RoomDto from(Room room, long itemCount, boolean deletable) {
    return new RoomDto(room.id(), room.name(), room.sector().id(), itemCount, deletable);
  }
}
