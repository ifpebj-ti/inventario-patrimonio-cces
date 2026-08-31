package clp.inventory.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserDto(
        long id,

        @NotBlank(message = "O nome é obrigatório") String name,

        @NotBlank(message = "O email é obrigatório") @Email(message = "Email inválido") String email,

        @NotBlank(message = "A senha é obrigatória") String password,

        String telephone,

        @NotBlank boolean verified
) {
}
