package clp.inventory.service.generatePdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import clp.inventory.dto.PDFLabelData;
import clp.inventory.model.Item;
import clp.inventory.repository.ItemRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/** Gera QR Code e PDF de etiquetas de verdade (sem Spring nem banco). */
class PdfGenerationTest {

  private final QRCodeGeneratorService qrCodeService = new QRCodeGeneratorService();
  private final PDFBuilderService pdfBuilder = new PDFBuilderService();

  private static boolean isPng(byte[] bytes) {
    return bytes.length > 8
        && (bytes[0] & 0xFF) == 0x89
        && new String(bytes, 1, 3, StandardCharsets.US_ASCII).equals("PNG");
  }

  private static boolean isPdf(byte[] bytes) {
    return bytes.length > 5 && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-");
  }

  private static Item item(String code) {
    return new Item(code, "Nome " + code, "Descricao " + code, 1000, "Sala 1", "Fulano");
  }

  @Test
  void qrCodeEGeradoComoPng() {
    assertThat(isPng(qrCodeService.generateQRCode("CODIGO-123", 150, 150))).isTrue();
  }

  @Test
  void qrCodeSemConteudoFalha() {
    assertThatThrownBy(() -> qrCodeService.generateQRCode(null, 150, 150))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void pdfDeEtiquetasComLinhaCompletaEIncompleta() {
    byte[] png = qrCodeService.generateQRCode("X", 100, 100);
    List<PDFLabelData> tres =
        List.of(
            new PDFLabelData("A", "a", png),
            new PDFLabelData("B", "b", png),
            new PDFLabelData("C", "c", png));
    List<PDFLabelData> quatro = new java.util.ArrayList<>(tres);
    quatro.add(new PDFLabelData("D", "d", png));

    assertThat(isPdf(pdfBuilder.createLabelsPdf(tres))).isTrue();
    // 4 etiquetas deixam a segunda linha incompleta: o builder preenche com células vazias.
    assertThat(isPdf(pdfBuilder.createLabelsPdf(quatro))).isTrue();
  }

  @Test
  void pdfComImagemInvalidaFalha() {
    // O builder engole o erro da imagem, mas o documento fica sem páginas e o close() lança.
    // O ItemController converte isso em HTTP 500.
    assertThatThrownBy(
            () ->
                pdfBuilder.createLabelsPdf(
                    List.of(new PDFLabelData("A", "a", new byte[] {1, 2, 3}))))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void pdfDeItensPorId() {
    ItemRepository repository = mock(ItemRepository.class);
    when(repository.findAllById(anyList())).thenReturn(List.of(item("A1"), item("A2")));
    ItemPdfService service = new ItemPdfService(repository, qrCodeService);

    assertThat(isPdf(service.generateItemPdf(List.of(1L, 2L)))).isTrue();
  }

  @Test
  void pdfDeItensPorIdSemResultadoFalha() {
    ItemRepository repository = mock(ItemRepository.class);
    when(repository.findAllById(anyList())).thenReturn(List.of());
    ItemPdfService service = new ItemPdfService(repository, qrCodeService);

    assertThatThrownBy(() -> service.generateItemPdf(List.of(99L)))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void pdfDeTodosOsItensDoInventario() {
    ItemRepository repository = mock(ItemRepository.class);
    when(repository.findByInventory_Id(eq(7L), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(item("B1"), item("B2"), item("B3"), item("B4"))));
    ItemPdfService service = new ItemPdfService(repository, qrCodeService);

    assertThat(isPdf(service.generateAllItemsPdf(7L))).isTrue();
  }

  @Test
  void pdfDeTodosOsItensDeInventarioVazioFalha() {
    ItemRepository repository = mock(ItemRepository.class);
    when(repository.findByInventory_Id(eq(8L), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));
    ItemPdfService service = new ItemPdfService(repository, qrCodeService);

    assertThatThrownBy(() -> service.generateAllItemsPdf(8L))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("8");
  }
}
