package clp.inventory.dto;

// Define um record Java chamado 'EmailRequestDto'.
// Este record é um Data Transfer Object (DTO) projetado especificamente para encapsular
// os dados necessários para enviar um e-mail.
// Records são classes concisas e imutáveis introduzidas no Java 16, ideais para representar
// dados puros, como em DTOs, pois reduzem significativamente o código boilerplate.
public record EmailRequestDto(
        // Declara um componente 'email' do tipo String.
        // Este campo representará o endereço de e-mail do destinatário.
        String email,
        // Declara um componente 'subject' do tipo String.
        // Este campo conterá o assunto do e-mail.
        String subject,
        // Declara um componente 'message' do tipo String.
        // Este campo armazenará o corpo ou a mensagem principal do e-mail.
        String message
) {
    // Ao utilizar um record, o compilador Java gera automaticamente:
    // 1. Um construtor canônico com todos os componentes (email, subject, message).
    // 2. Métodos de acesso (equivalentes a getters) para cada componente (email(), subject(), message()).
    // 3. Implementações automáticas e eficientes dos métodos 'equals()', 'hashCode()' e 'toString()'
    //    baseadas em todos os componentes.
    // Isso garante que o EmailRequestDto seja imutável e fácil de usar para transportar dados de e-mail
    // entre diferentes camadas da aplicação (por exemplo, do controlador para um serviço de e-mail).
}