package clp.inventory.service;

// Importa DTOs (Data Transfer Objects) para Inventário e Resultado de Processamento de Item.
import clp.inventory.dto.InventoryDto;
import clp.inventory.dto.ItemProcessingResult;
// Importa as classes de modelo para Inventário, Item e Usuário.
import clp.inventory.model.Inventory;
import clp.inventory.model.Item;
import clp.inventory.model.User;
// Importa o repositório de Inventário para operações de persistência.
import clp.inventory.repository.InventoryRepository;
// Importa anotações do Spring para serviços e transações.
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Importa classes utilitárias para listas e optionals.
import java.util.List;
import java.util.Optional;

// Anotação que marca esta classe como um serviço Spring.
// Isso significa que o Spring a gerenciará como um bean e poderá injetá-la em outros componentes.
@Service
public class InventoryService {

    // Injeção de dependência do repositório de inventário.
    private final InventoryRepository inventoryRepository;
    // Injeção de dependência do serviço de usuário.
    private final UserService userService;

    // Construtor da classe InventoryService.
    // O Spring injetará automaticamente o InventoryRepository e o UserService.
    public InventoryService(InventoryRepository inventoryRepository, UserService userService) {
        this.inventoryRepository = inventoryRepository;
        this.userService = userService;
    }

    /**
     * Cria um novo inventário para um usuário específico.
     * Realiza validações de entrada e unicidade do nome do inventário para o usuário.
     *
     * @param inventoryDto O DTO contendo os dados do novo inventário.
     * @param userId O ID do usuário que está criando o inventário.
     * @return O objeto Inventory recém-criado e persistido.
     * @throws IllegalArgumentException Se os dados do DTO forem inválidos.
     * @throws RuntimeException Se o usuário não for encontrado ou se já existir um inventário com o mesmo nome para o usuário.
     */
    public Inventory createInventory(InventoryDto inventoryDto, long userId) {
        // 1. Valida os dados de entrada do DTO do inventário.
        validateInventoryDto(inventoryDto);

        // 2. Busca o usuário pelo ID. Se não encontrado, lança uma exceção.
        User user = userService.findUserById(userId);
        // Valida se já existe um inventário com o mesmo nome para este usuário.
        validateInventoryUniqueness(inventoryDto.name(), userId);

        // 3. Cria uma nova instância de Inventory com os dados do DTO e o usuário.
        Inventory inventory = new Inventory(
                inventoryDto.name(),
                inventoryDto.description(),
                user
        );

        // Salva o novo inventário no banco de dados e o retorna.
        return inventoryRepository.save(inventory);
    }

    /**
     * Exclui um inventário existente, verificando se o inventário pertence ao usuário fornecido.
     * A anotação @Transactional garante que a operação seja atômica.
     *
     * @param inventoryId O ID do inventário a ser excluído.
     * @param userId O ID do usuário que está tentando excluir o inventário.
     * @throws RuntimeException Se o inventário não for encontrado ou não pertencer ao usuário.
     */
    @Transactional // Garante que a operação de exclusão seja executada dentro de uma transação.
    public void deleteInventory(long inventoryId, long userId) {
        // Busca o usuário pelo ID.
        User user = userService.findUserById(userId);
        // Busca o inventário pelo ID. Se não encontrado, lança uma exceção.
        Inventory inventory = inventoryRepository.findById(inventoryId).orElseThrow(() -> new RuntimeException("Invalid inventory"));
        // Verifica se o inventário pertence ao usuário. Se não, lança uma exceção de "usuário inválido".
        if (!inventory.User().equals(user)) {
            throw new RuntimeException("Invalid user");
        }
        // Exclui o inventário do banco de dados pelo seu ID.
        inventoryRepository.deleteById(inventoryId);
    }

    /**
     * Atualiza um inventário existente, verificando se ele pertence ao usuário.
     * A anotação @Transactional garante que a operação seja atômica.
     *
     * @param inventoryId O ID do inventário a ser atualizado.
     * @param inventoryDto O DTO contendo os dados atualizados do inventário.
     * @param userId O ID do usuário que está tentando atualizar o inventário.
     * @return O objeto Inventory atualizado e persistido.
     * @throws RuntimeException Se o inventário não for encontrado ou não pertencer ao usuário.
     */
    @Transactional // Garante que a operação de atualização seja executada dentro de uma transação.
    public Inventory updateInventory(long inventoryId, InventoryDto inventoryDto, long userId) {
        // Busca o inventário pelo ID. Se não encontrado, lança uma exceção.
        Inventory inventory = inventoryRepository.findById(inventoryId).orElseThrow(() -> new RuntimeException("Invalid inventory"));
        // Verifica se o inventário pertence ao usuário. Se não, lança uma exceção.
        if (!inventory.User().equals(userService.findUserById(userId))) {
            throw new RuntimeException("Invalid user");
        }
        // Atualiza o nome e a descrição do inventário com os dados do DTO.
        inventory.setName(inventoryDto.name());
        inventory.setDescription(inventoryDto.description());
        // Salva as alterações no inventário no banco de dados e o retorna.
        return inventoryRepository.save(inventory);
    }

