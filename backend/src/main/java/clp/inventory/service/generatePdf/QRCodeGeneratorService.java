package clp.inventory.service.generatePdf;

// Importa classes da biblioteca ZXing para geração de códigos de barras, especificamente QR Codes.
import com.google.zxing.BarcodeFormat;          // Define o formato do código de barras (ex: QR_CODE).
import com.google.zxing.client.j2se.MatrixToImageWriter; // Ajuda a converter BitMatrix em imagem.
import com.google.zxing.common.BitMatrix;       // Representa a matriz bidimensional de bits do QR Code.
import com.google.zxing.qrcode.QRCodeWriter;    // Classe principal para escrever (gerar) QR Codes.
// Importa a anotação @Service do Spring.
import org.springframework.stereotype.Service;

// Importa classes para operações de I/O.
import java.io.ByteArrayOutputStream; // Para escrever a imagem do QR Code na memória como bytes.

// Anotação que marca esta classe como um serviço Spring.
// Isso a torna um componente gerenciado pelo Spring, podendo ser injetado em outros lugares.
@Service
public class QRCodeGeneratorService {

    /**
     * Gera um QR Code como um array de bytes (formato PNG).
     *
     * @param content O conteúdo (texto, URL, etc.) a ser codificado no QR Code.
     * @param width A largura desejada para a imagem do QR Code em pixels.
     * @param height A altura desejada para a imagem do QR Code em pixels.
     * @return Um array de bytes que representa a imagem do QR Code no formato PNG.
     * @throws RuntimeException Se ocorrer um erro durante a geração do QR Code.
     */
    public byte[] generateQRCode(String content, int width, int height) {
        // Cria uma instância de QRCodeWriter. Esta classe é responsável por converter o conteúdo
        // em uma matriz de bits que representa o QR Code.
        QRCodeWriter qrCodeWriter = new QRCodeWriter(); // irá gerar o qrCode em um bitmatrix, matriz bidimensional de bits
        try {
            // Codifica o conteúdo fornecido em uma BitMatrix (matriz de bits).
            // - content: O dado a ser codificado.
            // - BarcodeFormat.QR_CODE: Especifica que queremos um QR Code.
            // - width, height: As dimensões da imagem do QR Code em pixels.
            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height); // gerando bitmatrix com encode
            // Cria um ByteArrayOutputStream para onde a imagem PNG será escrita na memória.
            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            // Converte a BitMatrix em uma imagem PNG e a escreve no ByteArrayOutputStream.
            MatrixToImageWriter.writeToStream(bitMatrix, "png", pngOutputStream);
            // Retorna o conteúdo do ByteArrayOutputStream como um array de bytes, que é a imagem PNG do QR Code.
            return pngOutputStream.toByteArray();
        } catch (Exception e) {
            // Em caso de qualquer erro (ex: WriterException), captura a exceção e a relança
            // como uma RuntimeException, encapsulando a exceção original.
            throw new RuntimeException(e);
        }
    }
}