package clp.inventory.service;

// Importa DTOs para Item e Observação.
import clp.inventory.dto.ItemDto;
import clp.inventory.dto.ObservationDto;
// Importa os modelos de dados para Inventário, Item, Observação e Usuário.
import clp.inventory.model.Inventory; // Não usado diretamente, mas pode ser para futuras extensões.
import clp.inventory.model.Item;
import clp.inventory.model.Observation;
import clp.inventory.model.User;     // Não usado diretamente, mas pode ser para futuras extensões.
// Importa os repositórios de Inventário e Item para acesso a dados.
import clp.inventory.repository.InventoryRepository; // Injetado no construtor, mas não utilizado.
import clp.inventory.repository.ItemRepository;
// Importa classes do Spring Data para paginação e ordenação de resultados.
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
// Importa HttpStatus do Spring para definir códigos de status HTTP em exceções.
import org.springframework.http.HttpStatus;
// Importa anotações do Spring para serviços e transações.
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Não usado diretamente neste arquivo, mas comum em serviços.
// Importa ResponseStatusException do Spring para lançar exceções com status HTTP.
import org.springframework.web.server.ResponseStatusException;

// Importa classes para manipulação de números decimais e coleções.
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

// Anotação que marca esta classe como um serviço Spring.
// Isso significa que o Spring a gerenciará como um bean e poderá injetá-la em outros componentes.
@Service
public class ItemService {

    // Injeção de dependência do repositório de itens.
    private final ItemRepository itemRepository;

    // Construtor da classe ItemService.
    // O Spring injetará o ItemRepository e o InventoryRepository.
    // Nota: O InventoryRepository é injetado, mas não é usado em nenhum método atualmente.
    public ItemService(ItemRepository itemRepository, InventoryRepository inventoryRepository) {
        this.itemRepository = itemRepository;
    }

    /**
     * Lista os itens de um inventário específico de forma paginada e ordenada pelo código.
     *
     * @param inventoryId O ID do inventário cujos itens serão listados.
     * @param page O número da página (baseado em 0).
     * @param size O número de itens por página.
     * @return Uma página de objetos Item.
     */
    public Page<Item> listInventoryItems(long inventoryId, int page, int size) {
        // Cria um objeto Pageable que define a paginação (número da página, tamanho)
        // e a ordenação dos resultados (por "code" ascendente).
        Pageable pageable = PageRequest.of(page, size, Sort.by("code"));
        // Chama o método do repositório para buscar os itens do inventário de forma paginada.
        return itemRepository.findByInventory_Id(inventoryId, pageable);
    }

    /**
     * Busca um item pelo seu ID.
     *
     * @param id O ID do item a ser buscado.
     * @return Um Optional contendo o Item se encontrado, ou um Optional vazio se não encontrado.
     */
    public Optional<Item> findById(Long id) {
        // Chama o método findById do repositório para buscar o item.
        return itemRepository.findById(id);
    }

    /**
     * Atualiza um item existente com os dados fornecidos em um DTO.
     *
     * @param updatedItem O ItemDto contendo os dados atualizados para o item.
     * @return O objeto Item atualizado e persistido no banco de dados.
     * @throws ResponseStatusException Se o item não for encontrado.
     */
    public Item updateItem(ItemDto updatedItem) {
        // 1. Busca o item existente no banco de dados pelo ID fornecido no DTO.
        // Se o item não for encontrado, lança uma ResponseStatusException com status NOT_FOUND (404).
        Item existingItem = itemRepository.findById(updatedItem.id()) // Usa getId() aqui
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item não encontrado com o ID: " + updatedItem.id()));

        // Converte o preço do item de String (do DTO) para long (para o modelo),
        // multiplicando por 100 (presumindo que o preço no DTO esteja em uma unidade inteira e o modelo em centavos).
        long priceParseLong = updatedItem.price() != null && !updatedItem.price().isEmpty()
                ? new BigDecimal(updatedItem.price()).multiply(BigDecimal.valueOf(100)).longValue()
                : 0L; // Se o preço for nulo ou vazio no DTO, define como 0.

        // 2. Atualiza as propriedades do item existente com os novos dados do DTO.
        // É importante ATUALIZAR as propriedades do objeto `existingItem` recuperado do banco,
        // em vez de criar um novo objeto Item e tentar salvá-lo, para que o JPA
        // possa gerenciar corretamente o estado da entidade e realizar a atualização.
        existingItem.setCode(updatedItem.code());
        existingItem.setName(""); // O campo 'name' é explicitamente definido como vazio.
        existingItem.setDescription(updatedItem.description());
        existingItem.setPrice(priceParseLong);
        existingItem.setLocale(updatedItem.locale());
        existingItem.setResponsible(updatedItem.responsible());
        existingItem.setValid(updatedItem.isValid()); // Atualiza o status de validade, se for editável.
        // Observações (notes) são tratadas via getNotes() e addObservation(), o que sugere um método separado para elas.

        // 3. Salva o item atualizado no banco de dados.
        // Como 'existingItem' é uma entidade gerenciada pelo JPA, o `save` irá persistir as alterações.
        return itemRepository.save(existingItem);
    }

    /**
     * Atualiza a lista de observações de um item.
     * Este método substitui todas as observações existentes do item pelas novas observações fornecidas.
     *
     * @param itemId O ID do item cujas observações serão atualizadas.
     * @param newNotesStrings Uma lista de ObservationDto contendo as novas observações.
     * @return O objeto Item com as observações atualizadas e persistidas.
     * @throws ResponseStatusException Se o item não for encontrado.
     */
    public Item updateItemNotes(Long itemId, List<ObservationDto> newNotesStrings) {
        // Busca o item existente no banco de dados pelo ID.
        // Se não encontrado, lança uma ResponseStatusException com status NOT_FOUND (404).
        Item existingItem = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item não encontrado com o ID: " + itemId));

        // Limpa todas as observações antigas associadas ao item.
        // Graças a `orphanRemoval = true` na anotação @OneToMany em Item,
        // as observações removidas desta lista serão excluídas do banco de dados.
        existingItem.observations().clear();

        // Adiciona as novas observações à lista do item.
        if (newNotesStrings != null && !newNotesStrings.isEmpty()) {
            for (ObservationDto noteText : newNotesStrings) {
                // Verifica se a observação não é nula e seu conteúdo não é vazio após remover espaços em branco.
                if (noteText != null && !noteText.content().trim().isEmpty()) {
                    // Cria um novo objeto Observation com o conteúdo do DTO (removendo espaços em branco).
                    Observation obs = new Observation(noteText.content().trim());
                    // Usa o método `addObservation` do Item para adicionar a nova observação.
                    // Este método é importante para manter a integridade do relacionamento bidirecional (se houver).
                    existingItem.addObservation(obs);
                }
            }
        }

        // Salva o item no banco de dados.
        // O JPA detectará as mudanças na coleção de observações e persistirá/removerá conforme necessário.
        return itemRepository.save(existingItem); // Salva o item com as observações atualizadas.
    }
}