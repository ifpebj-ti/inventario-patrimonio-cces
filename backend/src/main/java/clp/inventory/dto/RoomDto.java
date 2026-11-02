package clp.inventory.dto;

import clp.inventory.model.Room;

/**
 * Sala de um setor. {@code itemCount} são os itens cadastrados nela; {@code deletable} é falso
 * quando algum item (cadastrado ou encontrado) ou registro de auditoria aponta para ela.
 */
public record RoomDto(long id, String name, long sectorId, long itemCount, boolean deletable) {

  public static RoomDto from(Room room, long itemCount, boolean deletable) {
    return new RoomDto(room.id(), room.name(), room.sector().id(), itemCount, deletable);
  }
}
