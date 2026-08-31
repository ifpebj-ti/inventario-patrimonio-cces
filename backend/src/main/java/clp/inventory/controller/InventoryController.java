package clp.inventory.controller;

import clp.inventory.dto.*;
import clp.inventory.model.Inventory;
import clp.inventory.model.Item;
import clp.inventory.model.Observation;
import clp.inventory.service.InventoryService;
import clp.inventory.service.ItemService;
import clp.inventory.utils.AuthenticationUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/inventory")
@CrossOrigin(origins = "*")
public class InventoryController {

    private final InventoryService inventoryService;
    private final ItemService itemService;
    private final AuthenticationUtils authenticationUtils;

    public InventoryController(InventoryService inventoryService,
                               ItemService itemService,
                               AuthenticationUtils authenticationUtils
    ) {
        this.inventoryService = inventoryService;
        this.itemService = itemService;
        this.authenticationUtils = authenticationUtils;
    }

    @PostMapping("/new-inventory")
    public ResponseEntity<?> createInventory(
            @Valid @RequestBody InventoryDto inventoryDto,
            HttpServletRequest request
    ) {
        try {
            String userId = request.getAttribute("id_user").toString();
            Inventory newInventory = inventoryService.createInventory(inventoryDto, Long.parseLong(userId));

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(InventoryDto.from(newInventory));
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Error creating inventory: " + e.getMessage());
        }
    }

    @DeleteMapping("/{inventoryId}")
    public ResponseEntity<?> deleteInventory(@PathVariable Long inventoryId, @RequestHeader("Authorization") String authHeader) {
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
            @RequestHeader("Authorization") String authHeader
    ) {
        String userId = authenticationUtils.getUserIdFromToken(authHeader);
        try {
            Inventory updatedInventory = inventoryService.updateInventory(inventoryId, inventoryDto, Long.parseLong(userId));
            return ResponseEntity.status(HttpStatus.OK).body(InventoryDto.from(updatedInventory));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @PostMapping("/add-single-item")
    public ResponseEntity<?> addOneItem(
            @RequestParam("inventoryId") long inventoryId,
            @RequestBody Item item
    ) {
        try {
            inventoryService.addSingleItemToInventory(inventoryId, item);
            return ResponseEntity.status(HttpStatus.CREATED).body(item);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping(value = "/add-items", consumes = {"multipart/form-data"})
    public ResponseEntity<?> addItemsToInventory(
            @RequestParam("inventoryId") long inventoryId,
            @RequestParam("file") MultipartFile file
    ) {
        if (file.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("The uploaded file is empty.");
        }

        try {
            List<Item> items = parseExcelFile(file);

            List<ItemProcessingResult> results = new ArrayList<>();

            for (int i = 0; i < items.size(); i++) {
                Item item = items.get(i);
                ItemProcessingResult resultado = inventoryService.addItemToInventory(inventoryId, item, i + 2);
                results.add(resultado);
            }

            List<ItemProcessingResult> successes = results.stream()
                    .filter(ItemProcessingResult::success)
                    .collect(Collectors.toList());

            List<ItemProcessingResult> errors = results.stream()
                    .filter(r -> !r.success())
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("totalProcessed\n", items.size());
            response.put("successes\n", successes.size());
            response.put("failures\n", errors.size());
            response.put("successfulItems\n", successes);
            response.put("failedItems\n", errors);

            if (errors.isEmpty()) {
                response.put("status", "All items were added successfully!");
                return ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(response);
            } else if (successes.isEmpty()) {
                response.put("status", "No items were added.");
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(response);
            } else {
                response.put("status", "Some items were added, others failed.");
                return ResponseEntity
                        .status(HttpStatus.PARTIAL_CONTENT)
                        .body(response);
            }
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing file: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete-item")
    public ResponseEntity<?> deleteItemToInventory(
            @RequestParam long inventoryId,
            @RequestParam long itemId
    ) {
        boolean removed = inventoryService.removeItemFromInventory(inventoryId, itemId);

        if (removed) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping(value = "/validate-sheet", consumes = {"multipart/form-data"})
    public ResponseEntity<?> validateSheet(
            @RequestParam("file") MultipartFile file
    ) {
        if (file.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("The uploaded file is empty.");
        }

        try {
            List<Item> items = parseExcelFile(file);

            ValidationResponse validationResult = validateItems(items);

            if (validationResult.hasErrors()) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(validationResult);
            }

            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body("The file is valid and ready for processing.");
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing file: " + e.getMessage());
        }
    }

    @GetMapping("/user-inventories")
    public ResponseEntity<?> getInventories(
            HttpServletRequest request
    ) {
        try {
            String userId = request.getAttribute("id_user").toString();
            List<Inventory> inventories = inventoryService.getUserInventories(Long.parseLong(userId));

            List<InventoryDto> inventoryDtos = inventories.stream()
                    .map(InventoryDto::from)
                    .collect(Collectors.toList());

            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(inventoryDtos);
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving inventories: " + e.getMessage());
        }
    }

    @GetMapping("/inventory-items")
    public ResponseEntity<?> getItemsByInventory(
            @RequestParam("inventoryId") long inventoryId,
            @RequestParam("page") int page,
            @RequestParam("pageSize") int pageSize,
            HttpServletRequest request
    ) {
        try {
            Page<Item> items = itemService.listInventoryItems(inventoryId, page, pageSize);

            List<ItemDto> itemDtos = items.stream()
                    .map(ItemDto::from)
                    .collect(Collectors.toList());

            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(itemDtos);
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
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
                long priceParseLong = price != null && !price.isEmpty()
                        ? new BigDecimal(price).multiply(BigDecimal.valueOf(100)).longValue()
                        : 0L;

                Item item = new Item(code, name, description,
                        priceParseLong, locale, responsible);

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
