package clp.inventory.model;

// Importa anotações JPA para mapeamento de entidades e persistência.
import jakarta.persistence.*;

// Importa classes relacionadas a data/hora, coleções e geração de UUIDs.
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors; // Importado para uso no método getNotes().

// Anotação que marca esta classe como uma entidade JPA, indicando que ela será mapeada para uma tabela no banco de dados.
@Entity
// Anotação que especifica o nome da tabela no banco de dados para esta entidade.
@Table(name = "im_item")
public class Item {

    // Anotação que designa a chave primária da entidade.
    @Id
    // Anotação que especifica a estratégia de geração de valores para a chave primária.
    // IDENTITY indica que o banco de dados se encarregará de gerar valores auto-incrementais.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id; // Campo para o identificador único do item.

    // Anotação que mapeia o campo 'code' para uma coluna não nula no banco de dados.
    @Column(nullable = false)
    private String code; // Campo para o código de identificação do item.

    private String name; // Campo para o nome do item.

    private String description; // Campo para a descrição detalhada do item.

    private long price; // Campo para o preço do item (provavelmente armazenado em centavos para evitar problemas de ponto flutuante).

    private String locale; // Campo para o local onde o item está (ex: sala, armazém).

    // Anotação que mapeia o campo 'qrCode' para uma coluna não nula.
    @Column(nullable = false)
    private String qrCode; // Campo para a string que representa o QR Code do item.

    private String responsible; // Campo para o nome do responsável pelo item.

    // Anotação que mapeia o campo 'isValid' para uma coluna não nula, que não pode ser atualizada
    // e deve ter um valor único. Aparentemente 'unique = true' em um boolean não é comum para validar se é único no DB, mas sim no modelo.
    // O valor padrão é false.
    @Column(nullable = false, updatable = false, unique = true) // 'unique = true' aqui é pouco usual para booleanos, talvez um erro lógico ou intenção específica.
    private boolean isValid = false; // Campo que indica se o item foi validado.

    private LocalDateTime validatedAt; // Campo para a data e hora em que o item foi validado.

    // Anotação que define um relacionamento muitos-para-um (Many-to-One) com a entidade Inventory.
    // Isso significa que muitos itens podem pertencer a um único inventário.
    @ManyToOne
    // Anotação que especifica a coluna de chave estrangeira que faz a junção com a tabela Inventory.
    @JoinColumn(name = "id_inventory") // 'id_inventory' é a coluna no banco de dados que referencia o ID do inventário.
    private Inventory inventory; // Campo que representa o inventário ao qual este item pertence.

    // [INÍCIO DA MUDANÇA] getObservations() e setObservations() precisam ser adicionados
    // Anotação que define um relacionamento um-para-muitos (One-to-Many) com a entidade Observation.
    // Isso significa que um item pode ter muitas observações.
    @OneToMany(
            cascade = CascadeType.ALL, // Todas as operações (persist, merge, remove, refresh, detach) em Item serão propagadas para as Observations associadas.
            orphanRemoval = true,      // Observações que são removidas da lista 'observations' de um Item (ou quando um Item é excluído) serão removidas do banco de dados.
            fetch = FetchType.LAZY)    // As observações serão carregadas sob demanda (lazy loading), ou seja, só quando a coleção 'observations' for acessada.
    // Anotação que especifica a coluna de chave estrangeira na tabela de observações que faz a junção com a tabela Item.
    @JoinColumn(name = "id_item", nullable = false) // 'id_item' é a coluna na tabela de Observation que referencia o ID deste Item.
    private List<Observation> observations = new ArrayList<>(); // Lista para armazenar as observações associadas a este item. Inicializada como um ArrayList vazio.

    // Construtor padrão (sem argumentos). É necessário para o JPA.
    public Item() {
    }

    // Construtor para criar uma nova instância de Item com os dados básicos.
    public Item(String code, String name, String description, long price, String locale, String responsible) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.price = price;
        this.locale = locale;
        generateQrCode(); // Chama o método para gerar o QR Code automaticamente ao criar um item.
        this.responsible = responsible;
    }

    // --- GETTERS ---
    // Métodos de acesso (getters) para os campos da entidade.
    // Alguns seguem a convenção de record (nome do campo com parênteses) e outros a convenção JavaBeans (getCampo()).
    public long id() { // Getter para o ID.
        return id;
    }

    public void setId(long id) { // Setter para o ID (embora o ID seja gerado automaticamente, pode ser usado em casos específicos).
        this.id = id;
    }

    public String code() { // Getter para o código.
        return code;
    }

    public String name() { // Getter para o nome.
        return name;
    }

    public String description() { // Getter para a descrição.
        return description;
    }

    public boolean isValid() { // Getter para o status de validação.
        return isValid;
    }

    public long price() { // Getter para o preço.
        return price;
    }

    public String responsible() { // Getter para o responsável.
        return responsible;
    }

    public String locale() { // Getter para o local.
        return locale;
    }

    public String qrCode() { // Getter para o QR Code.
        return qrCode;
    }

    public List<Observation> observations() { // Getter para a lista de observações.
        return observations;
    }

    // Método para obter todas as observações do item como uma única string, unidas por quebras de linha.
    public String getNotes() {
        if (this.observations == null || this.observations.isEmpty()) {
            return null; // Retorna null se não houver observações.
        }
        // Usa Stream API para mapear cada Observation para seu conteúdo e depois junta tudo em uma String.
        return this.observations.stream()
                .map(Observation::content) // Mapeia para o conteúdo da observação.
                .collect(Collectors.joining("\n")); // Junta os conteúdos com uma quebra de linha entre eles.
    }
    // --- FIM DOS GETTERS ---


    // --- SETTERS ---
    // Métodos setters para os campos da entidade.
    public void setCode(String code) {
        this.code = code;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPrice(long price) {
        this.price = price;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public void setQrCode(String qrCode) {
        this.qrCode = qrCode;
    }

    public void setResponsible(String responsible) {
        this.responsible = responsible;
    }

    public void setValid(boolean valid) {
        isValid = valid;
    }

    public void setValidatedAt(LocalDateTime validatedAt) {
        this.validatedAt = validatedAt;
    }
    // --- FIM DOS SETTERS ---

    // --- Métodos de Relacionamento e Lógica ---
    // Método para definir o inventário ao qual este item pertence.
    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    // Método para adicionar uma observação à lista de observações do item.
    // Garante que a lista não seja nula antes de adicionar.
    public void addObservation(Observation observation) { // Este método já existia e é usado
        if (this.observations == null) {
            this.observations = new ArrayList<>(); // Inicializa a lista se for nula.
        }
        observations.add(observation); // Adiciona a observação à lista.
    }

    // Anotação que indica que este método será executado antes que a entidade seja persistida (salva) no banco de dados.
    @PrePersist
    // Método privado para gerar um QR Code único para o item, se ainda não tiver um.
    private void generateQrCode() {
        if (this.qrCode == null) {
            // Gera um UUID (Universally Unique Identifier) aleatório e o converte para String para usar como QR Code.
            this.qrCode = UUID.randomUUID().toString();
        }
    }
}