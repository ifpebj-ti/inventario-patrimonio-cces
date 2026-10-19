package clp.inventory.service;

import clp.inventory.dto.SectorDto;
import clp.inventory.model.Sector;
import clp.inventory.repository.InventoryRepository;
import clp.inventory.repository.ItemAuditRepository;
import clp.inventory.repository.SectorAllocationRepository;
import clp.inventory.repository.SectorRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SectorService {

  private final SectorRepository sectorRepository;
  private final SectorAllocationRepository sectorAllocationRepository;
  private final InventoryRepository inventoryRepository;
  private final ItemAuditRepository itemAuditRepository;
  private final AuthorizationService authorizationService;

  public SectorService(
      SectorRepository sectorRepository,
      SectorAllocationRepository sectorAllocationRepository,
      InventoryRepository inventoryRepository,
      ItemAuditRepository itemAuditRepository,
      AuthorizationService authorizationService) {
    this.sectorRepository = sectorRepository;
    this.sectorAllocationRepository = sectorAllocationRepository;
    this.inventoryRepository = inventoryRepository;
    this.itemAuditRepository = itemAuditRepository;
    this.authorizationService = authorizationService;
  }

  @Transactional
  public Sector createSector(SectorDto dto, long actingUserId) {
    authorizationService.requireAdmin(actingUserId);
    validateFields(dto);
    if (sectorRepository.existsByName(dto.name())) {
      throw new IllegalArgumentException("Sector with name '" + dto.name() + "' already exists");
    }
    Sector sector = new Sector(dto.name().trim(), dto.code());
    return sectorRepository.save(sector);
  }

  @Transactional
  public Sector updateSector(long id, SectorDto dto, long actingUserId) {
    authorizationService.requireAdmin(actingUserId);
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
    authorizationService.requireAdmin(actingUserId);
    Sector sector = findSectorByIdInternal(id);
    sector.setActive(true);
    return sectorRepository.save(sector);
  }

  @Transactional
  public Sector deactivateSector(long id, long actingUserId) {
    authorizationService.requireAdmin(actingUserId);
    Sector sector = findSectorByIdInternal(id);
    sector.setActive(false);
    return sectorRepository.save(sector);
  }

  /**
   * Só o admin exclui, e só setor sem usuário, inventário nem auditoria associados; os demais só
   * podem ser ativados ou desativados, para preservar o histórico.
   */
  @Transactional
  public void deleteSector(long id, long actingUserId) {
    authorizationService.requireAdmin(actingUserId);
    Sector sector = findSectorByIdInternal(id);
    if (!isDeletable(sector.id())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "O setor possui usuários ou inventários associados e não pode ser excluído; desative-o.");
    }
    sectorRepository.delete(sector);
  }

  public boolean isDeletable(long sectorId) {
    return !sectorAllocationRepository.existsBySector_Id(sectorId)
        && !inventoryRepository.existsBySector_Id(sectorId)
        && !itemAuditRepository.existsByActorSector_IdOrTargetSector_Id(sectorId, sectorId);
  }

  /** Leitura de um setor: admin ou qualquer usuário alocado a ele. */
  public Sector findSectorById(long id, long actingUserId) {
    authorizationService.requireViewSector(actingUserId, id);
    return findSectorByIdInternal(id);
  }

  /** Dados de gestão do setor (como a lista de membros): admin ou gestor do setor. */
  public Sector findSectorForManagement(long id, long actingUserId) {
    authorizationService.requireSectorPermission(actingUserId, id);
    return findSectorByIdInternal(id);
  }

  /** Admin vê todos os setores; os demais perfis veem os setores a que estão alocados. */
  public List<Sector> listAllSectors(long actingUserId) {
    return authorizationService.isAdmin(actingUserId)
        ? sectorRepository.findAll()
        : sectorRepository.findAllocatedToUser(actingUserId);
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
