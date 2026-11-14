package clp.inventory.controller;

import clp.inventory.dto.RoomDto;
import clp.inventory.dto.RoomRequest;
import clp.inventory.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Salas por setor. Listar vale para quem lê o setor; criar, renomear e excluir, para o
 * administrador e o gestor (MANAGE_SECTOR) do setor. Ver RoomService.
 */
@Tag(name = "Salas", description = "Salas de cada setor.")
@RestController
public class RoomController {

  private final RoomService roomService;

  public RoomController(RoomService roomService) {
    this.roomService = roomService;
  }

  @Operation(summary = "Listar salas do setor", description = "Vale para quem lê o setor.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Salas do setor.",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = RoomDto.class)))),
    @ApiResponse(responseCode = "404", description = "Setor não encontrado.", content = @Content)
  })
  @GetMapping("/sectors/{sectorId}/rooms")
  public ResponseEntity<List<RoomDto>> list(
      @PathVariable long sectorId, HttpServletRequest request) {
    return ResponseEntity.ok(roomService.list(sectorId, actingUserId(request)));
  }

  @Operation(
      summary = "Criar sala",
      description =
          "Exige administrador ou gestor (MANAGE_SECTOR) do setor. O nome é obrigatório e único no setor, sem diferenciar caixa, acento e espaços.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Sala criada.",
        content = @Content(schema = @Schema(implementation = RoomDto.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Nome vazio, longo demais ou já usado por outra sala do setor.",
        content = @Content),
    @ApiResponse(responseCode = "404", description = "Setor não encontrado.", content = @Content)
  })
  @PostMapping("/sectors/{sectorId}/rooms")
  public ResponseEntity<RoomDto> create(
      @PathVariable long sectorId, @RequestBody RoomRequest body, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(roomService.create(sectorId, body.name(), actingUserId(request)));
  }

  @Operation(
      summary = "Renomear sala",
      description = "Exige administrador ou gestor (MANAGE_SECTOR) do setor da sala.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Sala renomeada.",
        content = @Content(schema = @Schema(implementation = RoomDto.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Nome vazio, longo demais ou já usado por outra sala do setor.",
        content = @Content),
    @ApiResponse(responseCode = "404", description = "Sala não encontrada.", content = @Content)
  })
  @PutMapping("/rooms/{id}")
  public ResponseEntity<RoomDto> rename(
      @PathVariable long id, @RequestBody RoomRequest body, HttpServletRequest request) {
    return ResponseEntity.ok(roomService.rename(id, body.name(), actingUserId(request)));
  }

  @Operation(
      summary = "Excluir sala",
      description =
          "Exige administrador ou gestor (MANAGE_SECTOR) do setor. Só pode ser excluída quando nenhum item (cadastrado ou encontrado) nem registro de auditoria aponta para ela (veja deletable em RoomDto); senão responde 409.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Sala excluída.", content = @Content),
    @ApiResponse(responseCode = "404", description = "Sala não encontrada.", content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "A sala tem itens vinculados.",
        content = @Content)
  })
  @DeleteMapping("/rooms/{id}")
  public ResponseEntity<Void> delete(@PathVariable long id, HttpServletRequest request) {
    roomService.delete(id, actingUserId(request));
    return ResponseEntity.noContent().build();
  }

  private long actingUserId(HttpServletRequest request) {
    return Long.parseLong(request.getAttribute("id_user").toString());
  }
}
