package clp.inventory.controller;

import clp.inventory.dto.*;
import clp.inventory.dto.doc.ImportItemsResponse;
import clp.inventory.dto.doc.InventoryDetailsResponse;
import clp.inventory.dto.doc.MessageResponse;
import clp.inventory.dto.doc.ValidateSheetResponse;
import clp.inventory.model.Inventory;
import clp.inventory.model.Item;
import clp.inventory.model.Observation;
import clp.inventory.service.InventoryService;
import clp.inventory.service.ItemService;
import clp.inventory.utils.AuthenticationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.*;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Tag(
    name = "Inventários",
    description = "Inventários, importação de planilha CSV e listagem de itens.")
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

  @Operation(
      summary = "Criar inventário",
      description =
          "Exige administrador ou gestor (MANAGE_SECTOR) do setor informado. O nome é obrigatório e único por usuário; plannedEndDate usa yyyy-MM-dd e não pode ser anterior a hoje; sectorId é obrigatório.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Inventário criado.",
        content = @Content(schema = @Schema(implementation = InventoryDto.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Dados inválidos (mensagem no corpo).",
        content = @Content)
  })
  @PostMapping("/new-inventory")
  public ResponseEntity<?> createInventory(
      @Valid @RequestBody InventoryDto inventoryDto, HttpServletRequest request) {
    try {
      String userId = request.getAttribute("id_user").toString();
      Inventory newInventory =
          inventoryService.createInventory(inventoryDto, Long.parseLong(userId));

      // Retorna o DTO enriquecido com status, setor e total de itens.
      return ResponseEntity.status(HttpStatus.CREATED).body(inventoryService.toDto(newInventory));
    } catch (ResponseStatusException e) {
      throw e;
    } catch (RuntimeException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body("Error creating inventory: " + e.getMessage());
    }
  }

  @Operation(
      summary = "Buscar inventário por id",
      description =
          "Vale para administrador e para quem enxerga o setor. Inclui canFinalize, que indica se o usuário pode finalizar/editar.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Inventário.",
        content = @Content(schema = @Schema(implementation = InventoryDetailsResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Inventário não encontrado.",
        content = @Content)
  })
  @GetMapping("/{inventoryId}")
  public ResponseEntity<?> getInventory(
      @PathVariable long inventoryId,
      @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader) {
    long userId = Long.parseLong(authenticationUtils.getUserIdFromToken(authHeader));
    Inventory inventory = inventoryService.getAccessibleInventory(inventoryId, userId);
    return ResponseEntity.ok(
        Map.of(
            "inventory", inventoryService.toDto(inventory),
            "canFinalize", inventoryService.canFinalize(inventory, userId)));
  }

  @Operation(
      summary = "Finalizar inventário",
      description =
          "Exige administrador ou gestor (MANAGE_SECTOR) do setor. Depois de finalizado, não é possível alterar itens nem validar.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Inventário finalizado.",
        content = @Content(schema = @Schema(implementation = InventoryDto.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Inventário não encontrado.",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "O inventário já foi finalizado.",
        content = @Content)
  })
  @PostMapping("/{inventoryId}/finalize")
  public ResponseEntity<InventoryDto> finishInventory(
      @PathVariable long inventoryId,
      @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader) {
    long userId = Long.parseLong(authenticationUtils.getUserIdFromToken(authHeader));
    return ResponseEntity.ok(
        inventoryService.toDto(inventoryService.finishInventory(inventoryId, userId)));
  }

  @Operation(
      summary = "Excluir inventário",
      description = "Exige administrador ou gestor (MANAGE_SECTOR) do setor.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Inventário excluído.", content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "Inventário não encontrado.",
        content = @Content)
  })
  @DeleteMapping("/{inventoryId}")
  public ResponseEntity<?> deleteInventory(
      @PathVariable Long inventoryId,
      @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader) {
    String userId = authenticationUtils.getUserIdFromToken(authHeader);
    try {
      inventoryService.deleteInventory(inventoryId, Long.parseLong(userId));
      return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    } catch (ResponseStatusException e) {
      throw e;
    } catch (RuntimeException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
  }

  @Operation(
      summary = "Editar inventário",
      description =
          "Altera nome, descrição, prazo e, opcionalmente, o setor (para outro setor ativo em que o usuário também gerencia). Exige administrador ou gestor do setor.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Inventário atualizado.",
        content = @Content(schema = @Schema(implementation = InventoryDto.class))),
    @ApiResponse(responseCode = "400", description = "Dados inválidos.", content = @Content),
    @ApiResponse(responseCode = "403", description = "Sem permissão para editar este inventário.")
  })
  @PutMapping("/{inventoryId}")
  public ResponseEntity<?> updateInventory(
      @PathVariable("inventoryId") long inventoryId,
      @RequestBody InventoryDto inventoryDto,
      @Parameter(hidden = true) @RequestHeader("Authorization") String authHeader) {
    String userId = authenticationUtils.getUserIdFromToken(authHeader);
    try {
      Inventory updatedInventory =
          inventoryService.updateInventory(inventoryId, inventoryDto, Long.parseLong(userId));
      return ResponseEntity.status(HttpStatus.OK).body(inventoryService.toDto(updatedInventory));
    } catch (ResponseStatusException e) {
      throw e;
    } catch (RuntimeException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
  }

  @Operation(
      summary = "Adicionar item avulso",
      description =
          "Inclui um item no inventário. Exige administrador ou gestor do setor, e o inventário não pode estar finalizado. A sala (locale) é encontrada ou criada no setor. Só code, name, description, price, locale e responsible são lidos; id, qrCode e demais campos do corpo são ignorados (o item é sempre novo).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Item adicionado (devolve o item criado, com id e QR Code).",
        content = @Content(schema = @Schema(implementation = ItemDto.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Dados inválidos (mensagem no corpo).",
        content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "Inventário não encontrado.",
        content = @Content),
    @ApiResponse(responseCode = "409", description = "Inventário finalizado.", content = @Content)
  })
  @PostMapping("/add-single-item")
  public ResponseEntity<?> addOneItem(
      @Parameter(description = "Id do inventário.") @RequestParam("inventoryId") long inventoryId,
      @RequestBody NewItemRequest newItem,
      HttpServletRequest request) {
    try {
      Item item =
          inventoryService.addSingleItemToInventory(inventoryId, newItem, actingUserId(request));
      return ResponseEntity.status(HttpStatus.CREATED).body(ItemDto.from(item));
    } catch (ResponseStatusException e) {
      throw e;
    } catch (RuntimeException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }
  }

  @Operation(
      summary = "Importar planilha CSV de itens",
      description =
          "Importa os itens de uma planilha CSV para o inventário. Planilha CSV (UTF-8) com cabeçalho. Delimitador ponto e vírgula (também aceita vírgula ou tab). Colunas reconhecidas pelo nome do cabeçalho: código (obrigatória; também 'patrimônio', 'etiqueta', 'tombamento'), descrição, responsável, valor/preço (em reais, convertido para centavos), sala/local e observação. Exige administrador ou gestor do setor; o inventário não pode estar finalizado nem já ter itens (para reimportar, limpe com DELETE /inventory/clear-items). Salas citadas que não existem no setor são criadas. Use antes POST /inventory/validate-sheet para conferir. Respostas: 201 se todos entraram, 206 se parte falhou, 400 se nenhum entrou. Atenção: os campos totalProcessed, successes, failures, successfulItems e failedItems têm uma quebra de linha no fim do nome (ex.: \"totalProcessed\\n\"), e é assim mesmo que a API os devolve.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Todos os itens foram adicionados.",
        content = @Content(schema = @Schema(implementation = ImportItemsResponse.class))),
    @ApiResponse(
        responseCode = "206",
        description = "Importação parcial: alguns itens foram adicionados, outros falharam.",
        content = @Content(schema = @Schema(implementation = ImportItemsResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Arquivo vazio, não CSV, sem itens ou nenhum item adicionado.",
        content = @Content(schema = @Schema(implementation = MessageResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Inventário não encontrado.",
        content = @Content),
    @ApiResponse(
        responseCode = "409",
        description = "Inventário finalizado ou já com itens importados.",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Erro ao processar o arquivo.",
        content = @Content(schema = @Schema(implementation = MessageResponse.class)))
  })
  @PostMapping(
      value = "/add-items",
      consumes = {"multipart/form-data"})
  public ResponseEntity<?> addItemsToInventory(
      @Parameter(description = "Id do inventário.") @RequestParam("inventoryId") long inventoryId,
      @Parameter(description = "Planilha .csv.") @RequestParam("file") MultipartFile file,
      HttpServletRequest request) {
    // Só gestor/admin do setor importa, o inventário não pode estar finalizado nem ter itens.
    inventoryService.requireImportAllowed(inventoryId, actingUserId(request));
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

      long roomsBefore = inventoryService.roomCountOf(inventoryId);
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
      response.put("totalProcessed\n", items.size());
      response.put("successes\n", successes.size());
      response.put("failures\n", errors.size());
      response.put("successfulItems\n", successes);
      response.put("failedItems\n", errors);
      response.put("createdRooms", inventoryService.roomCountOf(inventoryId) - roomsBefore);

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

  @Operation(
      summary = "Remover item do inventário",
      description = "Exige administrador ou gestor do setor, com o inventário em andamento.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Item removido.", content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "Item não encontrado no inventário.",
        content = @Content),
    @ApiResponse(responseCode = "409", description = "Inventário finalizado.", content = @Content)
  })
  @DeleteMapping("/delete-item")
  public ResponseEntity<?> deleteItemToInventory(
      @Parameter(description = "Id do inventário.") @RequestParam long inventoryId,
      @Parameter(description = "Id do item.") @RequestParam long itemId,
      HttpServletRequest request) {
    boolean removed =
        inventoryService.removeItemFromInventory(inventoryId, itemId, actingUserId(request));

    if (removed) {
      return ResponseEntity.noContent().build();
    } else {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
  }

  @Operation(
      summary = "Remover todos os itens do inventário",
      description =
          "Exige administrador ou gestor do setor, com o inventário em andamento. Permite importar outra planilha depois.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Itens removidos.", content = @Content),
    @ApiResponse(
        responseCode = "404",
        description = "Inventário não encontrado.",
        content = @Content),
    @ApiResponse(responseCode = "409", description = "Inventário finalizado.", content = @Content)
  })
  @DeleteMapping("/clear-items")
  public ResponseEntity<?> clearInventoryItems(
      @Parameter(description = "Id do inventário.") @RequestParam long inventoryId,
      HttpServletRequest request) {
    boolean cleared = inventoryService.clearInventoryItems(inventoryId, actingUserId(request));

    if (cleared) {
      return ResponseEntity.noContent().build();
    } else {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
  }

  @Operation(
      summary = "Validar planilha CSV antes de importar",
      description =
          "Confere a planilha sem gravar nada: código obrigatório (até 30 caracteres) e preço não negativo, linha a linha. Planilha CSV (UTF-8) com cabeçalho. Delimitador ponto e vírgula (também aceita vírgula ou tab). Colunas reconhecidas pelo nome do cabeçalho: código (obrigatória; também 'patrimônio', 'etiqueta', 'tombamento'), descrição, responsável, valor/preço (em reais, convertido para centavos), sala/local e observação. Exige administrador ou gestor de algum setor. Informando inventoryId, devolve também quais salas seriam criadas.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Planilha válida.",
        content = @Content(schema = @Schema(implementation = ValidateSheetResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description =
            "Arquivo vazio, não CSV, ou com linhas inválidas (ValidationResponse, com erros por linha).",
        content = @Content(schema = @Schema(implementation = ValidationResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Inventário não encontrado.",
        content = @Content),
    @ApiResponse(
        responseCode = "500",
        description = "Erro ao processar o arquivo.",
        content = @Content(schema = @Schema(implementation = MessageResponse.class)))
  })
  @PostMapping(
      value = "/validate-sheet",
      consumes = {"multipart/form-data"})
  public ResponseEntity<?> validateSheet(
      @Parameter(description = "Planilha .csv.") @RequestParam("file") MultipartFile file,
      @Parameter(description = "Inventário de destino; se informado, devolve a prévia das salas.")
          @RequestParam(value = "inventoryId", required = false)
          Long inventoryId,
      HttpServletRequest request) {
    inventoryService.requireCanImport(actingUserId(request));
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

      Map<String, Object> body = new HashMap<>();
      body.put("message", "O arquivo é válido e está pronto para o processamento.");
      if (inventoryId != null) {
        RoomsPreviewDto rooms =
            inventoryService.previewRooms(inventoryId, items, actingUserId(request));
        body.put("newRooms", rooms.newRooms());
        body.put("existingRooms", rooms.existingRooms());
      }
      return ResponseEntity.status(HttpStatus.OK).body(body);
    } catch (ResponseStatusException e) {
      throw e;
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(Map.of("message", e.getMessage(), "hasErrors", true));
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("message", "Erro ao processar o arquivo CSV: " + e.getMessage()));
    }
  }

  @Operation(
      summary = "Listar inventários do usuário",
      description = "O administrador vê todos; os demais, os dos setores que enxergam.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Inventários.",
        content =
            @Content(array = @ArraySchema(schema = @Schema(implementation = InventoryDto.class))))
  })
  @GetMapping("/user-inventories")
  public ResponseEntity<?> getInventories(HttpServletRequest request) {
    try {
      String userId = request.getAttribute("id_user").toString();
      List<Inventory> inventories = inventoryService.getUserInventories(Long.parseLong(userId));

      // A listagem devolve os dados necessários para filtros, ordenação e indicadores sem expor a
      // entidade.
      List<InventoryDto> inventoryDtos =
          inventories.stream().map(inventoryService::toDto).collect(Collectors.toList());

      return ResponseEntity.status(HttpStatus.OK).body(inventoryDtos);
    } catch (RuntimeException e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Error retrieving inventories: " + e.getMessage());
    }
  }

  @Operation(
      summary = "Listar inventários de um setor",
      description = "Exige poder consultar o setor.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Inventários do setor.",
        content =
            @Content(array = @ArraySchema(schema = @Schema(implementation = InventoryDto.class))))
  })
  @GetMapping("/sector-inventories")
  public ResponseEntity<?> getSectorInventories(
      @Parameter(description = "Id do setor.") @RequestParam("sectorId") long sectorId,
      HttpServletRequest request) {
    List<Inventory> inventories =
        inventoryService.getSectorInventories(sectorId, actingUserId(request));

    List<InventoryDto> inventoryDtos =
        inventories.stream().map(inventoryService::toDto).collect(Collectors.toList());

    return ResponseEntity.status(HttpStatus.OK).body(inventoryDtos);
  }

  @Operation(
      summary = "Listar itens do inventário (paginado)",
      description = "Itens ordenados por código. page começa em 0.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Itens da página.",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = ItemDto.class)))),
    @ApiResponse(
        responseCode = "404",
        description = "Inventário não encontrado.",
        content = @Content)
  })
  @GetMapping("/inventory-items")
  public ResponseEntity<?> getItemsByInventory(
      @Parameter(description = "Id do inventário.") @RequestParam("inventoryId") long inventoryId,
      @Parameter(description = "Página, a partir de 0.", example = "0") @RequestParam("page")
          int page,
      @Parameter(description = "Itens por página.", example = "20") @RequestParam("pageSize")
          int pageSize,
      HttpServletRequest request) {
    inventoryService.requireInventoryView(inventoryId, actingUserId(request));
    try {
      Page<Item> items = itemService.listInventoryItems(inventoryId, page, pageSize);

      List<ItemDto> itemDtos = items.stream().map(ItemDto::from).collect(Collectors.toList());

      return ResponseEntity.status(HttpStatus.OK).body(itemDtos);
    } catch (RuntimeException e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Error retrieving inventories: " + e.getMessage());
    }
  }

  private long actingUserId(HttpServletRequest request) {
    return Long.parseLong(request.getAttribute("id_user").toString());
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
}
