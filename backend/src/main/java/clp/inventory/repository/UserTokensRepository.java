package clp.inventory.repository;

// Importa a classe UserTokens do pacote clp.inventory.model.
// Isso indica que este repositório é responsável por operações de persistência para a entidade UserTokens.
import clp.inventory.model.UserTokens;
// Importa JpaRepository do Spring Data JPA.
// JpaRepository fornece métodos CRUD (Create, Read, Update, Delete) e outras funcionalidades de persistência.
import org.springframework.data.jpa.repository.JpaRepository;

// Importa a classe Optional do pacote java.util.
// Optional é um contêiner que pode ou não conter um valor não nulo.
// Ele é usado para evitar retornos nulos e melhorar a clareza do código em situações onde um valor pode estar ausente.
import java.util.Optional;

// Esta interface estende JpaRepository.
// JpaRepository<UserTokens, Long> significa que este repositório irá gerenciar a entidade 'UserTokens'
// e que o tipo da chave primária da entidade 'UserTokens' é 'Long'.
// O Spring Data JPA automaticamente gera implementações para os métodos declarados aqui
// com base nas convenções de nomes de métodos.
public interface UserTokensRepository extends JpaRepository<UserTokens, Long> {

    /**
     * Busca um token de usuário pelo seu valor (a string do token).
     *
     * Este é um método de consulta derivado do Spring Data JPA. O Spring infere a consulta
     * automaticamente a partir do nome do método.
     * - 'findBy': indica que a consulta deve retornar registros.
     * - 'Token': refere-se ao campo 'token' da entidade UserTokens.
     *
     * @param token A string do token a ser buscada.
     * @return Um Optional contendo o objeto UserTokens se um token com o valor especificado for encontrado,
     * ou um Optional vazio se nenhum token for encontrado.
     */
    Optional<UserTokens> findByToken(String token);
}