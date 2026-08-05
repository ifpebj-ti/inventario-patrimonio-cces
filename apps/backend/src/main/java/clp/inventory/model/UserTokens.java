package clp.inventory.model;

// Importa anotações JPA para mapeamento de entidades e persistência.
import jakarta.persistence.*;
// Importa anotações do Spring para injeção de dependências e componentes.
import org.springframework.beans.factory.annotation.Autowired; // @Autowired não é usado diretamente nesta classe, mas é uma importação comum.
import org.springframework.stereotype.Component; // @Component não é usado diretamente nesta classe, mas é uma importação comum para classes Spring.

// Importa a classe LocalDateTime do Java 8 para lidar com datas e horas.
import java.time.LocalDateTime;

// Anotação que marca esta classe como uma entidade JPA, indicando que ela será mapeada para uma tabela no banco de dados.
@Entity
// Anotação que especifica o nome da tabela no banco de dados para esta entidade.
@Table(name = "im_user_tokens")
public class UserTokens {

    // Anotação que designa a chave primária da entidade.
    @Id
    // Anotação que mapeia o campo 'id' para a coluna 'id' na tabela do banco de dados.
    @Column(name = "id")
    // Anotação que especifica a estratégia de geração de valores para a chave primária.
    // IDENTITY indica que o banco de dados se encarregará de gerar valores auto-incrementais.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id; // Campo para o identificador único do token de usuário.

    // Anotação que mapeia o campo 'token' para uma coluna na tabela.
    @Column
    private String token; // Campo para o valor real do token (a string do token).

    // Anotação que mapeia o campo 'tokenType' para uma coluna na tabela.
    // @Enumerated(EnumType.ORDINAL) ou @Enumerated(EnumType.STRING) seriam comuns aqui
    // para especificar como o enum será persistido (como um inteiro ou como uma string).
    // A ausência de @Enumerated implica o padrão, que é ORDINAL, persistindo o índice do enum.
    @Column
    private TokenType tokenType; // Campo para o tipo do token (e.g., VERIFICATION, RESETPASSWORD).

    // Anotação que mapeia o campo 'expiration' para uma coluna não nula.
    // 'updatable = false' e 'insertable = false' indicam que este campo será gerenciado
    // automaticamente pelo banco de dados (por exemplo, usando um timestamp default ou trigger)
    // e não será incluído nas operações de INSERT ou UPDATE geradas pelo JPA.
    @Column(nullable = false, updatable = false, insertable = false)
    private LocalDateTime expiration; // Campo para a data e hora de expiração do token.

    // Anotação que define um relacionamento um-para-um (One-to-One) com a entidade User.
    // Isso significa que um token de usuário pertence a um único usuário, e um usuário pode ter um único token deste tipo.
    @OneToOne
    // Anotação que especifica a coluna de chave estrangeira que faz a junção com a tabela User.
    @JoinColumn(name = "id_user", nullable = false) // 'id_user' é a coluna no banco de dados que referencia o ID do usuário.
    private User user; // Campo que representa o usuário ao qual este token está associado.

    // Métodos de acesso (getters) para os campos da entidade.
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public TokenType getTokenType() {
        return tokenType;
    }

    public void setTokenType(TokenType tokenType) {
        this.tokenType = tokenType;
    }

    public LocalDateTime getExpiration() {
        return expiration;
    }

    public void setExpiration(LocalDateTime expiration) {
        this.expiration = expiration;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    // Construtor para criar uma nova instância de UserTokens com o token, tipo de token e usuário associado.
    // Nota: A expiração não é definida aqui, o que sugere que ela é definida em outro lugar (ex: no serviço, ou pelo DB).
    public UserTokens(String token, TokenType tokenType, User user) {
        this.token = token;
        this.tokenType = tokenType;
        this.user = user;
    }

    // Construtor padrão (sem argumentos).
    // É necessário para o JPA para que ele possa instanciar objetos desta classe.
    public UserTokens() {

    }
}