package clp.inventory.dto;

// Define um record Java chamado 'ResetPasswordDto'.
// Records são uma funcionalidade do Java (a partir do Java 16) que oferece uma forma concisa de declarar
// classes para transportar dados imutáveis, ideais para Data Transfer Objects (DTOs).
// Eles automaticamente geram construtores, métodos de acesso (getters), equals(), hashCode() e toString(),
// reduzindo o código boilerplate.
public record ResetPasswordDto(
        // Declara um componente 'password' do tipo String.
        // Este campo é usado para encapsular a nova senha que um usuário deseja definir
        // durante um processo de redefinição de senha.
        String password
) {
    // Por ser um record, o compilador se encarrega de gerar todo o código padrão necessário:
    // - Um construtor que aceita o parâmetro 'password'.
    // - Um método de acesso público 'password()' que retorna o valor da senha.
    // - Implementações de 'equals()', 'hashCode()' e 'toString()' baseadas no campo 'password'.
    // Isso torna 'ResetPasswordDto' uma maneira limpa e segura de transportar a nova senha
    // em uma requisição, garantindo imutabilidade.
}