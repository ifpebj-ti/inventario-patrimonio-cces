package clp.inventory.service;

import clp.inventory.dto.AuthenticatedUserDto;
import clp.inventory.model.User;
import clp.inventory.repository.UserRepository;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticatedUserService {

  private final UserRepository userRepository;
  private final SectorAllocationService sectorAllocationService;

  public AuthenticatedUserService(
      UserRepository userRepository, SectorAllocationService sectorAllocationService) {
    this.userRepository = userRepository;
    this.sectorAllocationService = sectorAllocationService;
  }

  @Transactional(readOnly = true)
  public AuthenticatedUserDto getById(long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new NoSuchElementException("User not found with id: " + userId));
    return AuthenticatedUserDto.from(user, sectorAllocationService.sectorIdsOf(userId));
  }
}
