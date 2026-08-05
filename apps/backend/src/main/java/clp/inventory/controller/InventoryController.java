package clp.inventory.controller;

// Importa DTOs (Data Transfer Objects) usados para comunicação de dados.
import clp.inventory.dto.*;
// Importa as classes de modelo para Inventário, Item e Observação.
import clp.inventory.model.Inventory;
import clp.inventory.model.Item;
import clp.inventory.model.Observation;
// Importa os serviços que contêm a lógica de negócio para Inventário e Item.
import clp.inventory.service.InventoryService;
import clp.inventory.service.ItemService;
// Importa a classe de utilitário para autenticação, que ajuda a extrair informações do token.
import clp.inventory.utils.AuthenticationUtils;
// Importa classes do Jackson para lidar com processamento JSON (embora não diretamente usadas neste trecho).
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
// Importa HttpServletRequest para acessar informações da requisição HTTP.
import jakarta.servlet.http.HttpServletRequest;
// Importa @Valid para validação de objetos DTO.
import jakarta.validation.Valid;
// Importa classes do Apache POI para trabalhar com arquivos Excel (HSSF para .xls, XSSF para .xlsx).
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
// Importa Page do Spring Data para paginação de resultados.
import org.springframework.data.domain.Page;
// Importa @Param (embora não usada diretamente neste código, pode ser um resquício ou para uso futuro com SpEL).
import org.springframework.data.repository.query.Param;
// Importa HttpStatus e ResponseEntity do Spring para construir respostas HTTP.
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
// Importa anotações REST do Spring para mapeamento de requisições.
import org.springframework.web.bind.annotation.*;
// Importa MultipartFile para lidar com upload de arquivos.
import org.springframework.web.multipart.MultipartFile;

// Importa classes para operações de I/O, manipulação de números e datas, e coleções.
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Anotação que marca esta classe como um controlador REST, o que significa que ela lida com requisições HTTP.
@RestController
// Mapeia todas as requisições que começam com "/inventory" para este controlador.
@RequestMapping("/inventory")
// Permite que qualquer origem (domínio) faça requisições para este controlador (CORS).
@CrossOrigin(origins = "*")
public class InventoryController {

    // Injeta o serviço de inventário, que contém a lógica de negócios para inventários.
    private final InventoryService inventoryService;
    // Injeta o serviço de item, que contém a lógica de negócios para itens.
    private final ItemService itemService;
    // Injeta o utilitário de autenticação para extrair informações do token.
    private final AuthenticationUtils authenticationUtils;

    // Construtor que recebe os serviços e o utilitário como dependências, injetadas pelo Spring.
    public InventoryController(InventoryService inventoryService,
                               ItemService itemService,
                               AuthenticationUtils authenticationUtils
    ) {
        this.inventoryService = inventoryService;
        this.itemService = itemService;
        this.authenticationUtils = authenticationUtils;
    }

    // Mapeia requisições POST para "/inventory/new-inventory".
    @PostMapping("/new-inventory")
    // Cria um novo inventário. Recebe um InventoryDto validado no corpo da requisição e o objeto HttpServletRequest.
    public ResponseEntity<?> createInventory(
            @Valid @RequestBody InventoryDto inventoryDto, // O corpo da requisição é mapeado para InventoryDto e validado.
            HttpServletRequest request // Permite acessar atributos da requisição, como o ID do usuário.
    ) {
        try {
            // Obtém o ID do usuário do atributo da requisição, que provavelmente foi definido por um filtro de autenticação.
            String userId = request.getAttribute("id_user").toString();
            // Chama o serviço para criar um novo inventário com os dados do DTO e o ID do usuário.
            Inventory newInventory = inventoryService.createInventory(inventoryDto, Long.parseLong(userId));

            // Retorna uma resposta HTTP 201 Created com o inventário recém-criado (convertido para DTO).
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(InventoryDto.from(newInventory));
        } catch (RuntimeException e) {
            // Em caso de qualquer RuntimeException, retorna uma resposta HTTP 400 Bad Request com a mensagem de erro.
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Error creating inventory: " + e.getMessage());
        }
    }

