package clp.inventory.repository;

// Importa a classe Inventory do pacote clp.inventory.model.
// Isso indica que este repositório é responsável por operações de persistência para a entidade Inventory.
import clp.inventory.model.Inventory;
// Importa JpaRepository do Spring Data JPA.
// JpaRepository fornece métodos CRUD (Create, Read, Update, Delete) e outras funcionalidades de persistência.
import org.springframework.data.jpa.repository.JpaRepository;
// Importa a anotação @Repository do Spring.
// @Repository é uma especialização de @Component que indica que a classe é um "repositório" de dados.
// O Spring pode então detectar este bean e traduzir quaisquer exceções específicas do DAO em exceções unificadas do Spring.
import org.springframework.stereotype.Repository;

// Importa a classe List do pacote java.util para trabalhar com coleções de objetos.
import java.util.List;

// Anotação que marca esta interface como um repositório Spring.
// Isso permite que o Spring crie uma implementação concreta desta interface em tempo de execução,
// fornecendo acesso aos dados do banco de dados para a entidade Inventory.
@Repository
// Declara a interface InventoryRepository que estende JpaRepository.
// JpaRepository<Inventory, Long> significa que este repositório irá gerenciar a entidade 'Inventory'
// e que o tipo da chave primária da entidade 'Inventory' é 'Long'.
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /**
     * Verifica se existe um inventário com um dado nome associado a um usuário específico.
     *
     * Este é um método de consulta derivado do Spring Data JPA. O Spring infere a consulta
     * automaticamente a partir do nome do método.
     * - 'existsBy': indica que a consulta deve verificar a existência de registros.
     * - 'Name': refere-se ao campo 'name' da entidade Inventory.
     * - 'AndUser_Id': refere-se ao campo 'user' (relacionamento com a entidade User) e,
     * dentro dele, ao campo 'id'. Isso significa que a consulta buscará
     * inventários pelo nome E pelo ID do usuário associado.
     *
     * @param name O nome do inventário a ser verificado.
     * @param userId O ID do usuário ao qual o inventário deve estar associado.
     * @return true se um inventário com o nome e ID de usuário especificados existir, false caso contrário.
     */
    boolean existsByNameAndUser_Id(String name, Long userId);

    /**
     * Busca e retorna uma lista de todos os inventários associados a um ID de usuário específico.
     *
     * Este também é um método de consulta derivado do Spring Data JPA.
     * - 'findBy': indica que a consulta deve retornar registros.
     * - 'User_Id': refere-se ao campo 'user' (relacionamento com a entidade User) e,
     * dentro dele, ao campo 'id'.
     *
     * @param userId O ID do usuário cujos inventários devem ser encontrados.
     * @return Uma lista de objetos Inventory que pertencem ao usuário com o ID fornecido.
     */
    List<Inventory> findByUser_Id(Long userId);
}