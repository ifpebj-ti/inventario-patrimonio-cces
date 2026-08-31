package clp.inventory.service.generatePdf;

import clp.inventory.dto.PDFLabelData;
import clp.inventory.model.Item;
import clp.inventory.repository.ItemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ItemPdfService {

    private final ItemRepository itemRepository;
    private final QRCodeGeneratorService qrCodeGeneratorService;
    private final PDFBuilderService pdfBuilderService;

    public ItemPdfService(ItemRepository itemRepository, QRCodeGeneratorService qrCodeGeneratorService) {
        this.itemRepository = itemRepository;
        this.qrCodeGeneratorService = qrCodeGeneratorService;
        this.pdfBuilderService = new PDFBuilderService();
    }

    public byte[] generateItemPdf(List<Long> itemIds) {
        List<Item> items = itemRepository.findAllById(itemIds);
        if (items.isEmpty()) {
            throw new RuntimeException("There's no item to the IDs you passed");
        }
        List<PDFLabelData> labels = new ArrayList<>();
        for (Item item : items) {
            byte[] qrCode = qrCodeGeneratorService.generateQRCode(item.qrCode(), 150, 150);
            labels.add(new PDFLabelData(item.code(), item.description(), qrCode));
        }

        return pdfBuilderService.createLabelsPdf(labels);
    }

    public byte[] generateAllItemsPdf(long inventoryId) {

        int page = 0;
        int size = 100;
        List<PDFLabelData> labels = new ArrayList<>();

        Page<Item> itemsPage;

        do {
            Pageable pageable = PageRequest.of(page, size);
            itemsPage = itemRepository.findByInventory_Id(inventoryId, pageable);

            for (Item item : itemsPage) {
                byte[] qrCode = qrCodeGeneratorService.generateQRCode(item.qrCode(), 150, 150);
                labels.add(new PDFLabelData(item.code(), item.description(), qrCode));
            }

            page++;
        } while (!itemsPage.isLast());

        if (labels.isEmpty()) {
            throw new RuntimeException("Não há itens para o inventário com ID: " + inventoryId);
        }

        return pdfBuilderService.createLabelsPdf(labels);
    }
}
