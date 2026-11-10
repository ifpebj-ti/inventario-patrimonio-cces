package clp.inventory.controller;

import clp.inventory.dto.EmailRequestDto;
import clp.inventory.dto.ItemDto;
import clp.inventory.dto.ObservationDto;
import clp.inventory.dto.ValidateItemRequest;
import clp.inventory.model.Item;
import clp.inventory.service.EmailService;
import clp.inventory.service.ItemService;
import clp.inventory.service.generatePdf.ItemPdfService;
import clp.inventory.service.generateSheet.SheetBuilderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Tag(
    name = "Itens",
    description = "Itens do patrimônio: validação, observações, etiquetas e planilha.")
@RestController
@RequestMapping("/item")
public class ItemController {

  private final ItemPdfService itemPdfService;
  private final SheetBuilderService sheetBuilderService;
  private final EmailService emailService;
  private final ItemService itemService;

  public ItemController(
      ItemPdfService itemPdfService,
      SheetBuilderService sheetBuilderService,
      EmailService emailService,
      ItemService itemService) {
    this.itemPdfService = itemPdfService;
    this.sheetBuilderService = sheetBuilderService;
    this.emailService = emailService;
    this.itemService = itemService;
  }

  @Operation(
      summary = "Gerar etiquetas em PDF (itens escolhidos)",
      description =
          "Gera o PDF de etiquetas com QR Code dos itens informados. Todos precisam pertencer a inventários que o usuário enxerga.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "PDF de etiquetas.",
        content =
            @Content(
                mediaType = "application/pdf",
                schema = @Schema(type = "string", format = "binary"))),
    @ApiResponse(responseCode = "500", description = "Falha ao gerar o PDF.", content = @Content)
  })
  @PostMapping("/pdf")
  public ResponseEntity<byte[]> generatePDF(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Ids dos itens.")
          @RequestBody
          List<Long> ids,
      HttpServletRequest request) {
    itemService.requireItemsView(ids, actingUserId(request));
    try {
      byte[] pdf = itemPdfService.generateItemPdf(ids);

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_PDF);
      headers.setContentDispositionFormData("filename", "etiquetas.pdf");

      return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  @Operation(summary = "Buscar item por id", description = "Exige enxergar o inventário do item.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Item.",
        content = @Content(schema = @Schema(implementation = ItemDto.class))),
    @ApiResponse(responseCode = "404", description = "Item não encontrado.", content = @Content)
  })
  @GetMapping("/{id}")
  public ResponseEntity<ItemDto> getItemById(
      @Parameter(description = "Id do item.") @PathVariable Long id, HttpServletRequest request) {
    Item item = itemService.findAccessibleItem(id, actingUserId(request));

    return ResponseEntity.ok(ItemDto.from(item));
  }

  @Operation(
      summary = "Atualizar item",
      description =
          "O id do caminho precisa ser igual ao do corpo. Exige poder operar o inventário (administrador, gestor ou MANAGE_ITEM). Alterar o estado de validação de um item não é permitido depois que o inventário é finalizado.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Item atualizado.",
        content = @Content(schema = @Schema(implementation = ItemDto.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Id do caminho diferente do id do corpo, ou corpo inválido.",
        content = @Content),
    @ApiResponse(responseCode = "404", description = "Item não encontrado.", content = @Content),
    @ApiResponse(responseCode = "409", description = "Inventário finalizado.", content = @Content)
  })
  @PutMapping("/{id}")
  public ResponseEntity<ItemDto> updateItem(
      @PathVariable Long id, @Valid @RequestBody ItemDto updatedItem, HttpServletRequest request) {
    if (!id.equals(updatedItem.id())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "ID no path e no corpo da requisição não correspondem.");
    }
    Item savedItem = itemService.updateItem(updatedItem, actingUserId(request));
    return ResponseEntity.ok(ItemDto.from(savedItem));
  }

  @Operation(
      summary = "Validar item",
      description =
          "Marca o item como validado na sala informada e registra a auditoria (sala encontrada x cadastrada e se divergem). Em setor que tem salas, roomId é obrigatório; em setor sem salas pode ser omitido. Se o usuário não é alocado ao setor do inventário (e não é administrador), a validação é parcial e fica pendente do setor de destino. Validar de novo um item já validado só gera novo registro se a sala for outra. Não é permitido com o inventário finalizado.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Item validado.",
        content = @Content(schema = @Schema(implementation = ItemDto.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "roomId ausente em setor com salas, ou sala que não é do setor do inventário.",
        content = @Content),
    @ApiResponse(responseCode = "404", description = "Item não encontrado.", content = @Content),
    @ApiResponse(responseCode = "409", description = "Inventário finalizado.", content = @Content)
  })
  @PatchMapping("/{id}/validate")
  public ResponseEntity<ItemDto> validateItem(
      @PathVariable Long id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false)
          @RequestBody(required = false)
          ValidateItemRequest body,
      HttpServletRequest request) {
    Item validated =
        itemService.validateItem(id, actingUserId(request), body != null ? body.roomId() : null);
    return ResponseEntity.ok(ItemDto.from(validated));
  }

  @Operation(
      summary = "Substituir observações do item",
      description =
          "Recebe a lista completa de observações do item, que substitui a atual. Exige poder operar o inventário (administrador, gestor ou MANAGE_ITEM).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Item com as observações atualizadas.",
        content = @Content(schema = @Schema(implementation = ItemDto.class))),
    @ApiResponse(responseCode = "404", description = "Item não encontrado.", content = @Content)
  })
  @PatchMapping("/{id}/notes")
  public ResponseEntity<ItemDto> updateItemNotes(
      @PathVariable Long id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              description = "Lista completa de observações.")
          @RequestBody
          List<ObservationDto> notesList,
      HttpServletRequest request) {
    Item updatedItem = itemService.updateItemNotes(id, notesList, actingUserId(request));
    return ResponseEntity.ok(ItemDto.from(updatedItem));
  }

  @Operation(
      summary = "Gerar etiquetas em PDF (inventário inteiro)",
      description =
          "Gera o PDF de etiquetas com QR Code de todos os itens do inventário. O corpo é o id do inventário, como número puro (ex.: 10).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "PDF de etiquetas.",
        content =
            @Content(
                mediaType = "application/pdf",
                schema = @Schema(type = "string", format = "binary"))),
    @ApiResponse(
        responseCode = "404",
        description = "Inventário não encontrado.",
        content = @Content),
    @ApiResponse(responseCode = "500", description = "Falha ao gerar o PDF.", content = @Content)
  })
  @PostMapping("/all-items-pdf")
  public ResponseEntity<byte[]> generatePDFAllItems(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
              description = "Id do inventário (número puro).",
              content =
                  @Content(schema = @Schema(type = "integer", format = "int64", example = "10")))
          @RequestBody
          long inventoryId,
      HttpServletRequest request) {
    itemService.requireInventoryView(inventoryId, actingUserId(request));
    try {
      byte[] pdf = itemPdfService.generateAllItemsPdf(inventoryId);

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_PDF);
      headers.setContentDispositionFormData("filename", "etiquetas.pdf");

      return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  @Operation(
      summary = "Exportar planilha do inventário",
      description = "Baixa o patrimônio do inventário em Excel (patrimonio.xlsx).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Arquivo patrimonio.xlsx.",
        content =
            @Content(
                mediaType = "application/octet-stream",
                schema = @Schema(type = "string", format = "binary"))),
    @ApiResponse(
        responseCode = "404",
        description = "Inventário não encontrado.",
        content = @Content)
  })
  @GetMapping("/export-sheet")
  public ResponseEntity<byte[]> exportItems(
      @Parameter(description = "Id do inventário.") @RequestParam long inventoryId,
      HttpServletRequest request)
      throws IOException {
    itemService.requireInventoryView(inventoryId, actingUserId(request));
    byte[] excelData = sheetBuilderService.createAllItemsSheet(inventoryId);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
    headers.setContentDisposition(
        ContentDisposition.builder("attachment").filename("patrimonio.xlsx").build());

    return ResponseEntity.ok().headers(headers).body(excelData);
  }

  @Operation(
      summary = "Enviar planilha do inventário por e-mail",
      description =
          "Gera a planilha do inventário e a envia como anexo (patrimonio.xlsx) ao e-mail informado.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "E-mail enviado.", content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "Inventário não encontrado.",
        content = @Content)
  })
  @PostMapping("/send-email-sheet")
  public ResponseEntity<Void> sendSheetByEmail(
      @Parameter(description = "Id do inventário.") @RequestParam long inventoryId,
      @RequestBody EmailRequestDto emailRequest,
      HttpServletRequest request)
      throws IOException {
    itemService.requireInventoryView(inventoryId, actingUserId(request));
    byte[] excelData = sheetBuilderService.createAllItemsSheet(inventoryId);

    emailService.sendEmailWithAttachment(
        emailRequest.email(),
        emailRequest.subject(),
        emailRequest.message(),
        "patrimonio.xlsx",
        excelData);

    return ResponseEntity.ok().build();
  }

  private long actingUserId(HttpServletRequest request) {
    return Long.parseLong(request.getAttribute("id_user").toString());
  }
}
