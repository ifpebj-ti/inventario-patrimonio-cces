package clp.inventory.controller;

import clp.inventory.dto.EmailRequestDto;
import clp.inventory.dto.ItemDto;
import clp.inventory.dto.ObservationDto;
import clp.inventory.model.Item;
import clp.inventory.service.EmailService;
import clp.inventory.service.ItemService;
import clp.inventory.service.generatePdf.ItemPdfService;
import clp.inventory.service.generateSheet.SheetBuilderService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/item")
@CrossOrigin(origins = "*")
public class ItemController {

    private final ItemPdfService itemPdfService;
    private final SheetBuilderService sheetBuilderService;
    private final EmailService emailService;
    private final ItemService itemService;

    public ItemController(
            ItemPdfService itemPdfService,
            SheetBuilderService sheetBuilderService,
            EmailService emailService,
            ItemService itemService
    ) {
        this.itemPdfService = itemPdfService;
        this.sheetBuilderService = sheetBuilderService;
        this.emailService = emailService;
        this.itemService = itemService;
    }

    @PostMapping("/pdf")
    public ResponseEntity<byte[]> generatePDF(@RequestBody List<Long> ids) {
        try {
            byte[] pdf = itemPdfService.generateItemPdf(ids);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("filename", "etiquetas.pdf");

            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ItemDto> getItemById(@PathVariable Long id) {
        Item item = itemService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item não encontrado com o ID: " + id));

        return ResponseEntity.ok(ItemDto.from(item));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ItemDto> updateItem(@PathVariable Long id, @Valid @RequestBody ItemDto updatedItem) {
        if (!id.equals(updatedItem.id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID no path e no corpo da requisição não correspondem.");
        }
        Item savedItem = itemService.updateItem(updatedItem);
        return ResponseEntity.ok(ItemDto.from(savedItem));
    }

    @PatchMapping("/{id}/notes")
    public ResponseEntity<ItemDto> updateItemNotes(@PathVariable Long id, @RequestBody List<ObservationDto> notesList) {
        Item updatedItem = itemService.updateItemNotes(id, notesList);
        return ResponseEntity.ok(ItemDto.from(updatedItem));
    }

    @PostMapping("/all-items-pdf")
    public ResponseEntity<byte[]> generatePDFAllItems(@RequestBody long inventoryId) {
        try {
            byte[] pdf = itemPdfService.generateAllItemsPdf(inventoryId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("filename", "etiquetas.pdf");

            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/export-sheet")
    public ResponseEntity<byte[]> exportItems(@RequestParam long inventoryId) throws IOException {
        byte[] excelData = sheetBuilderService.createAllItemsSheet(inventoryId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition
                .builder("attachment")
                .filename("patrimonio.xlsx")
                .build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(excelData);
    }

    @PostMapping("/send-email-sheet")
    public ResponseEntity<Void> sendSheetByEmail(
            @RequestParam long inventoryId,
            @RequestBody EmailRequestDto request
    ) throws IOException {
        byte[] excelData = sheetBuilderService.createAllItemsSheet(inventoryId);

        emailService.sendEmailWithAttachment(
                request.email(),
                request.subject(),
                request.message(),
                "patrimonio.xlsx",
                excelData
        );

        return ResponseEntity.ok().build();
    }
}
