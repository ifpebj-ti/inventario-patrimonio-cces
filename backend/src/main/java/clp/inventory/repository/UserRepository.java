package clp.inventory.repository;

// Importa a classe User do pacote clp.inventory.model.
// Isso indica que este repositório é responsável por operações de persistência para a entidade User.
import clp.inventory.model.User;
// Importa JpaRepository do Spring Data JPA.
// JpaRepository fornece métodos CRUD (Create, Read, Update, Delete) e outras funcionalidades de persistência.
import org.springframework.data.jpa.repository.JpaRepository;

// Importa a classe Optional do pacote java.util.
// Optional é um contêiner que pode ou não conter um valor não nulo.
// Ele é usado para evitar retornos nulos e melhorar a clareza do código em situações onde um valor pode estar ausente.
import java.util.Optional;

// Esta interface estende JpaRepository.
// JpaRepository<User, Long> significa que este repositório irá gerenciar a entidade 'User'
// e que o tipo da chave primária da entidade 'User' é 'Long'.
// O Spring Data JPA automaticamente gera implementações para os métodos declarados aqui
// com base nas convenções de nomes de métodos.
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Busca um usuário pelo seu endereço de e-mail.
     *
     * Este é um método de consulta derivado do Spring Data JPA. O Spring infere a consulta
     * automaticamente a partir do nome do método.
     * - 'findBy': indica que a consulta deve retornar registros.
     * - 'Email': refere-se ao campo 'email' da entidade User.
     *
     * @param email O endereço de e-mail do usuário a ser buscado.
     * @return Um Optional contendo o objeto User se um usuário com o e-mail especificado for encontrado,
     * ou um Optional vazio se nenhum usuário for encontrado.
     */
    Optional<User> findByEmail(String email);

    /**
     * Busca um usuário pelo seu ID.
     *
     * Este é outro método de consulta derivado. Embora 'findById(Long id)' já seja fornecido
     * pelo JpaRepository, este método `findUserById` oferece uma alternativa explícita,
     * seguindo um padrão de nomeação específico para o domínio, se desejado.
     *
     * @param id O ID do usuário a ser buscado.
     * @return Um Optional contendo o objeto User se um usuário com o ID especificado for encontrado,
     * ou um Optional vazio se nenhum usuário for encontrado.
     */
    Optional<User> findUserById(long id);
}