package clp.inventory.controller;

// Importa o DTO (Data Transfer Object) para User, que é usado para representar dados de usuário.
import clp.inventory.dto.UserDto;
// Importa o modelo User, que representa a entidade de usuário no domínio da aplicação.
import clp.inventory.model.User;
// Importa o UserService, que contém a lógica de negócio para operações relacionadas a usuários.
import clp.inventory.service.UserService;
// Importa as anotações do Spring Web para controladores REST.
import org.springframework.web.bind.annotation.*;

// Importa a classe List do pacote java.util para trabalhar com coleções de objetos.
import java.util.List;

// Anotação que indica que esta classe é um controlador REST.
@RestController
// Anotação que permite que requisições de qualquer origem (domínio) acessem este controlador.
// Isso é útil para permitir que aplicações frontend em diferentes domínios se comuniquem com esta API.
@CrossOrigin(origins = "*")
public class UserController {

    // Declara uma dependência do UserService.
    // A palavra-chave 'final' indica que esta referência não pode ser reatribuída após a inicialização.
    private final UserService userService;

    // Construtor da classe UserController.
    // O Spring irá injetar uma instância de UserService automaticamente quando criar um UserController.
    public UserController(UserService userService) {
        this.userService = userService; // Inicializa a dependência do UserService.
    }

    // Mapeia requisições HTTP POST para o caminho "/new-user".
    @PostMapping("/new-user")
    // Este método é responsável por criar um novo usuário.
    // @RequestBody indica que o corpo da requisição HTTP deve ser mapeado para um objeto UserDto.
    public User createUser(@RequestBody UserDto userDto) {
        // Chama o método createUser do UserService para realizar a lógica de criação do usuário
        // e retorna o objeto User criado.
        return userService.createUser(userDto);
    }

    // Mapeia requisições HTTP GET para o caminho "/users".
    @GetMapping("/users")
    // Este método é responsável por listar todos os usuários.
    // Ele retorna uma lista de objetos User.
    public List<User> listUsers() {
        // Chama o método listAllUsers do UserService para obter todos os usuários
        // e retorna a lista resultante.
        return userService.listAllUsers();
    }
}