    // Mapeia requisições DELETE para "/inventory/{inventoryId}".
    @DeleteMapping("/{inventoryId}")
    // Exclui um inventário. Recebe o ID do inventário do caminho e o cabeçalho de autorização.
    public ResponseEntity<?> deleteInventory(@PathVariable Long inventoryId, @RequestHeader("Authorization") String authHeader) {
        // Extrai o ID do usuário do token de autorização usando o utilitário.
        String userId = authenticationUtils.getUserIdFromToken(authHeader);
        try {
            // Chama o serviço para excluir o inventário, verificando se o usuário tem permissão.
            inventoryService.deleteInventory(inventoryId, Long.parseLong(userId));
            // Retorna uma resposta HTTP 204 No Content se a exclusão for bem-sucedida.
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        } catch (RuntimeException e) {
            // Em caso de erro (por exemplo, inventário não encontrado ou sem permissão), retorna HTTP 400 Bad Request.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    // Mapeia requisições PUT para "/inventory/{inventoryId}".
    @PutMapping("/{inventoryId}")
    // Atualiza um inventário existente. Recebe o ID do inventário, o DTO com os dados de atualização e o cabeçalho de autorização.
    public ResponseEntity<?> updateInventory(
            @PathVariable("inventoryId") long inventoryId, // ID do inventário a ser atualizado.
            @RequestBody InventoryDto inventoryDto, // Novos dados do inventário no corpo da requisição.
            @RequestHeader("Authorization") String authHeader // Cabeçalho de autorização para verificar o usuário.
    ) {
        // Extrai o ID do usuário do token de autorização.
        String userId = authenticationUtils.getUserIdFromToken(authHeader);
        try {
            // Chama o serviço para atualizar o inventário.
            Inventory updatedInventory = inventoryService.updateInventory(inventoryId, inventoryDto, Long.parseLong(userId));
            // Retorna uma resposta HTTP 200 OK com o inventário atualizado (convertido para DTO).
            return ResponseEntity.status(HttpStatus.OK).body(InventoryDto.from(updatedInventory));
        } catch (RuntimeException e) {
            // Em caso de erro, retorna HTTP 400 Bad Request.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    // Mapeia requisições POST para "/inventory/add-single-item".
    @PostMapping("/add-single-item")
    // Adiciona um único item a um inventário.
    public ResponseEntity<?> addOneItem(
            @RequestParam("inventoryId") long inventoryId, // ID do inventário via parâmetro de requisição.
            @RequestBody Item item // O item a ser adicionado no corpo da requisição.
    ) {
        try {
            // Chama o serviço para adicionar o item ao inventário.
            inventoryService.addSingleItemToInventory(inventoryId, item);
            // Retorna uma resposta HTTP 201 Created com o item adicionado.
            return ResponseEntity.status(HttpStatus.CREATED).body(item);
        } catch (RuntimeException e) {
            // Em caso de erro, retorna HTTP 400 Bad Request com a mensagem de erro.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // Mapeia requisições POST para "/inventory/add-items", consumindo "multipart/form-data" (para upload de arquivos).
    @PostMapping(value = "/add-items", consumes = {"multipart/form-data"})
    // Adiciona múltiplos itens a um inventário a partir de um arquivo Excel.
    public ResponseEntity<?> addItemsToInventory(
            @RequestParam("inventoryId") long inventoryId, // ID do inventário via parâmetro de requisição.
            @RequestParam("file") MultipartFile file // O arquivo Excel enviado.
    ) {
        // Verifica se o arquivo está vazio.
        if (file.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("The uploaded file is empty.");
        }

        try {
            // Analisa o arquivo Excel e converte suas linhas em uma lista de objetos Item.
            List<Item> items = parseExcelFile(file);

            // Lista para armazenar os resultados do processamento de cada item (sucesso ou falha).
            List<ItemProcessingResult> results = new ArrayList<>();

            // Itera sobre cada item da lista.
            for (int i = 0; i < items.size(); i++) {
                Item item = items.get(i);
                // Chama o serviço para adicionar o item ao inventário, recebendo um resultado detalhado.
                ItemProcessingResult resultado = inventoryService.addItemToInventory(inventoryId, item, i + 2);
                results.add(resultado); // Adiciona o resultado à lista.
            }

            // Filtra os resultados para separar os itens que foram adicionados com sucesso.
            List<ItemProcessingResult> successes = results.stream()
                    .filter(ItemProcessingResult::success)
                    .collect(Collectors.toList());

            // Filtra os resultados para separar os itens que falharam.
            List<ItemProcessingResult> errors = results.stream()
                    .filter(r -> !r.success())
                    .collect(Collectors.toList());

            // Prepara um mapa para a resposta detalhada, contendo estatísticas e listas de sucesso/falha.
            Map<String, Object> response = new HashMap<>();
            response.put("totalProcessed\n", items.size());
            response.put("successes\n", successes.size());
            response.put("failures\n", errors.size());
            response.put("successfulItems\n", successes);
            response.put("failedItems\n", errors);

            // Define o status HTTP da resposta com base nos resultados do processamento.
            if (errors.isEmpty()) {
                // Se não houver erros, todos os itens foram adicionados com sucesso.
                response.put("status", "All items were added successfully!");
                return ResponseEntity
                        .status(HttpStatus.CREATED) // Retorna HTTP 201 Created.
                        .body(response);
            } else if (successes.isEmpty()) {
                // Se não houver sucessos, nenhum item foi adicionado.
                response.put("status", "No items were added.");
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST) // Retorna HTTP 400 Bad Request.
                        .body(response);
            } else {
                // Se houver sucessos e falhas, alguns itens foram adicionados, outros falharam.
                response.put("status", "Some items were added, others failed.");
                return ResponseEntity
                        .status(HttpStatus.PARTIAL_CONTENT) // Retorna HTTP 206 Partial Content.
                        .body(response);
            }
        } catch (Exception e) {
            // Em caso de qualquer outra exceção durante o processamento do arquivo, retorna HTTP 500 Internal Server Error.
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing file: " + e.getMessage());
        }
    }

    // Mapeia requisições DELETE para "/inventory/delete-item".
    @DeleteMapping("/delete-item")
    // Remove um item específico de um inventário.
    public ResponseEntity<?> deleteItemToInventory(
            @RequestParam long inventoryId, // ID do inventário.
            @RequestParam long itemId // ID do item a ser removido.
    ) {
        // Chama o serviço para remover o item do inventário. Retorna true se removido, false caso contrário.
        boolean removed = inventoryService.removeItemFromInventory(inventoryId, itemId);

        if (removed) {
            // Se o item foi removido, retorna HTTP 204 No Content.
            return ResponseEntity.noContent().build();
        } else {
            // Se o item não foi encontrado ou não pôde ser removido, retorna HTTP 404 Not Found.
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    // Mapeia requisições POST para "/inventory/validate-sheet", consumindo "multipart/form-data".
    @PostMapping(value = "/validate-sheet", consumes = {"multipart/form-data"})
    // Valida o conteúdo de uma planilha Excel sem adicioná-los ao inventário.
    public ResponseEntity<?> validateSheet(
            @RequestParam("file") MultipartFile file // O arquivo Excel a ser validado.
    ) {
        // Verifica se o arquivo está vazio.
        if (file.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("The uploaded file is empty.");
        }

        try {
            // Analisa o arquivo Excel e converte suas linhas em uma lista de objetos Item.
            List<Item> items = parseExcelFile(file);

            // Chama o método interno para validar os itens.
            ValidationResponse validationResult = validateItems(items);

            // Se houver erros de validação, retorna HTTP 400 Bad Request com os detalhes dos erros.
            if (validationResult.hasErrors()) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(validationResult);
            }

            // Se a planilha for válida, retorna HTTP 200 OK com uma mensagem de sucesso.
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body("The file is valid and ready for processing.");
        } catch (Exception e) {
            // Em caso de qualquer outra exceção durante o processamento do arquivo, retorna HTTP 500 Internal Server Error.
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing file: " + e.getMessage());
        }
    }

    // Mapeia requisições GET para "/inventory/user-inventories".
    @GetMapping("/user-inventories")
    // Obtém todos os inventários pertencentes ao usuário autenticado.
    public ResponseEntity<?> getInventories(
            HttpServletRequest request // Permite acessar o ID do usuário da requisição.
    ) {
        try {
            // Obtém o ID do usuário do atributo da requisição.
            String userId = request.getAttribute("id_user").toString();
            // Chama o serviço para obter a lista de inventários do usuário.
            List<Inventory> inventories = inventoryService.getUserInventories(Long.parseLong(userId));

            // Converte a lista de objetos Inventory para uma lista de InventoryDto.
            List<InventoryDto> inventoryDtos = inventories.stream()
                    .map(InventoryDto::from)
                    .collect(Collectors.toList());

            // Retorna uma resposta HTTP 200 OK com a lista de DTOs de inventário.
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(inventoryDtos);
        } catch (RuntimeException e) {
            // Em caso de erro, retorna HTTP 500 Internal Server Error com a mensagem de erro.
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving inventories: " + e.getMessage());
        }
    }


    // Mapeia requisições GET para "/inventory/inventory-items".
    @GetMapping("/inventory-items")
    // Obtém uma página de itens para um inventário específico.
    public ResponseEntity<?> getItemsByInventory(
            @RequestParam("inventoryId") long inventoryId, // ID do inventário.
            @RequestParam("page") int page, // Número da página (para paginação).
            @RequestParam("pageSize") int pageSize, // Tamanho da página (número de itens por página).
            HttpServletRequest request // Objeto de requisição HTTP (não usado diretamente aqui, mas pode ser para futuras validações).
    ) {
        try {
            // Chama o serviço para listar os itens do inventário com paginação.
            Page<Item> items = itemService.listInventoryItems(inventoryId, page, pageSize);

            // Converte a página de objetos Item para uma lista de ItemDto.
            List<ItemDto> itemDtos = items.stream()
                    .map(ItemDto::from)
                    .collect(Collectors.toList());

            // Retorna uma resposta HTTP 200 OK com a lista de DTOs de item.
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(itemDtos);
        } catch (RuntimeException e) {
            // Em caso de erro, retorna HTTP 500 Internal Server Error com a mensagem de erro.
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving inventories: " + e.getMessage());
        }
    }

    // Método privado para validar uma lista de itens.
    private ValidationResponse validateItems(List<Item> items) {
        // Lista para armazenar os erros de validação encontrados.
        List<ValidationError> errors = new ArrayList<>();

        // Itera sobre cada item na lista.
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            // Lista temporária para armazenar os erros específicos de cada item.
            List<String> errosItem = new ArrayList<>();

            // Verifica se o campo 'code' do item é nulo, vazio ou excede 30 caracteres.
            if (item.code() == null || item.code().trim().isEmpty()) {
                errosItem.add("Código não pode ser nulo ou vazio");
            } else if (item.code().length() > 30) {
                errosItem.add("Código não pode ter mais de 30 caracteres");
            }

            // Verifica se o campo 'price' do item é negativo.
            if (item.price() < 0) {
                errosItem.add("Preço não pode ser negativo");
            }

            // Se houver erros para o item atual, adiciona um ValidationError à lista principal de erros.
            // 'i + 2' é usado para indicar o número da linha no Excel (cabeçalho na linha 1, dados começam na linha 2).
            if (!errosItem.isEmpty()) {
                errors.add(new ValidationError(item.code(), i + 2, errosItem));
            }
        }

        // Retorna um objeto ValidationResponse indicando se há erros e a lista de erros.
        return new ValidationResponse(!errors.isEmpty(), errors);
    }

    // Método privado para analisar um arquivo Excel e extrair itens.
    private List<Item> parseExcelFile(MultipartFile file) throws IOException, ParseException {
        List<Item> items = new ArrayList<>(); // Lista para armazenar os itens extraídos do Excel.
        Workbook workbook; // Objeto Workbook para representar o arquivo Excel.

        try (InputStream inputStream = file.getInputStream()) {
            // Determina o tipo de arquivo Excel (.xlsx ou .xls) e cria o Workbook apropriado.
            if (file.getOriginalFilename().endsWith(".xlsx")) {
                workbook = new XSSFWorkbook(inputStream); // Para arquivos .xlsx (Office Open XML).
            } else if (file.getOriginalFilename().endsWith(".xls")) {
                workbook = new HSSFWorkbook(inputStream); // Para arquivos .xls (Binary Interchange File Format).
            } else {
                // Lança uma exceção se o formato do arquivo não for suportado.
                throw new IllegalArgumentException("Unsupported file format");
            }

            // Obtém a primeira planilha do arquivo Excel.
            Sheet sheet = workbook.getSheetAt(0);

            // Itera sobre cada linha da planilha.
            for (Row row : sheet) {
                // Ignora a primeira linha, que é o cabeçalho.
                if (row.getRowNum() == 0) {
                    continue;
                }

                // Extrai os valores das células da linha, seguindo um modelo específico de colunas.
                // O modelo "Seguindo o modelo exclusivo usado por Márcia." indica que a ordem das colunas é específica.
                String code = getCellValueAsString(row.getCell(1)); // Coluna B (índice 1) para o código.
                // String name = getCellValueAsString(row.getCell(1)); // Comentado, indica que 'name' não é extraído diretamente de uma coluna.
                String name = ""; // 'name' é inicializado como vazio.
                String description = getCellValueAsString(row.getCell(2)); // Coluna C (índice 2) para a descrição.
                String responsible = getCellValueAsString(row.getCell(3)); // Coluna D (índice 3) para o responsável.
                String price = getCellValueAsString(row.getCell(4)); // Coluna E (índice 4) para o preço.
                String locale = getCellValueAsString(row.getCell(5)); // Coluna F (índice 5) para o local.
                String observation = getCellValueAsString(row.getCell(6)); // Coluna G (índice 6) para a observação.

                // Converte o preço de String para long, multiplicando por 100 para lidar com centavos (assumindo que o preço no Excel está em unidade monetária).
                long priceParseLong = price != null && !price.isEmpty()
                        ? new BigDecimal(price).multiply(BigDecimal.valueOf(100)).longValue()
                        : 0L; // Se o preço for nulo ou vazio, define como 0.

                // Cria um novo objeto Item com os dados extraídos.
                Item item = new Item(code, name, description,
                        priceParseLong, locale, responsible);

                // Se houver uma observação, cria um objeto Observation e o adiciona ao item.
                if (observation != null && !observation.isEmpty()) {
                    Observation obs = new Observation(observation);
                    item.addObservation(obs);
                }

                // Adiciona o item à lista de itens.
                items.add(item);
            }
        }

        // Retorna a lista de itens parsados.
        return items;
    }

    // Método privado auxiliar para obter o valor de uma célula do Excel como String.
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return ""; // Se a célula for nula, retorna uma string vazia.

        // Usa um switch para lidar com diferentes tipos de célula do Excel.
        switch (cell.getCellType()) {
            case STRING:
                // Se for uma célula de texto, retorna o valor como String.
                return cell.getStringCellValue();

            case NUMERIC:
                // Se for uma célula numérica.
                if (DateUtil.isCellDateFormatted(cell)) {
                    // Se for formatada como data, retorna a representação String da data/hora.
                    return cell.getLocalDateTimeCellValue().toString();
                } else {
                    // Caso contrário, trata como número.
                    double numericValue = cell.getNumericCellValue();
                    // Verifica se o valor numérico é um inteiro (sem parte decimal).
                    if (numericValue == Math.floor(numericValue)) {
                        // Se for um inteiro, retorna como String de um long (para evitar ".0").
                        return String.valueOf((long) numericValue);
                    } else {
                        // Se tiver casas decimais, retorna como String do double.
                        return String.valueOf(numericValue);
                    }
                }

            case BLANK:
                // Se a célula estiver em branco, retorna uma string vazia.
                return "";

            default:
                // Para outros tipos de célula não tratados, retorna "UNKNOWN_TYPE".
                return "UNKNOWN_TYPE";
        }
    }
}