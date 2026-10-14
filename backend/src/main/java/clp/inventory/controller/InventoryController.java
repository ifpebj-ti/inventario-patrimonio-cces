package clp.inventory.controller;

import clp.inventory.dto.*;
import clp.inventory.model.Inventory;
import clp.inventory.model.Item;
import clp.inventory.model.Observation;
import clp.inventory.service.InventoryService;
import clp.inventory.service.ItemService;
import clp.inventory.utils.AuthenticationUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

  private final InventoryService inventoryService;
  private final ItemService itemService;
  private final AuthenticationUtils authenticationUtils;

  public InventoryController(
      InventoryService inventoryService,
      ItemService itemService,
      AuthenticationUtils authenticationUtils) {
    this.inventoryService = inventoryService;
    this.itemService = itemService;
    this.authenticationUtils = authenticationUtils;
  }

  @PostMapping("/new-inventory")
  public ResponseEntity<?> createInventory(
      @Valid @RequestBody InventoryDto inventoryDto, HttpServletRequest request) {
    try {
      String userId = request.getAttribute("id_user").toString();
      Inventory newInventory =
          inventoryService.createInventory(inventoryDto, Long.parseLong(userId));

      // Retorna o DTO enriquecido com status, setor e total de itens.
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(inventoryService.toDto(newInventory));
    } catch (RuntimeException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body("Error creating inventory: " + e.getMessage());
    }
  }

  @DeleteMapping("/{inventoryId}")
  public ResponseEntity<?> deleteInventory(
      @PathVariable Long inventoryId, @RequestHeader("Authorization") String authHeader) {
    String userId = authenticationUtils.getUserIdFromToken(authHeader);
    try {
      inventoryService.deleteInventory(inventoryId, Long.parseLong(userId));
      return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    } catch (RuntimeException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
  }

  @PutMapping("/{inventoryId}")
  public ResponseEntity<?> updateInventory(
      @PathVariable("inventoryId") long inventoryId,
      @RequestBody InventoryDto inventoryDto,
      @RequestHeader("Authorization") String authHeader) {
    String userId = authenticationUtils.getUserIdFromToken(authHeader);
    try {
      Inventory updatedInventory =
          inventoryService.updateInventory(inventoryId, inventoryDto, Long.parseLong(userId));
      return ResponseEntity.status(HttpStatus.OK)
          .body(inventoryService.toDto(updatedInventory));
    } catch (RuntimeException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
  }

  @PostMapping("/add-single-item")
  public ResponseEntity<?> addOneItem(
      @RequestParam("inventoryId") long inventoryId, @RequestBody Item item) {
    try {
      inventoryService.addSingleItemToInventory(inventoryId, item);
      return ResponseEntity.status(HttpStatus.CREATED).body(item);
    } catch (RuntimeException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
  }

  @PostMapping(
      value = "/add-items",
      consumes = {"multipart/form-data"})
  public ResponseEntity<?> addItemsToInventory(
      @RequestParam("inventoryId") long inventoryId, @RequestParam("file") MultipartFile file) {
    if (file.isEmpty()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Map.of("message", "O arquivo enviado está vazio."));
    }

    String filename = file.getOriginalFilename();
    if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(
              Map.of(
                  "message",
                  "Formato de arquivo inválido. O sistema aceita exclusivamente arquivos CSV (.csv)."));
    }

    try {
      List<Item> items = parseCsvFile(file);

      if (items.isEmpty()) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("message", "A planilha CSV não contém itens ou dados para importar."));
      }

      List<ItemProcessingResult> results = new ArrayList<>();

      for (int i = 0; i < items.size(); i++) {
        Item item = items.get(i);
        ItemProcessingResult resultado =
            inventoryService.addItemToInventory(inventoryId, item, i + 2);
        results.add(resultado);
      }

      List<ItemProcessingResult> successes =
          results.stream().filter(ItemProcessingResult::success).collect(Collectors.toList());

      List<ItemProcessingResult> errors =
          results.stream().filter(r -> !r.success()).collect(Collectors.toList());

      Map<String, Object> response = new HashMap<>();
      response.put("totalProcessed", items.size());
      response.put("totalProcessed\n", items.size());
      response.put("successes", successes.size());
      response.put("successes\n", successes.size());
      response.put("failures", errors.size());
      response.put("failures\n", errors.size());
      response.put("successfulItems", successes);
      response.put("successfulItems\n", successes);
      response.put("failedItems", errors);
      response.put("failedItems\n", errors);

      if (errors.isEmpty()) {
        response.put("status", "Todos os itens foram adicionados com sucesso!");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
      } else if (successes.isEmpty()) {
        response.put("status", "Nenhum item foi adicionado.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
      } else {
        response.put("status", "Alguns itens foram adicionados, outros falharam.");
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).body(response);
      }
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("message", "Erro ao processar o arquivo CSV: " + e.getMessage()));
    }
  }

  @DeleteMapping("/delete-item")
  public ResponseEntity<?> deleteItemToInventory(
      @RequestParam long inventoryId, @RequestParam long itemId) {
    boolean removed = inventoryService.removeItemFromInventory(inventoryId, itemId);

    if (removed) {
      return ResponseEntity.noContent().build();
    } else {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
  }

  @DeleteMapping("/clear-items")
  public ResponseEntity<?> clearInventoryItems(@RequestParam long inventoryId) {
    boolean cleared = inventoryService.clearInventoryItems(inventoryId);

    if (cleared) {
      return ResponseEntity.noContent().build();
    } else {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
  }

  @PostMapping(
      value = "/validate-sheet",
      consumes = {"multipart/form-data"})
  public ResponseEntity<?> validateSheet(@RequestParam("file") MultipartFile file) {
    if (file.isEmpty()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Map.of("message", "O arquivo enviado está vazio."));
    }

    String filename = file.getOriginalFilename();
    if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(
              Map.of(
                  "message",
                  "Formato de arquivo inválido. O sistema aceita exclusivamente arquivos CSV (.csv)."));
    }

    try {
      List<Item> items = parseCsvFile(file);

      if (items.isEmpty()) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("message", "A planilha CSV não contém itens ou dados para validação."));
      }

      ValidationResponse validationResult = validateItems(items);

      if (validationResult.hasErrors()) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(validationResult);
      }

      return ResponseEntity.status(HttpStatus.OK)
          .body(Map.of("message", "O arquivo é válido e está pronto para o processamento."));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Map.of("message", e.getMessage(), "hasErrors", true));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("message", "Erro ao processar o arquivo CSV: " + e.getMessage()));
    }
  }

  @GetMapping("/user-inventories")
  public ResponseEntity<?> getInventories(HttpServletRequest request) {
    try {
      String userId = request.getAttribute("id_user").toString();
      List<Inventory> inventories = inventoryService.getUserInventories(Long.parseLong(userId));

      // A listagem devolve os dados necessários para filtros, ordenação e indicadores sem expor a entidade.
      List<InventoryDto> inventoryDtos =
          inventories.stream()
              .map(inventoryService::toDto)
              .collect(Collectors.toList());

      return ResponseEntity.status(HttpStatus.OK).body(inventoryDtos);
    } catch (RuntimeException e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Error retrieving inventories: " + e.getMessage());
    }
  }

  @GetMapping("/inventory-items")
  public ResponseEntity<?> getItemsByInventory(
      @RequestParam("inventoryId") long inventoryId,
      @RequestParam("page") int page,
      @RequestParam("pageSize") int pageSize,
      HttpServletRequest request) {
    try {
      Page<Item> items = itemService.listInventoryItems(inventoryId, page, pageSize);

      List<ItemDto> itemDtos = items.stream().map(ItemDto::from).collect(Collectors.toList());

      return ResponseEntity.status(HttpStatus.OK).body(itemDtos);
    } catch (RuntimeException e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Error retrieving inventories: " + e.getMessage());
    }
  }

  private ValidationResponse validateItems(List<Item> items) {
    List<ValidationError> errors = new ArrayList<>();

    for (int i = 0; i < items.size(); i++) {
      Item item = items.get(i);
      List<String> errosItem = new ArrayList<>();

      if (item.code() == null || item.code().trim().isEmpty()) {
        errosItem.add("Código não pode ser nulo ou vazio");
      } else if (item.code().length() > 30) {
        errosItem.add("Código não pode ter mais de 30 caracteres");
      }

      if (item.price() < 0) {
        errosItem.add("Preço não pode ser negativo");
      }

      if (!errosItem.isEmpty()) {
        // i + 2 = número da linha na planilha (linha 1 é o cabeçalho, dados começam na 2).
        errors.add(new ValidationError(item.code(), i + 2, errosItem));
      }
    }

    return new ValidationResponse(!errors.isEmpty(), errors);
  }

  private List<Item> parseCsvFile(MultipartFile file) throws IOException {
    List<Item> items = new ArrayList<>();

    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

      String firstLine = reader.readLine();
      if (firstLine == null || firstLine.trim().isEmpty()) {
        throw new IllegalArgumentException(
            "O arquivo enviado está vazio. Baixe o modelo oficial de planilha.");
      }

      if (firstLine.startsWith("\uFEFF")) {
        firstLine = firstLine.substring(1);
      }

      char delimiter = ';';
      if (!firstLine.contains(";") && firstLine.contains(",")) {
        delimiter = ',';
      } else if (!firstLine.contains(";") && !firstLine.contains(",") && firstLine.contains("\t")) {
        delimiter = '\t';
      }

      List<String> headers = parseCsvLine(firstLine, delimiter);
      if (headers.isEmpty()) {
        throw new IllegalArgumentException(
            "O arquivo enviado não possui cabeçalho válido. Baixe o modelo oficial.");
      }

      int colCode = -1;
      int colDesc = -1;
      int colResp = -1;
      int colPrice = -1;
      int colLocale = -1;
      int colObs = -1;

      for (int i = 0; i < headers.size(); i++) {
        String h = normalizeHeader(headers.get(i));
        if (colCode == -1
            && (h.equals("codigo")
                || h.equals("cod")
                || h.contains("codigo")
                || h.contains("patrimonio")
                || h.contains("etiqueta")
                || h.contains("tombamento"))) {
          colCode = i;
        } else if (colDesc == -1
            && (h.contains("desc")
                || h.contains("espec")
                || h.contains("item")
                || h.contains("nome"))) {
          colDesc = i;
        } else if (colResp == -1
            && (h.contains("resp") || h.contains("carga") || h.contains("detentor"))) {
          colResp = i;
        } else if (colPrice == -1
            && (h.contains("val")
                || h.contains("prec")
                || h.contains("price")
                || h.contains("custo"))) {
          colPrice = i;
        } else if (colLocale == -1
            && (h.contains("sala")
                || h.contains("local")
                || h.contains("setor")
                || h.contains("ambiente"))) {
          colLocale = i;
        } else if (colObs == -1 && (h.contains("obs") || h.contains("nota"))) {
          colObs = i;
        }
      }

      // Validação estrita: A coluna de Código é OBRIGATÓRIA no cabeçalho
      if (colCode == -1) {
        throw new IllegalArgumentException(
            "O arquivo enviado não possui as colunas obrigatórias esperadas (coluna 'Código' não encontrada). Baixe o modelo oficial.");
      }

      String line;
      while ((line = reader.readLine()) != null) {
        if (line.trim().isEmpty()) {
          continue;
        }
        List<String> tokens = parseCsvLine(line, delimiter);
        Item item =
            createItemFromTokens(tokens, colCode, colDesc, colResp, colPrice, colLocale, colObs);
        if (item != null) {
          items.add(item);
        }
      }

      if (items.isEmpty()) {
        throw new IllegalArgumentException(
            "A planilha não contém itens ou dados para importar após o cabeçalho.");
      }
    }

    return items;
  }

  private Item createItemFromTokens(
      List<String> tokens,
      int colCode,
      int colDesc,
      int colResp,
      int colPrice,
      int colLocale,
      int colObs) {
    String code = getSafeToken(tokens, colCode);
    String description = getSafeToken(tokens, colDesc);
    String responsible = getSafeToken(tokens, colResp);
    String priceStr = getSafeToken(tokens, colPrice);
    String locale = getSafeToken(tokens, colLocale);
    String observation = getSafeToken(tokens, colObs);

    long priceCentavos = parsePriceToCentavos(priceStr);

    Item item = new Item(code, "", description, priceCentavos, locale, responsible);
    if (!observation.isEmpty()) {
      item.addObservation(new Observation(observation));
    }
    return item;
  }

  private String getSafeToken(List<String> tokens, int index) {
    if (index >= 0 && index < tokens.size()) {
      return tokens.get(index).trim();
    }
    return "";
  }

  private long parsePriceToCentavos(String priceStr) {
    if (priceStr == null || priceStr.trim().isEmpty()) {
      return 0L;
    }
    try {
      String clean = priceStr.replaceAll("[^0-9,.]", "").trim();
      if (clean.isEmpty()) {
        return 0L;
      }
      if (clean.contains(",") && clean.contains(".")) {
        clean = clean.replace(".", "").replace(",", ".");
      } else if (clean.contains(",")) {
        clean = clean.replace(",", ".");
      }
      return new BigDecimal(clean).multiply(BigDecimal.valueOf(100)).longValue();
    } catch (Exception e) {
      return 0L;
    }
  }

  private String normalizeHeader(String header) {
    if (header == null) {
      return "";
    }
    String normalized = Normalizer.normalize(header, Normalizer.Form.NFD);
    return normalized.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
  }

  private List<String> parseCsvLine(String line, char delimiter) {
    List<String> result = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;

    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          current.append('"');
          i++;
        } else {
          inQuotes = !inQuotes;
        }
      } else if (c == delimiter && !inQuotes) {
        result.add(current.toString().trim());
        current.setLength(0);
      } else {
        current.append(c);
      }
    }
    result.add(current.toString().trim());
    return result;
  }

  private List<Item> parseExcelFile(MultipartFile file) throws IOException, ParseException {
    List<Item> items = new ArrayList<>();
    Workbook workbook;

    try (InputStream inputStream = file.getInputStream()) {
      if (file.getOriginalFilename().endsWith(".xlsx")) {
        workbook = new XSSFWorkbook(inputStream);
      } else if (file.getOriginalFilename().endsWith(".xls")) {
        workbook = new HSSFWorkbook(inputStream);
      } else {
        throw new IllegalArgumentException("Unsupported file format");
      }

      Sheet sheet = workbook.getSheetAt(0);

      for (Row row : sheet) {
        if (row.getRowNum() == 0) {
          continue;
        }

        // Layout fixo da planilha usada pelo setor: A é ignorada, B=código, C=descrição,
        // D=responsável, E=preço, F=local, G=observação. Alterar a ordem quebra a importação.
        String code = getCellValueAsString(row.getCell(1));
        String name = "";
        String description = getCellValueAsString(row.getCell(2));
        String responsible = getCellValueAsString(row.getCell(3));
        String price = getCellValueAsString(row.getCell(4));
        String locale = getCellValueAsString(row.getCell(5));
        String observation = getCellValueAsString(row.getCell(6));

        // A planilha traz o valor em reais; a entidade persiste em centavos.
        long priceParseLong =
            price != null && !price.isEmpty()
                ? new BigDecimal(price).multiply(BigDecimal.valueOf(100)).longValue()
                : 0L;

        Item item = new Item(code, name, description, priceParseLong, locale, responsible);

        if (observation != null && !observation.isEmpty()) {
          Observation obs = new Observation(observation);
          item.addObservation(obs);
        }

        items.add(item);
      }
    }

    return items;
  }

  private String getCellValueAsString(Cell cell) {
    if (cell == null) return "";

    switch (cell.getCellType()) {
      case STRING:
        return cell.getStringCellValue();

      case NUMERIC:
        if (DateUtil.isCellDateFormatted(cell)) {
          return cell.getLocalDateTimeCellValue().toString();
        } else {
          double numericValue = cell.getNumericCellValue();
          // O POI lê todo número como double; sem o cast, um código inteiro viraria "123.0".
          if (numericValue == Math.floor(numericValue)) {
            return String.valueOf((long) numericValue);
          } else {
            return String.valueOf(numericValue);
          }
        }

      case BLANK:
        return "";

      default:
        return "UNKNOWN_TYPE";
    }
  }
}
