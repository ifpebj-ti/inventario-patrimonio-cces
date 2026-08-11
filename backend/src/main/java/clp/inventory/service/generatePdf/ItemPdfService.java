package clp.inventory.service.generatePdf;

// Importa o DTO PDFLabelData, que encapsula os dados para uma etiqueta PDF.
import clp.inventory.dto.PDFLabelData;
// Importa o modelo Item, que representa um item do inventário.
import clp.inventory.model.Item;
// Importa o repositório de itens para acessar dados de itens no banco.
import clp.inventory.repository.ItemRepository;
// Importa classes do Spring Data para paginação e ordenação de resultados.
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort; // Não usado diretamente, mas comum em paginação.
// Importa a anotação @Service para marcar a classe como um serviço Spring.
import org.springframework.stereotype.Service;

// Importa classes utilitárias para listas.
import java.util.ArrayList;
import java.util.List;

// Anotação que marca esta classe como um serviço Spring.
// Isso a torna um componente gerenciado pelo Spring, podendo ser injetado em outros lugares.
@Service
public class ItemPdfService {

    // Injeção de dependência do repositório de itens para operações de banco de dados.
    private final ItemRepository itemRepository;
    // Injeção de dependência do serviço gerador de QR Code.
    private final QRCodeGeneratorService qrCodeGeneratorService;
    // Instância do serviço construtor de PDF. Não é injetado via Spring, mas criado diretamente.
    private final PDFBuilderService pdfBuilderService;

    // Construtor da classe ItemPdfService.
    // O Spring injetará o ItemRepository e o QRCodeGeneratorService.
    public ItemPdfService(ItemRepository itemRepository, QRCodeGeneratorService qrCodeGeneratorService) {
        this.itemRepository = itemRepository;
        this.qrCodeGeneratorService = qrCodeGeneratorService;
        // Instancia diretamente o PDFBuilderService.
        this.pdfBuilderService = new PDFBuilderService();
    }

    /**
     * Gera um arquivo PDF com etiquetas para uma lista específica de IDs de itens.
     *
     * @param itemIds Uma lista de IDs de itens para os quais as etiquetas devem ser geradas.
     * @return Um array de bytes representando o conteúdo do arquivo PDF gerado.
     * @throws RuntimeException Se a lista de IDs estiver vazia ou nenhum item for encontrado para os IDs.
     */
    public byte[] generateItemPdf(List<Long> itemIds) {
        // Busca todos os itens no repositório cujos IDs estão na lista fornecida.
        List<Item> items = itemRepository.findAllById(itemIds);
        // Se a lista de itens estiver vazia (nenhum item encontrado para os IDs), lança uma exceção.
        if (items.isEmpty()) {
            throw new RuntimeException("There's no item to the IDs you passed");
        }
        // Lista para armazenar os dados das etiquetas (código, descrição, imagem do QR Code) que serão passados para o construtor de PDF.
        List<PDFLabelData> labels = new ArrayList<>();
        // Itera sobre cada item encontrado.
        for (Item item : items) {
            // Gera o QR Code para a string qrCode do item, com 150x150 pixels.
            byte[] qrCode = qrCodeGeneratorService.generateQRCode(item.qrCode(), 150, 150);
            // Cria um objeto PDFLabelData com o código do item, descrição e a imagem do QR Code, e adiciona à lista.
            labels.add(new PDFLabelData(item.code(), item.description(), qrCode));
        }

        // Chama o PDFBuilderService para criar o PDF final a partir dos dados das etiquetas e retorna o array de bytes.
        return pdfBuilderService.createLabelsPdf(labels);
    }

    /**
     * Gera um arquivo PDF com etiquetas para *todos* os itens pertencentes a um inventário específico.
     * Busca os itens de forma paginada para lidar com grandes volumes de dados.
     *
     * @param inventoryId O ID do inventário cujos itens devem ter etiquetas geradas.
     * @return Um array de bytes representando o conteúdo do arquivo PDF gerado.
     * @throws RuntimeException Se nenhum item for encontrado para o inventário especificado.
     */
    public byte[] generateAllItemsPdf(long inventoryId) {

        int page = 0;   // Começa da primeira página (índice 0).
        int size = 100; // Tamanho da página: busca 100 itens por vez.
        List<PDFLabelData> labels = new ArrayList<>(); // Lista para armazenar os dados de todas as etiquetas.

        Page<Item> itemsPage; // Variável para armazenar a página de itens retornada pelo repositório.

        do {
            // Cria um objeto Pageable para a requisição de paginação.
            Pageable pageable = PageRequest.of(page, size);
            // Busca uma página de itens associados ao inventário.
            itemsPage = itemRepository.findByInventory_Id(inventoryId, pageable);

            // Itera sobre os itens na página atual.
            for (Item item : itemsPage) {
                // Gera o QR Code para a string qrCode do item.
                byte[] qrCode = qrCodeGeneratorService.generateQRCode(item.qrCode(), 150, 150);
                // Adiciona os dados da etiqueta à lista.
                labels.add(new PDFLabelData(item.code(), item.description(), qrCode));
            }

            page++; // Incrementa o número da página para a próxima iteração.
        } while (!itemsPage.isLast()); // Continua o loop enquanto não for a última página de itens.

        // Após coletar todos os itens de todas as páginas, verifica se alguma etiqueta foi gerada.
        if (labels.isEmpty()) {
            throw new RuntimeException("Não há itens para o inventário com ID: " + inventoryId);
        }

        // Chama o PDFBuilderService para criar o PDF final a partir de todos os dados das etiquetas coletados.
        return pdfBuilderService.createLabelsPdf(labels);
    }
}