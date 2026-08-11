package clp.inventory.service.generateSheet;

// Importa o modelo Item, que representa um item do inventário.
import clp.inventory.model.Item;
// Importa o repositório de itens para acessar dados de itens no banco.
import clp.inventory.repository.ItemRepository;
// Importa classes do Apache POI para trabalhar com planilhas Excel (especificamente SXSSFWorkbook para grandes arquivos).
import org.apache.poi.ss.usermodel.Row;     // Representa uma linha em uma planilha.
import org.apache.poi.ss.usermodel.Sheet;   // Representa uma planilha.
import org.apache.poi.xssf.streaming.SXSSFWorkbook; // Workbook de streaming para escrita eficiente de grandes arquivos XLSX.
// Importa classes do Spring Data para paginação de resultados.
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
// Importa a anotação @Service para marcar a classe como um serviço Spring.
import org.springframework.stereotype.Service;

// Importa classes para operações de I/O e manipulação de números decimais.
import java.io.ByteArrayOutputStream; // Para escrever a planilha na memória como um array de bytes.
import java.io.IOException;           // Para lidar com exceções de I/O.
import java.math.BigDecimal;          // Para operações com números decimais de alta precisão.
import java.math.RoundingMode;        // Para definir o modo de arredondamento.

// Anotação que marca esta classe como um serviço Spring.
// Isso a torna um componente gerenciado pelo Spring, podendo ser injetado em outros lugares.
@Service
public class SheetBuilderService {

    // Injeção de dependência do repositório de itens.
    private final ItemRepository itemRepository;

    // Construtor da classe SheetBuilderService.
    // O Spring injetará o ItemRepository automaticamente.
    public SheetBuilderService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    /**
     * Cria uma planilha Excel (formato XLSX) contendo todos os itens de um inventário específico.
     * Os itens são buscados de forma paginada para otimizar o uso de memória em grandes conjuntos de dados.
     *
     * @param inventoryId O ID do inventário cujos itens serão exportados para a planilha.
     * @return Um array de bytes representando o conteúdo do arquivo Excel gerado.
     * @throws IOException Se ocorrer um erro durante a escrita da planilha.
     */
    public byte[] createAllItemsSheet(long inventoryId) throws IOException {
        int page = 0;   // Inicia a paginação na primeira página (índice 0).
        int size = 100; // Define o tamanho da página, ou seja, quantos itens serão buscados por vez.
        int rowNum = 1; // Começa a escrever dados na linha 1 (linha 0 é para o cabeçalho).

        // Cria um novo SXSSFWorkbook. SXSSFWorkbook é uma implementação do Apache POI
        // otimizada para trabalhar com grandes arquivos Excel XLSX, pois ele não mantém
        // todas as linhas na memória, escrevendo-as em disco conforme são criadas.
        SXSSFWorkbook workbook = new SXSSFWorkbook();
        // Cria uma nova planilha dentro do workbook com o nome "Itens".
        Sheet sheet = workbook.createSheet("Itens");
        // Define a largura das colunas para melhorar a legibilidade.
        sheet.setColumnWidth(0, 3000);  // Coluna 0 (Código)
        sheet.setColumnWidth(1, 20000); // Coluna 1 (Descrição)
        sheet.setColumnWidth(2, 10000); // Coluna 2 (Carga Atual/Responsável)
        sheet.setColumnWidth(3, 3000);  // Coluna 3 (Valor)
        sheet.setColumnWidth(4, 10000); // Coluna 4 (Sala/Local)
        sheet.setColumnWidth(5, 10000); // Coluna 5 (Observações)

        // Cria a linha do cabeçalho na primeira linha da planilha (índice 0).
        Row header = sheet.createRow(0);
        // Define os valores das células do cabeçalho.
        header.createCell(0).setCellValue("Código");
        header.createCell(1).setCellValue("Descrição");
        header.createCell(2).setCellValue("Carga Atual");
        header.createCell(3).setCellValue("Valor");
        header.createCell(4).setCellValue("Sala");
        header.createCell(5).setCellValue("Observações");

        Page<Item> itemsPage; // Variável para armazenar cada página de itens.

        // Loop para buscar e processar os itens de forma paginada.
        do {
            // Cria um objeto Pageable para a requisição de paginação.
            Pageable pageable = PageRequest.of(page, size);
            // Busca uma página de itens do inventário no repositório.
            itemsPage = itemRepository.findByInventory_Id(inventoryId, pageable);

            // Itera sobre cada item na página atual.
            for (Item item : itemsPage) {
                // Cria uma nova linha na planilha para cada item e incrementa o contador de linhas.
                Row row = sheet.createRow(rowNum++);
                // Popula as células da linha com os dados do item.
                row.createCell(0).setCellValue(item.code());        // Código do item.
                row.createCell(1).setCellValue(item.description()); // Descrição do item.
                row.createCell(2).setCellValue(item.responsible()); // Responsável (ou "Carga Atual").
                // Converte o preço do item (long, presumivelmente em centavos) para BigDecimal,
                // divide por 100 para obter o valor monetário, arredonda para 2 casas decimais e converte para String.
                row.createCell(3).setCellValue(new BigDecimal(item.price()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).toString()); // Valor.
                row.createCell(4).setCellValue(item.locale());      // Local/Sala do item.
                // Verifica se há observações para o item.
                if (item.observations() == null || item.observations().isEmpty()) {
                    row.createCell(5).setCellValue(""); // Se não houver observações, deixa a célula em branco.
                } else{
                    // Assumindo que há pelo menos uma observação, pega o conteúdo da primeira.
                    // Se houvesse múltiplas observações e a intenção fosse exibir todas,
                    // seria necessário concatená-las ou criar uma lógica mais complexa aqui.
                    row.createCell(5).setCellValue(item.observations().getFirst().content());
                }
            }

            page++; // Incrementa o número da página para a próxima iteração.
        } while (!itemsPage.isLast()); // Continua o loop enquanto não for a última página de itens.

        // Prepara um ByteArrayOutputStream para escrever o workbook na memória.
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            // Escreve o conteúdo do workbook (planilha) para o ByteArrayOutputStream.
            workbook.write(outputStream);
            // Retorna o conteúdo do outputStream como um array de bytes, que é o arquivo Excel.
            return outputStream.toByteArray();
        } finally {
            // Garante que os recursos do workbook e do outputStream sejam limpos e fechados.
            // Isso é crucial para SXSSFWorkbook, pois ele pode criar arquivos temporários em disco.
            workbook.dispose(); // Limpa quaisquer arquivos temporários criados pelo SXSSFWorkbook.
            workbook.close();   // Fecha o workbook e libera recursos.
            outputStream.close(); // Fecha o output stream.
        }
    }
}