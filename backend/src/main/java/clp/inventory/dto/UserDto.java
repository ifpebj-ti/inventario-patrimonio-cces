package clp.inventory.dto;

import clp.inventory.model.User;
import java.util.List;

public record UserDto(long id, String name, String email, List<Long> sectorIds, Long profileId) {

  public static UserDto from(User user, List<Long> sectorIds) {
    return new UserDto(
        user.getId(), user.getName(), user.getEmail(), sectorIds, user.getProfileId());
  }
}
