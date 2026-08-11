package clp.inventory.model;

// Importa a anotação @JsonIgnore do Jackson, usada para ignorar um campo durante a serialização/desserialização JSON.
import com.fasterxml.jackson.annotation.JsonIgnore;
// Importa anotações JPA para mapeamento de entidades e persistência.
import jakarta.persistence.*;

// Importa classes relacionadas a data e hora do Java 8.
import java.time.LocalDateTime;

// Anotação que marca esta classe como uma entidade JPA, ou seja, ela será mapeada para uma tabela no banco de dados.
@Entity
// Anotação que especifica o nome da tabela no banco de dados para esta entidade.
@Table(name = "im_user")
public class User {

    // Anotação que designa a chave primária da entidade.
    @Id
    // Anotação que mapeia o campo 'id' para a coluna 'id' na tabela do banco de dados.
    @Column(name = "id")
    // Anotação que especifica a estratégia de geração de valores para a chave primária.
    // IDENTITY indica que o banco de dados se encarregará de gerar valores auto-incrementais.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Campo para o identificador único do usuário.

    // Anotação que mapeia o campo 'name' para uma coluna não nula no banco de dados.
    @Column(nullable = false)
    private String name; // Campo para o nome completo do usuário.

    // Anotação que mapeia o campo 'email' para uma coluna que deve ser única e não nula.
    @Column(unique = true, nullable = false)
    private String email; // Campo para o endereço de e-mail do usuário, que é único.

    // Anotação que mapeia o campo 'password' para uma coluna não nula.
    // @JsonIgnore: Esta anotação do Jackson impede que o campo 'password' seja incluído
    // na representação JSON quando um objeto User é serializado (por exemplo, ao retornar dados do usuário para o frontend).
    // Isso é crucial para a segurança, evitando a exposição da senha.
    @Column(nullable = false)
    @JsonIgnore
    private String password; // Campo para a senha do usuário.

    // Anotação que mapeia o campo 'verified' para uma coluna.
    @Column
    private boolean verified; // Campo booleano que indica se o e-mail do usuário foi verificado.

    @Column(nullable = false)
    private String telephone;

    // Método de acesso (getter) para o campo 'email'.
    // Nota: Geralmente, getters e setters estão agrupados para melhor legibilidade.
    public String getEmail() {
        return email;
    }

    // Anotação que mapeia o campo 'createdAt' para uma coluna não nula.
    // 'updatable = false' e 'insertable = false' indicam que este campo será gerenciado
    // automaticamente pelo banco de dados (por exemplo, usando um timestamp default ou trigger)
    // e não será incluído nas operações de INSERT ou UPDATE geradas pelo JPA.
    @Column(nullable = false, updatable = false, insertable = false)
    private LocalDateTime createdAt; // Campo para a data e hora de criação do usuário.

    // Semelhante a 'createdAt', este campo é gerenciado pelo banco de dados para
    // registrar a última data e hora de atualização.
    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt; // Campo para a data e hora da última atualização do usuário.

    // Construtor padrão (sem argumentos). É necessário para o JPA.
    public User() {
    }

    // Construtor para criar uma nova instância de User com os dados fornecidos.
    public User(String name, String email, String password, boolean verified, String telephone) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.verified = verified;
        this.telephone = telephone; // Inicializa o telefone no construtor.
    }

    // Método de acesso (getter) para o ID do usuário.
    public Long getId() {
        return id;
    }

    // Método de acesso (getter) para o status de verificação. O prefixo 'is' é comum para booleanos.
    public boolean isVerified() {
        return verified;
    }

    // Método setter para o status de verificação do usuário.
    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    // Método setter para o ID do usuário.
    public void setId(Long id) {
        this.id = id;
    }

    // Método de acesso (getter) para o nome do usuário.
    public String getName() {
        return name;
    }

    // Método setter para o nome do usuário.
    public void setName(String name) {
        this.name = name;
    }

    // Método setter para o e-mail do usuário.
    public void setEmail(String email) {
        this.email = email;
    }

    // Método de acesso (getter) para a senha do usuário.
    // @JsonIgnore: Assim como no campo, esta anotação no getter também impede que a senha
    // seja serializada para JSON ao acessar o objeto User através deste método.
    @JsonIgnore
    public String getPassword() {
        return password;
    }

    // Método setter para a senha do usuário.
    public void setPassword(String password) {
        this.password = password;
    }

}