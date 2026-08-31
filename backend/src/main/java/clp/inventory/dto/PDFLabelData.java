package clp.inventory.dto;

public record PDFLabelData(
        String code,
        String description,
        byte[] qrCodeImage
) {
}
