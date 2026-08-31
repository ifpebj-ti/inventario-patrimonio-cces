package clp.inventory.service.generateSheet;

import clp.inventory.model.Item;
import clp.inventory.repository.ItemRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class SheetBuilderService {

    private final ItemRepository itemRepository;

    public SheetBuilderService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    public byte[] createAllItemsSheet(long inventoryId) throws IOException {
        int page = 0;
        int size = 100;
        int rowNum = 1;

        // SXSSF mantém apenas parte das linhas em memória (o restante vai para disco),
        // o que permite exportar inventários grandes sem estourar a heap.
        SXSSFWorkbook workbook = new SXSSFWorkbook();
        Sheet sheet = workbook.createSheet("Itens");
        sheet.setColumnWidth(0, 3000);
        sheet.setColumnWidth(1, 20000);
        sheet.setColumnWidth(2, 10000);
        sheet.setColumnWidth(3, 3000);
        sheet.setColumnWidth(4, 10000);
        sheet.setColumnWidth(5, 10000);

        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Código");
        header.createCell(1).setCellValue("Descrição");
        header.createCell(2).setCellValue("Carga Atual");
        header.createCell(3).setCellValue("Valor");
        header.createCell(4).setCellValue("Sala");
        header.createCell(5).setCellValue("Observações");

        Page<Item> itemsPage;

        do {
            Pageable pageable = PageRequest.of(page, size);
            itemsPage = itemRepository.findByInventory_Id(inventoryId, pageable);

            for (Item item : itemsPage) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(item.code());
                row.createCell(1).setCellValue(item.description());
                row.createCell(2).setCellValue(item.responsible());
                row.createCell(3).setCellValue(new BigDecimal(item.price()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).toString());
                row.createCell(4).setCellValue(item.locale());
                if (item.observations() == null || item.observations().isEmpty()) {
                    row.createCell(5).setCellValue("");
                } else{
                    row.createCell(5).setCellValue(item.observations().getFirst().content());
                }
            }

            page++;
        } while (!itemsPage.isLast());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } finally {
            // Obrigatório: remove os arquivos temporários criados em disco pelo SXSSF.
            workbook.dispose();
            workbook.close();
            outputStream.close();
        }
    }
}
