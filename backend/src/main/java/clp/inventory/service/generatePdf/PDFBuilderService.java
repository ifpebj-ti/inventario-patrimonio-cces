package clp.inventory.service.generatePdf;

import clp.inventory.dto.PDFLabelData;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
public class PDFBuilderService {

    public byte[] createLabelsPdf(List<PDFLabelData> labels) {
        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, outputStream);
            document.open();

            Font codeFont = FontFactory.getFont(FontFactory.HELVETICA, 12, Font.BOLD);
            Font descriptionFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

            int numberOfColumns = 3;
            PdfPTable table = new PdfPTable(numberOfColumns);
            table.setWidthPercentage(100);
            table.getDefaultCell().setBorder(Rectangle.NO_BORDER);

            for (PDFLabelData label : labels) {
                PdfPCell cell = new PdfPCell();
                cell.setBorder(Rectangle.NO_BORDER);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setPaddingBottom(20f);

                Paragraph codeParagraph = new Paragraph(label.code(), codeFont);
                codeParagraph.setAlignment(Element.ALIGN_CENTER);
                cell.addElement(codeParagraph);

                Image qrCode = Image.getInstance(label.qrCodeImage());
                qrCode.setAlignment(Element.ALIGN_CENTER);
                qrCode.scaleToFit(100, 100);
                cell.addElement(qrCode);

                Paragraph descriptionParagraph = new Paragraph(label.description(), descriptionFont);
                descriptionParagraph.setAlignment(Element.ALIGN_CENTER);
                descriptionParagraph.setSpacingAfter(30);
                cell.addElement(descriptionParagraph);

                table.addCell(cell);
            }

            int totalCells = labels.size();
            int remainder = totalCells % numberOfColumns;
            if (remainder != 0) {
                int emptyCells = numberOfColumns - remainder;
                for (int i = 0; i < emptyCells; i++) {
                    PdfPCell emptyCell = new PdfPCell(new Phrase(""));
                    emptyCell.setBorder(Rectangle.NO_BORDER);
                    table.addCell(emptyCell);
                }
            }

            document.add(table);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            document.close();
        }
        return outputStream.toByteArray();
    }
}
