package clp.inventory.dto;

// Define um record Java chamado 'AuthDto'.
// Records são uma nova funcionalidade do Java (a partir do Java 16) que servem como classes concisas
// para armazenar dados. Eles são ideais para DTOs (Data Transfer Objects)
// pois reduzem a quantidade de código boilerplate (construtores, getters, equals, hashCode, toString).
public record AuthDto(
        // Declara um componente 'email' do tipo String.
        // Este campo armazenará o endereço de e-mail do usuário para autenticação.
        String email,
        // Declara um componente 'password' do tipo String.
        // Este campo armazenará a senha do usuário para autenticação.
        String password
) {
    // Ao usar um record, o compilador Java gera automaticamente:
    // 1. Um construtor canônico com todos os componentes (email, password).
    // 2. Métodos de acesso (equivalentes a getters) para cada componente (email(), password()).
    // 3. Implementações de 'equals()', 'hashCode()' e 'toString()' baseadas em todos os componentes.
    // Isso torna o AuthDto imutável e muito eficiente para representar os dados de login/autenticação
    // que são enviados e recebidos entre o cliente e o servidor.
}