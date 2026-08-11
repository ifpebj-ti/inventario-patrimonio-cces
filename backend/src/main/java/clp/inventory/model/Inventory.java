package clp.inventory.model;

// Importa anotações JPA para mapeamento de entidades.
import jakarta.persistence.*;

// Importa classes relacionadas a data e hora do Java 8 e coleções.
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Anotação que marca esta classe como uma entidade JPA, ou seja, ela será mapeada para uma tabela no banco de dados.
@Entity
// Anotação que especifica o nome da tabela no banco de dados para esta entidade.
@Table(name = "im_inventory")
public class Inventory {

    // Anotação que designa a chave primária da entidade.
    @Id
    // Anotação que mapeia o campo 'id' para a coluna 'id' na tabela do banco de dados.
    @Column(name = "id")
    // Anotação que especifica a estratégia de geração de valores para a chave primária.
    // IDENTITY indica que o banco de dados se encarregará de gerar valores auto-incrementais.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id; // Campo para o identificador único do inventário.

    // Anotação que mapeia o campo 'name' para uma coluna não nula no banco de dados.
    @Column(nullable = false)
    private String name; // Campo para o nome do inventário.

    private String description; // Campo para a descrição do inventário.

    // Anotação que mapeia o campo 'createdAt' para uma coluna não nula.
    // 'insertable = false' e 'updatable = false' indicam que este campo será gerenciado
    // automaticamente pelo banco de dados (por exemplo, usando um trigger) e não será
    // incluído nas operações de INSERT ou UPDATE geradas pelo JPA.
    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt; // Campo para a data e hora de criação do inventário.

    // Semelhante a 'createdAt', este campo é gerenciado pelo banco de dados
    // para registrar a última data e hora de atualização.
    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt; // Campo para a data e hora da última atualização do inventário.

    // Anotação que define um relacionamento muitos-para-um (Many-to-One) com a entidade User.
    // Isso significa que muitos inventários podem pertencer a um único usuário.
    @ManyToOne
    // Anotação que especifica a coluna de chave estrangeira que faz a junção com a tabela User.
    @JoinColumn(name = "id_user", nullable = false) // 'id_user' é a coluna no banco de dados que referencia o ID do usuário.
    private User user; // Campo que representa o usuário ao qual este inventário pertence.

    // Anotação que define um relacionamento um-para-muitos (One-to-Many) com a entidade Item.
    // Isso significa que um inventário pode conter muitos itens.
    @OneToMany(
            mappedBy = "inventory", // Indica que o campo 'inventory' na entidade Item é o lado "proprietário" do relacionamento.
            cascade = CascadeType.ALL, // Todas as operações (persist, merge, remove, refresh, detach) em Inventory serão propagadas para os Itens associados.
            orphanRemoval = true,      // Itens que são removidos da lista 'items' (ou quando um Inventory é excluído) serão removidos do banco de dados.
            fetch = FetchType.LAZY     // Os itens serão carregados sob demanda (lazy loading), ou seja, só quando a coleção 'items' for acessada.
    )
    private List<Item> items = new ArrayList<>(); // Lista para armazenar os itens associados a este inventário. Inicializada como um ArrayList vazio.

    // Construtor padrão (sem argumentos). É necessário para o JPA.
    public Inventory() {
    }

    // Método de acesso (getter) para o campo 'user'.
    // O nome do método 'User()' com 'U' maiúsculo é incomum para getters em Java (convenção é 'getUser()'),
    // mas funcionalmente retorna o objeto User associado.
    public User User() {
        return user;
    }

    // Construtor para criar uma nova instância de Inventory com nome, descrição e o usuário associado.
    public Inventory(String name, String description, User user) {
        this.name = name;
        this.description = description;
        this.user = user;
    }

    // Método de acesso (getter) para o ID do inventário.
    // O nome do método 'id()' com 'i' minúsculo é um estilo de record, mas em classes normais,
    // a convenção é 'getId()'.
    public Long id() {
        return id;
    }

    // Método de acesso (getter) para o nome do inventário.
    public String name() {
        return name;
    }

    // Método setter para o nome do inventário.
    public void setName(String name) {
        this.name = name;
    }

    // Método setter para a descrição do inventário.
    public void setDescription(String description) {
        this.description = description;
    }

    // Método de acesso (getter) para a descrição do inventário.
    public String description() {
        return description;
    }

    // Método de acesso (getter) para a data de criação do inventário.
    public LocalDateTime createdAt() {
        return createdAt;
    }

    // Método de acesso (getter) para a lista de itens do inventário.
    public List<Item> items() {
        return items;
    }

    // Método para adicionar um item à lista de itens do inventário.
    // Também define o inventário no próprio item para manter a coerência do relacionamento bidirecional.
    public void addItem(Item item) {
        item.setInventory(this); // Define este inventário como o inventário do item.
        items.add(item);         // Adiciona o item à lista de itens.
    }

    // Método para remover um item da lista de itens do inventário.
    // Embora o item.setInventory(this) aqui seja redundante para remoção em orphanRemoval,
    // se o item pudesse existir sem inventário, seria usado para desassociá-lo.
    public void removeItem(Item item) {
        item.setInventory(this); // Define o inventário do item (neste contexto, não é estritamente necessário para remoção com orphanRemoval).
        items.remove(item);      // Remove o item da lista.
    }

}