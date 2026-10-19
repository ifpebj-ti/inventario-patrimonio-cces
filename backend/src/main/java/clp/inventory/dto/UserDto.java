package clp.inventory.dto;

import clp.inventory.model.SectorAllocation;
import clp.inventory.model.User;
import java.util.Comparator;
import java.util.List;

public record UserDto(
    long id,
    String name,
    String email,
    List<Long> sectorIds,
    List<AllocationDto> allocations,
    boolean administrator,
    boolean active) {

  public static UserDto from(User user, List<SectorAllocation> allocations) {
    List<AllocationDto> dtos =
        allocations.stream()
            .map(AllocationDto::from)
            .sorted(Comparator.comparingLong(AllocationDto::sectorId))
            .toList();
    return new UserDto(
        user.getId(),
        user.getName(),
        user.getEmail(),
        dtos.stream().map(AllocationDto::sectorId).toList(),
        dtos,
        user.isAdministrator(),
        user.isActive());
  }
}
