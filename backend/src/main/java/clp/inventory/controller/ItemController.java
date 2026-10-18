package clp.inventory.controller;

import clp.inventory.dto.EmailRequestDto;
import clp.inventory.dto.ItemDto;
import clp.inventory.dto.ObservationDto;
import clp.inventory.model.Item;
import clp.inventory.service.EmailService;
import clp.inventory.service.ItemService;
import clp.inventory.service.generatePdf.ItemPdfService;
import clp.inventory.service.generateSheet.SheetBuilderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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

  @PostMapping("/pdf")
  public ResponseEntity<byte[]> generatePDF(
      @RequestBody List<Long> ids, HttpServletRequest request) {
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

  @GetMapping("/{id}")
  public ResponseEntity<ItemDto> getItemById(@PathVariable Long id, HttpServletRequest request) {
    Item item = itemService.findAccessibleItem(id, actingUserId(request));

    return ResponseEntity.ok(ItemDto.from(item));
  }

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

  @PatchMapping("/{id}/validate")
  public ResponseEntity<ItemDto> validateItem(@PathVariable Long id, HttpServletRequest request) {
    Item validated = itemService.validateItem(id, actingUserId(request));
    return ResponseEntity.ok(ItemDto.from(validated));
  }

  @PatchMapping("/{id}/notes")
  public ResponseEntity<ItemDto> updateItemNotes(
      @PathVariable Long id,
      @RequestBody List<ObservationDto> notesList,
      HttpServletRequest request) {
    Item updatedItem = itemService.updateItemNotes(id, notesList, actingUserId(request));
    return ResponseEntity.ok(ItemDto.from(updatedItem));
  }

  @PostMapping("/all-items-pdf")
  public ResponseEntity<byte[]> generatePDFAllItems(
      @RequestBody long inventoryId, HttpServletRequest request) {
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

  @GetMapping("/export-sheet")
  public ResponseEntity<byte[]> exportItems(
      @RequestParam long inventoryId, HttpServletRequest request) throws IOException {
    itemService.requireInventoryView(inventoryId, actingUserId(request));
    byte[] excelData = sheetBuilderService.createAllItemsSheet(inventoryId);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
    headers.setContentDisposition(
        ContentDisposition.builder("attachment").filename("patrimonio.xlsx").build());

    return ResponseEntity.ok().headers(headers).body(excelData);
  }

  @PostMapping("/send-email-sheet")
  public ResponseEntity<Void> sendSheetByEmail(
      @RequestParam long inventoryId,
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
