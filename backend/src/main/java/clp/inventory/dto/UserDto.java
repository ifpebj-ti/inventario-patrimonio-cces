package clp.inventory.dto;

// Importa anotações de validação do Jakarta Validation (parte do Bean Validation).
import jakarta.validation.constraints.Email;   // Para validar se uma string é um formato de e-mail válido.
import jakarta.validation.constraints.NotBlank; // Para validar se uma string não é nula e não contém apenas espaços em branco.

// Define um record Java chamado 'UserDto'.
// Records são uma funcionalidade do Java (a partir do Java 16) que servem como classes concisas para
// armazenar dados imutáveis. Eles são ideais para Data Transfer Objects (DTOs)
// pois reduzem a quantidade de código boilerplate (construtores, getters, equals, hashCode, toString são gerados automaticamente).
public record UserDto(
        // Componente 'id' do tipo long: representa o identificador único do usuário.
        long id,

        // Componente 'name' do tipo String.
        // @NotBlank(message = "O nome é obrigatório"): Garante que o campo 'name' não seja nulo, vazio ou contenha apenas espaços em branco.
        // Se a validação falhar, a mensagem de erro "O nome é obrigatório" será retornada.
        @NotBlank(message = "O nome é obrigatório") String name,

        // Componente 'email' do tipo String.
        // @NotBlank(message = "O email é obrigatório"): Garante que o campo 'email' não seja nulo ou vazio.
        // @Email(message = "Email inválido"): Valida que o formato da string corresponde a um endereço de e-mail válido.
        @NotBlank(message = "O email é obrigatório") @Email(message = "Email inválido") String email,

        // Componente 'password' do tipo String.
        // @NotBlank(message = "A senha é obrigatória"): Garante que o campo 'password' não seja nulo ou vazio.
        @NotBlank(message = "A senha é obrigatória") String password,

        // Componente 'telephone' do tipo String: representa o número de telefone do usuário (opcional, sem validações específicas aqui).
        String telephone,

        // Componente 'verified' do tipo boolean.
        // @NotBlank: Embora seja uma anotação para Strings, pode ser aplicada a booleanos em alguns contextos de frameworks
        // para indicar que o valor não pode ser nulo. No entanto, para booleanos primitivos, @NotNull seria mais comum se a intenção
        // é garantir que não seja um Wrapper Boolean null. Para um 'boolean' primitivo, ele sempre terá um valor (true/false).
        // A presença de @NotBlank aqui em um boolean primitivo é incomum e pode não ter o efeito desejado,
        // pois 'boolean' não pode ser "blank". Se fosse 'Boolean' (Wrapper), @NotNull faria sentido.
        @NotBlank boolean verified
) {
    // Como um record, o compilador gera automaticamente:
    // - Um construtor canônico com todos os componentes.
    // - Métodos de acesso (equivalentes a getters) para cada componente (id(), name(), email(), etc.).
    // - Implementações de 'equals()', 'hashCode()' e 'toString()' baseadas em todos os componentes.
    // A imutabilidade do record, combinada com as anotações de validação, torna este DTO seguro e robusto
    // para receber e validar dados de entrada do usuário.
}