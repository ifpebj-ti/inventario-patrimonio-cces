package clp.inventory.model;

// Define uma enumeração (enum) chamada 'TokenType'.
// Enums são um tipo especial de classe que representa um grupo de constantes (valores imutáveis e fixos).
// Neste caso, 'TokenType' é usada para categorizar diferentes tipos de tokens que podem ser usados na aplicação,
// como tokens de verificação de e-mail ou tokens de redefinição de senha.
public enum TokenType {
    // Declara o primeiro tipo de token: VERIFICATION.
    // Ele é associado à string "verificationToken" como sua descrição.
    VERIFICATION("verificationToken"),
    // Declara o segundo tipo de token: RESETPASSWORD.
    // Ele é associado à string "resetPasswordToken" como sua descrição.
    RESETPASSWORD("resetPasswordToken");

    // Campo privado e final para armazenar a descrição de cada tipo de token.
    // 'final' significa que o valor é atribuído uma vez no construtor e não pode ser alterado.
    private final String description;

    // Construtor privado para a enumeração TokenType.
    // Construtores de enums são sempre privados ou package-private, pois não podem ser instanciados diretamente de fora.
    // Ele é chamado automaticamente para cada constante de enumeração (VERIFICATION, RESETPASSWORD)
    // para inicializar seu campo 'description'.
    private TokenType(String description) {
        this.description = description; // Atribui a descrição passada ao campo 'description'.
    }

    // Método público para obter a descrição de um tipo de token.
    // Este é o "getter" para o campo 'description'.
    public String getDescription() {
        return description; // Retorna a string de descrição associada ao tipo de token.
    }
}