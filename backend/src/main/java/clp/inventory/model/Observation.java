package clp.inventory.model;

// Importa anotações JPA para mapeamento de entidades e persistência.
import jakarta.persistence.*;

// Anotação que marca esta classe como uma entidade JPA, indicando que ela será mapeada para uma tabela no banco de dados.
@Entity
// Anotação que especifica o nome da tabela no banco de dados para esta entidade.
@Table(name = "im_observation")
public class Observation {

    // Anotação que designa a chave primária da entidade.
    @Id
    // Anotação que mapeia o campo 'id' para a coluna 'id' na tabela do banco de dados.
    @Column(name = "id")
    // Anotação que especifica a estratégia de geração de valores para a chave primária.
    // IDENTITY indica que o banco de dados se encarregará de gerar valores auto-incrementais.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id; // Campo para o identificador único da observação.

    // Anotação que mapeia o campo 'content' para uma coluna não nula no banco de dados.
    @Column(nullable = false)
    private String content; // Campo para o conteúdo textual da observação.

    // Construtor padrão (sem argumentos).
    // É necessário para o JPA (Java Persistence API) para que ele possa instanciar objetos desta classe.
    public Observation() {
    }

    // Construtor que permite criar uma nova instância de Observation com um conteúdo específico.
    public Observation(String content) {
        this.content = content; // Inicializa o campo 'content' com o valor passado.
    }

    // Método de acesso (getter) para o ID da observação.
    // O nome do método 'id()' é um estilo comum em records do Java 16+, mas em classes POJO tradicionais
    // o nome convencional seria 'getId()'.
    public long id() {
        return id;
    }

    // Método de acesso (getter) para o conteúdo da observação.
    // Assim como 'id()', 'content()' segue o estilo de record.
    public String content() {
        return content;
    }

}