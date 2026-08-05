package clp.inventory.dto;

// Importa a classe Inventory do pacote clp.inventory.model.
// Isso indica que este DTO será usado para converter um objeto Inventory em um formato mais adequado para transferência de dados.
import clp.inventory.model.Inventory;

// Importa DateTimeFormatter do pacote java.time.format.
// Utilizado para formatar objetos de data e hora em strings específicas.
import java.time.format.DateTimeFormatter;

// Define um record Java chamado 'InventoryDto'.
// Records são classes de dados concisas e imutáveis, introduzidas no Java 16,
// ideais para Data Transfer Objects (DTOs), pois reduzem o boilerplate code
// (construtores, getters, equals, hashCode, toString são gerados automaticamente).
public record InventoryDto(
        // Componente 'id' do tipo long: representa o identificador único do inventário.
        long id,
        // Componente 'name' do tipo String: representa o nome do inventário.
        String name,
        // Componente 'description' do tipo String: representa a descrição do inventário.
        String description,
        // Componente 'createdAt' do tipo String: representa a data de criação do inventário, formatada como String.
        String createdAt
) {

    // Método estático de fábrica 'from'.
    // Este método é um padrão comum em DTOs para converter uma entidade de domínio (Inventory)
    // para o formato DTO (InventoryDto). Isso encapsula a lógica de conversão.
    public static InventoryDto from(Inventory inventory) {

        // Cria um DateTimeFormatter com o padrão "dd/MM/yyyy".
        // Isso significa que a data será formatada como dia/mês/ano (ex: 25/12/2023).
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        // Formata a data de criação do inventário.
        // Verifica se 'inventory.createdAt()' não é nulo.
        // Se não for nulo, formata a data usando o 'formatter'.
        // Se for nulo, 'formattedDate' será nulo.
        String formattedDate = inventory.createdAt() != null
                ? inventory.createdAt().format(formatter)
                : null;

        // Retorna uma nova instância de InventoryDto, preenchendo seus componentes
        // com os dados do objeto Inventory fornecido e a data formatada.
        return new InventoryDto(
                inventory.id(),          // Mapeia o ID do inventário.
                inventory.name(),        // Mapeia o nome do inventário.
                inventory.description(), // Mapeia a descrição do inventário.
                formattedDate            // Mapeia a data de criação formatada.
        );
    }
}