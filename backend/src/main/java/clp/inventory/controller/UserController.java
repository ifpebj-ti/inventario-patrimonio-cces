package clp.inventory.controller;

import clp.inventory.dto.UserDto;
import clp.inventory.model.User;
import clp.inventory.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/new-user")
    public User createUser(@RequestBody UserDto userDto) {
        return userService.createUser(userDto);
    }

    @GetMapping("/users")
    public List<User> listUsers() {
        return userService.listAllUsers();
    }
}
