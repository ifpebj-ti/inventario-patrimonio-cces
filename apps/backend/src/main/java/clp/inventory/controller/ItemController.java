package clp.inventory.controller;

// Importa DTOs (Data Transfer Objects) para requisição de e-mail, item e observação.
import clp.inventory.dto.EmailRequestDto;
import clp.inventory.dto.ItemDto;
import clp.inventory.dto.ObservationDto;
// Importa o modelo Item.
import clp.inventory.model.Item;
// Importa serviços relacionados a e-mail, item, geração de PDF de item e construção de planilhas.
import clp.inventory.service.EmailService;
import clp.inventory.service.ItemService;
import clp.inventory.service.generatePdf.ItemPdfService;
import clp.inventory.service.generateSheet.SheetBuilderService;
// Importa anotação para validação de beans.
import jakarta.validation.Valid;
// Importa classes do Spring para manipulação de HTTP (cabeçalhos, status, etc.).
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
// Importa exceção para status de resposta HTTP.
import org.springframework.web.server.ResponseStatusException;

// Importa classes para operações de I/O e manipulação de listas.
import java.io.IOException;
import java.util.List;

// Anotação que indica que esta classe é um controlador REST.
@RestController
// Mapeia todas as requisições que começam com "/item" para este controlador.
@RequestMapping("/item")
// Permite que requisições de qualquer origem (domínio) acessem este controlador (CORS).
@CrossOrigin(origins = "*")
public class ItemController {

    // Injeção de dependência do serviço de geração de PDF para itens.
    private final ItemPdfService itemPdfService;
    // Injeção de dependência do serviço de construção de planilhas.
    private final SheetBuilderService sheetBuilderService;
    // Injeção de dependência do serviço de e-mail.
    private final EmailService emailService;
    // Injeção de dependência do serviço de item.
    private final ItemService itemService;

    // Construtor que recebe os serviços como dependências, injetadas pelo Spring.
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

