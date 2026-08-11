package clp.inventory.dto;

// Importa a classe User do pacote clp.inventory.model.
// Isso indica que o AuthResponseDTO irá incluir um objeto User como parte de sua estrutura.
import clp.inventory.model.User;

// Define um record Java chamado 'AuthResponseDTO'.
// Este record é projetado para encapsular a resposta de uma operação de autenticação,
// que tipicamente inclui um token de autenticação e informações sobre o usuário autenticado.
// Records são classes de dados concisas e imutáveis, ideais para DTOs (Data Transfer Objects)
// por reduzirem a necessidade de boilerplate code.
public record AuthResponseDTO(
        // Declara um componente 'token' do tipo String.
        // Este campo provavelmente armazenará o JWT (JSON Web Token) ou outro tipo de token
        // que será usado para futuras requisições autenticadas.
        String token,
        // Declara um componente 'user' do tipo User.
        // Este campo conterá um objeto User, fornecendo detalhes sobre o usuário que foi autenticado com sucesso.
        // Isso evita a necessidade de uma requisição separada para obter os detalhes do usuário após o login.
        User user
) {
    // Ao usar um record, o compilador Java gera automaticamente para 'AuthResponseDTO':
    // 1. Um construtor canônico que aceita 'token' e 'user'.
    // 2. Métodos de acesso (equivalentes a getters) para 'token()' e 'user()'.
    // 3. Implementações padrão de 'equals()', 'hashCode()' e 'toString()' baseadas nos valores de 'token' e 'user'.
    // A imutabilidade do record é benéfica para a segurança e previsibilidade em operações de autenticação.
}