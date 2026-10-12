package clp.inventory.service;

import clp.inventory.dto.SectorDto;
import clp.inventory.model.Sector;
import clp.inventory.repository.SectorRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SectorService {

  private final SectorRepository sectorRepository;
  private final AuthorizationService authorizationService;

  public SectorService(SectorRepository sectorRepository, AuthorizationService authorizationService) {
    this.sectorRepository = sectorRepository;
    this.authorizationService = authorizationService;
  }

  @Transactional
  public Sector createSector(SectorDto dto, long actingUserId) {
    authorizationService.requirePermission(actingUserId, "ADMIN");
    validateFields(dto);
    if (sectorRepository.existsByName(dto.name())) {
      throw new IllegalArgumentException("Sector with name '" + dto.name() + "' already exists");
    }
    Sector sector = new Sector(dto.name().trim(), dto.code());
    return sectorRepository.save(sector);
  }

  @Transactional
  public Sector updateSector(long id, SectorDto dto, long actingUserId) {
    authorizationService.requirePermission(actingUserId, "ADMIN");
    validateFields(dto);
    Sector sector = findSectorByIdInternal(id);
    if (sectorRepository.existsByNameAndIdNot(dto.name(), id)) {
      throw new IllegalArgumentException("Sector with name '" + dto.name() + "' already exists");
    }
    sector.setName(dto.name().trim());
    sector.setCode(dto.code());
    return sectorRepository.save(sector);
  }

  @Transactional
  public Sector activateSector(long id, long actingUserId) {
    authorizationService.requirePermission(actingUserId, "ADMIN");
    Sector sector = findSectorByIdInternal(id);
    sector.setActive(true);
    return sectorRepository.save(sector);
  }

  @Transactional
  public Sector deactivateSector(long id, long actingUserId) {
    authorizationService.requirePermission(actingUserId, "ADMIN");
    Sector sector = findSectorByIdInternal(id);
    sector.setActive(false);
    return sectorRepository.save(sector);
  }

  public Sector findSectorById(long id, long actingUserId) {
    authorizationService.requireAnyPermission(actingUserId, "ADMIN", "MANAGE_SECTOR");
    return findSectorByIdInternal(id);
  }

  public List<Sector> listAllSectors(long actingUserId) {
    authorizationService.requireAnyPermission(actingUserId, "ADMIN", "MANAGE_SECTOR");
    return sectorRepository.findAll();
  }

  private Sector findSectorByIdInternal(long id) {
    return sectorRepository
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException("Sector not found with id: " + id));
  }

  private void validateFields(SectorDto dto) {
    if (dto.name() == null || dto.name().trim().isEmpty()) {
      throw new IllegalArgumentException("Sector name cannot be null or empty");
    }
    if (dto.name().trim().length() > 255) {
      throw new IllegalArgumentException("Sector name cannot exceed 255 characters");
    }
    if (dto.code() != null && dto.code().length() > 100) {
      throw new IllegalArgumentException("Sector code cannot exceed 100 characters");
    }
  }
}