    // Mapeia requisições POST para "/item/pdf".
    @PostMapping("/pdf")
    // Gera um PDF de etiquetas para uma lista de IDs de itens fornecidos no corpo da requisição.
    public ResponseEntity<byte[]> generatePDF(@RequestBody List<Long> ids) {
        try {
            // Chama o serviço para gerar o PDF dos itens.
            byte[] pdf = itemPdfService.generateItemPdf(ids);

            // Configura os cabeçalhos da resposta HTTP para indicar que é um PDF para download.
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("filename", "etiquetas.pdf"); // Define o nome do arquivo para download.

            // Retorna o PDF como um array de bytes, com os cabeçalhos e status HTTP 200 OK.
            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        } catch (Exception e) {
            // Em caso de erro, retorna uma resposta HTTP 500 Internal Server Error.
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Mapeia requisições GET para "/item/{id}".
    @GetMapping("/{id}")
    // Obtém um item pelo seu ID.
    public ResponseEntity<ItemDto> getItemById(@PathVariable Long id) {
        // Tenta encontrar o item pelo ID. Se não encontrar, lança uma ResponseStatusException com status 404 Not Found.
        Item item = itemService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item não encontrado com o ID: " + id));

        // Retorna o item encontrado (convertido para ItemDto) com status HTTP 200 OK.
        return ResponseEntity.ok(ItemDto.from(item));
    }

    // Mapeia requisições PUT para "/item/{id}".
    @PutMapping("/{id}")
    // Atualiza um item existente. O ID é passado no caminho e o item atualizado no corpo da requisição.
    public ResponseEntity<ItemDto> updateItem(@PathVariable Long id, @Valid @RequestBody ItemDto updatedItem) {
        // Verifica se o ID no caminho da URL corresponde ao ID do item no corpo da requisição.
        if (!id.equals(updatedItem.id())) {
            // Se não corresponder, lança uma ResponseStatusException com status 400 Bad Request.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID no path e no corpo da requisição não correspondem.");
        }
        // Chama o serviço para atualizar o item.
        Item savedItem = itemService.updateItem(updatedItem);
        // Retorna o item atualizado (convertido para ItemDto) com status HTTP 200 OK.
        return ResponseEntity.ok(ItemDto.from(savedItem));
    }

    // Mapeia requisições PATCH para "/item/{id}/notes". PATCH é usado para atualizações parciais.
    @PatchMapping("/{id}/notes")
    // Atualiza as notas (observações) de um item específico.
    public ResponseEntity<ItemDto> updateItemNotes(@PathVariable Long id, @RequestBody List<ObservationDto> notesList) {
        // Chama o serviço para atualizar as observações do item.
        Item updatedItem = itemService.updateItemNotes(id, notesList);
        // Retorna o item com as observações atualizadas (convertido para ItemDto) com status HTTP 200 OK.
        return ResponseEntity.ok(ItemDto.from(updatedItem));
    }

    // Mapeia requisições POST para "/item/all-items-pdf".
    @PostMapping("/all-items-pdf")
    // Gera um PDF de etiquetas para todos os itens de um inventário específico.
    public ResponseEntity<byte[]> generatePDFAllItems(@RequestBody long inventoryId) {
        try {
            // Chama o serviço para gerar o PDF de todos os itens de um inventário.
            byte[] pdf = itemPdfService.generateAllItemsPdf(inventoryId);

            // Configura os cabeçalhos da resposta HTTP para indicar que é um PDF para download.
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("filename", "etiquetas.pdf"); // Define o nome do arquivo.

            // Retorna o PDF como um array de bytes, com os cabeçalhos e status HTTP 200 OK.
            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        } catch (Exception e) {
            // Em caso de erro, retorna uma resposta HTTP 500 Internal Server Error.
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Mapeia requisições GET para "/item/export-sheet".
    @GetMapping("/export-sheet")
    // Exporta todos os itens de um inventário para uma planilha Excel.
    public ResponseEntity<byte[]> exportItems(@RequestParam long inventoryId) throws IOException {
        // Chama o serviço para criar a planilha com todos os itens do inventário.
        byte[] excelData = sheetBuilderService.createAllItemsSheet(inventoryId);

        // Configura os cabeçalhos da resposta HTTP para indicar que é um arquivo de stream de octetos (binário)
        // e que deve ser anexado (download) com um nome de arquivo específico.
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition
                .builder("attachment") // Indica que o conteúdo deve ser baixado.
                .filename("patrimonio.xlsx") // Nome do arquivo para download.
                .build());

        // Retorna a planilha como um array de bytes, com os cabeçalhos e status HTTP 200 OK.
        return ResponseEntity.ok()
                .headers(headers)
                .body(excelData);
    }

    // Mapeia requisições POST para "/item/send-email-sheet".
    @PostMapping("/send-email-sheet")
    // Envia uma planilha Excel de itens por e-mail.
    public ResponseEntity<Void> sendSheetByEmail(
            @RequestParam long inventoryId, // ID do inventário cujos itens serão enviados.
            @RequestBody EmailRequestDto request // DTO contendo o e-mail, assunto e mensagem.
    ) throws IOException {
        // Gera a planilha Excel com os itens do inventário.
        byte[] excelData = sheetBuilderService.createAllItemsSheet(inventoryId);

        // Chama o serviço de e-mail para enviar o e-mail com a planilha como anexo.
        emailService.sendEmailWithAttachment(
                request.email(),    // Endereço de e-mail do destinatário.
                request.subject(),  // Assunto do e-mail.
                request.message(),  // Mensagem do corpo do e-mail.
                "patrimonio.xlsx",  // Nome do arquivo do anexo.
                excelData           // Dados da planilha como array de bytes.
        );

        // Retorna uma resposta HTTP 200 OK sem corpo (Void).
        return ResponseEntity.ok().build();
    }
}