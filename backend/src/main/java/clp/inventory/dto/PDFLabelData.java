package clp.inventory.dto;

// Define um record Java chamado 'PDFLabelData'.
// Este record serve como um Data Transfer Object (DTO) para encapsular os dados
// necessários para gerar uma única etiqueta em um documento PDF.
// Records são classes concisas e imutáveis introduzidas no Java 16, ideais para DTOs,
// pois o compilador gera automaticamente o construtor, métodos de acesso (getters),
// e as implementações de equals(), hashCode() e toString(), reduzindo o boilerplate.
public record PDFLabelData(
        // Componente 'code' do tipo String: representa o código do item que será exibido na etiqueta.
        String code,
        // Componente 'description' do tipo String: representa a descrição do item para a etiqueta.
        String description,
        // Componente 'qrCodeImage' do tipo byte[]: representa a imagem do QR Code como um array de bytes.
        // Esta imagem pode ser gerada a partir do código do item ou de outra informação relevante,
        // e será incorporada diretamente no PDF.
        byte[] qrCodeImage
) {
    // Como um record, não é necessário adicionar explicitamente:
    // - Um construtor com 'code', 'description' e 'qrCodeImage'.
    // - Métodos como 'getCode()', 'getDescription()', 'getQrCodeImage()'.
    // - Sobrescritas de 'equals()', 'hashCode()' ou 'toString()'.
    // Todos esses membros são gerados automaticamente pelo compilador, garantindo
    // que este DTO seja imutável e fácil de usar para transportar os dados de uma etiqueta.
}