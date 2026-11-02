package clp.inventory.dto;

import java.util.List;

/**
 * Salas citadas na planilha, para o gestor conferir antes de importar: as que ainda não existem no
 * setor (a importação vai criá-las) e as que já existem.
 */
public record RoomsPreviewDto(List<String> newRooms, List<String> existingRooms) {}
