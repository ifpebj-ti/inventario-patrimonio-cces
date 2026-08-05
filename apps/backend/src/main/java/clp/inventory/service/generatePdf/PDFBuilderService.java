package clp.inventory.service.generatePdf;

// Importa o DTO PDFLabelData, que contém os dados para cada etiqueta.
import clp.inventory.dto.PDFLabelData;
// Importa classes da biblioteca iText (com.lowagie.text) para manipulação de PDF.
import com.lowagie.text.*;           // Classes básicas como Document, Paragraph, Image, Font.
import com.lowagie.text.pdf.PdfPCell;  // Para células de tabela em PDF.
import com.lowagie.text.pdf.PdfPTable; // Para tabelas em PDF.
import com.lowagie.text.pdf.PdfWriter; // Para escrever o documento PDF em um OutputStream.
// Importa a anotação @Service do Spring.
import org.springframework.stereotype.Service;

// Importa classes para operações de I/O e coleções.
import java.io.ByteArrayOutputStream; // Para escrever o PDF na memória como um array de bytes.
import java.util.List;               // Para lidar com listas de dados de etiquetas.

// Anotação que marca esta classe como um serviço Spring.
// Isso a torna um componente gerenciado pelo Spring, podendo ser injetado em outros lugares.
@Service
public class PDFBuilderService {

    /**
     * Cria um documento PDF contendo etiquetas com códigos, descrições e QR Codes.
     * As etiquetas são organizadas em uma tabela com 3 colunas.
     *
     * @param labels Uma lista de objetos PDFLabelData, cada um contendo os dados para uma etiqueta.
     * @return Um array de bytes que representa o conteúdo do arquivo PDF gerado.
     */
    public byte[] createLabelsPdf(List<PDFLabelData> labels) {
        // Cria um novo documento PDF com o tamanho de página A4.
        Document document = new Document(PageSize.A4);
        // Cria um ByteArrayOutputStream para armazenar o conteúdo do PDF na memória como bytes.
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            // Obtém uma instância de PdfWriter para escrever o documento no outputStream.
            PdfWriter.getInstance(document, outputStream);
            // Abre o documento para começar a adicionar conteúdo.
            document.open();

            // Define as fontes para o código e a descrição da etiqueta.
            // FontFactory.HELVETICA: Tipo de fonte Helvetica.
            // 12 / 10: Tamanho da fonte.
            // Font.BOLD: Estilo da fonte em negrito para o código.
            Font codeFont = FontFactory.getFont(FontFactory.HELVETICA, 12, Font.BOLD);
            Font descriptionFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

            // Define o número de colunas para a tabela de etiquetas.
            int numberOfColumns = 3;
            // Cria uma nova tabela PDF com o número especificado de colunas.
            PdfPTable table = new PdfPTable(numberOfColumns);
            // Define a largura da tabela para 100% da página.
            table.setWidthPercentage(100);
            // Define que as células padrão da tabela não terão borda.
            table.getDefaultCell().setBorder(Rectangle.NO_BORDER);

            // Itera sobre cada objeto PDFLabelData na lista para criar uma etiqueta para cada um.
            for (PDFLabelData label : labels) {
                // Cria uma nova célula da tabela para conter os elementos da etiqueta.
                PdfPCell cell = new PdfPCell();
                cell.setBorder(Rectangle.NO_BORDER);               // Remove a borda da célula.
                cell.setHorizontalAlignment(Element.ALIGN_CENTER); // Centraliza o conteúdo horizontalmente.
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);   // Centraliza o conteúdo verticalmente.
                cell.setPaddingBottom(20f);                        // Adiciona preenchimento inferior à célula.

                // Cria um parágrafo para o código do item, aplicando a fonte definida e centralizando.
                Paragraph codeParagraph = new Paragraph(label.code(), codeFont);
                codeParagraph.setAlignment(Element.ALIGN_CENTER);
                cell.addElement(codeParagraph); // Adiciona o parágrafo à célula.

                // Cria uma imagem PDF a partir do array de bytes do QR Code.
                Image qrCode = Image.getInstance(label.qrCodeImage());
                qrCode.setAlignment(Element.ALIGN_CENTER); // Centraliza a imagem.
                qrCode.scaleToFit(100, 100);       // Redimensiona a imagem para caber em 100x100 pixels.
                cell.addElement(qrCode);                   // Adiciona a imagem à célula.

                // Cria um parágrafo para a descrição do item, aplicando a fonte definida, centralizando e adicionando espaço após.
                Paragraph descriptionParagraph = new Paragraph(label.description(), descriptionFont);
                descriptionParagraph.setAlignment(Element.ALIGN_CENTER);
                descriptionParagraph.setSpacingAfter(30); // Adiciona espaço após a descrição para separar as etiquetas.
                cell.addElement(descriptionParagraph);    // Adiciona o parágrafo à célula.

                table.addCell(cell); // Adiciona a célula (com todo o conteúdo da etiqueta) à tabela.
            }

            // Verifica se na última linha há células vazias e completa com células vazias para que assim não ocorra um
            // problema e a última linha apareça no pdf
            // Este bloco de código garante que a última linha da tabela seja preenchida com células vazias,
            // se o número total de etiquetas não for um múltiplo exato do número de colunas.
            // Isso pode evitar problemas de layout ou quebras inesperadas no PDF.
            int totalCells = labels.size();           // Número total de células já adicionadas (igual ao número de etiquetas).
            int remainder = totalCells % numberOfColumns; // Calcula o resto da divisão para saber quantas células faltam na última linha.
            if (remainder != 0) { // Se o resto não for zero, significa que a última linha não está completa.
                int emptyCells = numberOfColumns - remainder; // Calcula quantas células vazias precisam ser adicionadas.
                for (int i = 0; i < emptyCells; i++) {
                    PdfPCell emptyCell = new PdfPCell(new Phrase("")); // Cria uma célula vazia.
                    emptyCell.setBorder(Rectangle.NO_BORDER);         // Remove a borda da célula vazia.
                    table.addCell(emptyCell);                         // Adiciona a célula vazia à tabela.
                }
            }

            document.add(table); // Adiciona a tabela completa ao documento PDF.
        } catch (Exception e) {
            // Em caso de qualquer exceção durante a criação do PDF, imprime o stack trace para depuração.
            e.printStackTrace();
        } finally {
            // Garante que o documento seja fechado no final, independentemente de ter ocorrido um erro ou não.
            // Isso libera os recursos e finaliza a escrita do PDF.
            document.close();
        }
        // Converte o conteúdo do ByteArrayOutputStream para um array de bytes e o retorna.
        // Este array de bytes é o arquivo PDF completo.
        return outputStream.toByteArray();
    }
}