    /**
     * Tenta adicionar um item a um inventário.
     * Verifica se o inventário existe e se o item (pelo código) já não está presente no inventário.
     * Retorna um ItemProcessingResult detalhando o sucesso ou falha da operação.
     *
     * @param inventoryId O ID do inventário ao qual o item será adicionado.
     * @param item O objeto Item a ser adicionado.
     * @param line O número da linha do item (útil para feedback em processamento de lote).
     * @return Um ItemProcessingResult indicando o resultado da adição.
     */
    public ItemProcessingResult addItemToInventory(long inventoryId, Item item, int line) {
        try {
            // Busca o inventário pelo ID. Se não encontrado, lança uma exceção.
            Inventory inventory = inventoryRepository.findById(inventoryId)
                    .orElseThrow(() -> new RuntimeException("Inventory not found with id: " + inventoryId));

            // Verifica se já existe um item com o mesmo código dentro deste inventário.
            boolean itemExist = inventory.items().stream()
                    .anyMatch(existingItem -> existingItem.code().equals(item.code()));

            // Se o item já existir, retorna um resultado de falha.
            if (itemExist) {
                return new ItemProcessingResult(
                        item.code(),
                        line,
                        false, // Indica falha.
                        "Item already exists in inventory" // Mensagem de erro.
                );
            }

            // Adiciona o item ao inventário. O método 'addItem' do Inventory também associa o inventário ao item.
            inventory.addItem(item);
            // Salva o inventário (que agora inclui o novo item) no banco de dados.
            inventoryRepository.save(inventory);

            // Retorna um resultado de sucesso.
            return new ItemProcessingResult(
                    item.code(),
                    line,
                    true, // Indica sucesso.
                    "Item added successfully to inventory" // Mensagem de sucesso.
            );
        } catch (Exception e) {
            // Em caso de qualquer exceção durante o processo, retorna um resultado de falha com a mensagem de erro.
            return new ItemProcessingResult(
                    item.code(),
                    line,
                    false, // Indica falha.
                    "Error processing item: " + e.getMessage() // Mensagem de erro.
            );
        }
    }

    /**
     * Remove um item específico de um inventário.
     *
     * @param inventoryId O ID do inventário do qual o item será removido.
     * @param itemId O ID do item a ser removido.
     * @return true se o item foi removido com sucesso, false se o item não foi encontrado no inventário.
     * @throws RuntimeException Se o inventário não for encontrado.
     */
    public boolean removeItemFromInventory(long inventoryId, long itemId) {
        // Busca o inventário pelo ID. Se não encontrado, lança uma exceção.
        Inventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new RuntimeException("Inventory not found with id: " + inventoryId));

        // Tenta encontrar o item a ser removido na lista de itens do inventário, pelo seu ID.
        Optional<Item> itemToRemove = inventory.items().stream()
                .filter(existingItem -> existingItem.id() == itemId)
                .findFirst();

        // Se o item não for encontrado no inventário, retorna false.
        if (itemToRemove.isEmpty()) {
            return false; // não encontrou o item
        }

        // Remove o item encontrado da lista de itens do inventário.
        inventory.removeItem(itemToRemove.get());
        // Salva o inventário (com o item removido) no banco de dados.
        inventoryRepository.save(inventory);

        // Retorna true, indicando que a remoção foi bem-sucedida.
        return true; // removeu com sucesso
    }

    /**
     * Obtém todos os inventários associados a um usuário específico.
     *
     * @param userId O ID do usuário.
     * @return Uma lista de objetos Inventory pertencentes ao usuário.
     */
    public List<Inventory> getUserInventories(long userId) {
        // Chama o repositório para buscar todos os inventários pelo ID do usuário.
        return inventoryRepository.findByUser_Id(userId);
    }

    /**
     * Método privado para validar o DTO de criação/atualização de inventário.
     *
     * @param inventoryDto O DTO a ser validado.
     * @throws IllegalArgumentException Se o nome do inventário for nulo ou vazio.
     */
    private void validateInventoryDto(InventoryDto inventoryDto) {
        // Valida se o nome do inventário não é nulo ou vazio.
        if (inventoryDto.name() == null || inventoryDto.name().trim().isEmpty()) {
            throw new IllegalArgumentException("Inventory name cannot be null or empty");
        }
    }

    /**
     * Método privado para validar a unicidade do nome do inventário para um usuário específico.
     * Um usuário não pode ter dois inventários com o mesmo nome.
     *
     * @param inventoryName O nome do inventário a ser verificado.
     * @param userId O ID do usuário.
     * @throws RuntimeException Se já existir um inventário com o mesmo nome para o usuário.
     */
    private void validateInventoryUniqueness(String inventoryName, long userId) {
        // Usa o repositório para verificar se já existe um inventário com o nome e ID de usuário fornecidos.
        if (inventoryRepository.existsByNameAndUser_Id(inventoryName, userId)) {
            throw new RuntimeException(
                    "Inventory with name '" + inventoryName + "' already exists for this user"
            );
        }
    }

    /**
     * Adiciona um único item a um inventário, sem validação de duplicidade de código como em addItemToInventory.
     * Pode ser usado para itens que não precisam de validação de unicidade de código ou quando essa validação ocorre em outra camada.
     *
     * @param inventoryId O ID do inventário.
     * @param item O item a ser adicionado.
     * @throws RuntimeException Se o inventário não for encontrado ou ocorrer um erro ao adicionar.
     */
    public void addSingleItemToInventory(long inventoryId, Item item) {
        try {
            // Busca o inventário pelo ID. Se não encontrado, lança uma exceção.
            Inventory inventory = inventoryRepository.findById(inventoryId)
                    .orElseThrow(() -> new RuntimeException("Inventory not found with id: " + inventoryId));

            // Adiciona o item ao inventário.
            inventory.addItem(item);
            // Salva o inventário com o novo item.
            inventoryRepository.save(inventory);
        } catch (Exception e) {
            // Em caso de qualquer erro, lança uma RuntimeException com a mensagem de erro.
            throw new RuntimeException("Error adding item: " + e.getMessage());
        }
    }
}