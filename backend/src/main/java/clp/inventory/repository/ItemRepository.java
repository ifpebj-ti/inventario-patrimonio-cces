package clp.inventory.repository;

// Importa a classe Item do pacote clp.inventory.model.
// Isso indica que este repositório é responsável por operações de persistência para a entidade Item.
import clp.inventory.model.Item;
// Importa Page e Pageable do Spring Data.
// Page é um objeto que contém uma fatia de uma lista de dados, junto com metadados de paginação.
// Pageable é uma interface para informações de paginação (número da página, tamanho da página, ordenação).
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
// Importa JpaRepository do Spring Data JPA.
// JpaRepository fornece métodos CRUD (Create, Read, Update, Delete) e outras funcionalidades de persistência.
import org.springframework.data.jpa.repository.JpaRepository;
// Importa a anotação @Repository do Spring.
// @Repository é uma especialização de @Component que indica que a classe é um "repositório" de dados,
// permitindo que o Spring detecte-a e forneça recursos de acesso a dados.
import org.springframework.stereotype.Repository;

// Anotação que marca esta interface como um repositório Spring.
// O Spring criará uma implementação concreta desta interface em tempo de execução,
// fornecendo acesso aos dados do banco de dados para a entidade Item.
@Repository
// Declara a interface ItemRepository que estende JpaRepository.
// JpaRepository<Item, Long> significa que este repositório irá gerenciar a entidade 'Item'
// e que o tipo da chave primária da entidade 'Item' é 'Long'.
public interface ItemRepository extends JpaRepository<Item, Long> {

    /**
     * Busca e retorna uma página de itens associados a um ID de inventário específico.
     *
     * Este é um método de consulta derivado do Spring Data JPA. O Spring infere a consulta
     * automaticamente a partir do nome do método.
     * - 'findBy': indica que a consulta deve retornar registros.
     * - 'Inventory_Id': refere-se ao campo 'inventory' (relacionamento com a entidade Inventory)
     * e, dentro dele, ao campo 'id'. Isso significa que a consulta buscará itens
     * pelo ID do inventário ao qual estão associados.
     * - O parâmetro 'Pageable pageable' permite que a consulta retorne os resultados paginados,
     * ou seja, em "pedaços" de um determinado tamanho, facilitando a manipulação de grandes conjuntos de dados.
     *
     * @param inventoryId O ID do inventário cujos itens devem ser encontrados.
     * @param pageable Um objeto Pageable que contém informações sobre a paginação (número da página, tamanho da página, ordenação).
     * @return Uma Page (página) de objetos Item que pertencem ao inventário com o ID fornecido.
     */
    Page<Item> findByInventory_Id(Long inventoryId, Pageable pageable);
}