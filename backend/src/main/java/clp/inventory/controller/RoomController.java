package clp.inventory.controller;

import clp.inventory.dto.RoomDto;
import clp.inventory.dto.RoomRequest;
import clp.inventory.service.RoomService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Salas por setor. Listar vale para quem lê o setor; criar, renomear e excluir, para o
 * administrador e o gestor (MANAGE_SECTOR) do setor. Ver RoomService.
 */
@RestController
public class RoomController {

  private final RoomService roomService;

  public RoomController(RoomService roomService) {
    this.roomService = roomService;
  }

  @GetMapping("/sectors/{sectorId}/rooms")
  public ResponseEntity<List<RoomDto>> list(
      @PathVariable long sectorId, HttpServletRequest request) {
    return ResponseEntity.ok(roomService.list(sectorId, actingUserId(request)));
  }

  @PostMapping("/sectors/{sectorId}/rooms")
  public ResponseEntity<RoomDto> create(
      @PathVariable long sectorId, @RequestBody RoomRequest body, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(roomService.create(sectorId, body.name(), actingUserId(request)));
  }

  @PutMapping("/rooms/{id}")
  public ResponseEntity<RoomDto> rename(
      @PathVariable long id, @RequestBody RoomRequest body, HttpServletRequest request) {
    return ResponseEntity.ok(roomService.rename(id, body.name(), actingUserId(request)));
  }

  @DeleteMapping("/rooms/{id}")
  public ResponseEntity<Void> delete(@PathVariable long id, HttpServletRequest request) {
    roomService.delete(id, actingUserId(request));
    return ResponseEntity.noContent().build();
  }

  private long actingUserId(HttpServletRequest request) {
    return Long.parseLong(request.getAttribute("id_user").toString());
  }
}
