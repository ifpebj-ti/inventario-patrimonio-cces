package clp.inventory.dto;

// Importa a classe List do pacote java.util, que será usada para armazenar uma coleção de erros de validação.
import java.util.List;

// Define um record Java chamado 'ValidationResponse'.
// Records são uma funcionalidade do Java (a partir do Java 16) que oferece uma forma concisa de declarar
// classes para transportar dados imutáveis. Eles são perfeitos para Data Transfer Objects (DTOs),
// como este, que encapsula o resultado de um processo de validação.
// O compilador gera automaticamente o construtor canônico, métodos de acesso (getters),
// e as implementações de equals(), hashCode() e toString(), reduzindo significativamente o código boilerplate.
public record ValidationResponse(
        // Declara o componente 'hasErrors' do tipo boolean.
        // Este campo indica se algum erro de validação foi encontrado (true) ou se a validação foi bem-sucedida (false).
        boolean hasErrors,
        // Declara o componente 'errors' do tipo List<ValidationError>.
        // Este campo contém uma lista de objetos ValidationError. Cada ValidationError detalha
        // um erro específico, incluindo o código do item, a linha onde o erro ocorreu e as mensagens de erro.
        // Se 'hasErrors' for false, esta lista estará vazia.
        List<ValidationError> errors
) {
    // Por ser um record, não é necessário escrever explicitamente:
    // - Um construtor que aceita 'hasErrors' e 'errors'.
    // - Métodos de acesso como `hasErrors()` e `errors()`.
    // - As implementações de `equals()`, `hashCode()` ou `toString()`.
    // Todas essas funcionalidades são geradas automaticamente pelo compilador,
    // tornando o 'ValidationResponse' um DTO eficiente e imutável para comunicar
    // os resultados de validações de dados.
}