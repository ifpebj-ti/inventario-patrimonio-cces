package clp.inventory.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record CreateUserDto(
    @Size(max = 100) String name,
    @NotBlank @Email @Size(max = 255) String email,
    @Positive Long profileId,
    @NotNull @Size(max = 100) List<@NotNull @Positive Long> sectorIds) {}
