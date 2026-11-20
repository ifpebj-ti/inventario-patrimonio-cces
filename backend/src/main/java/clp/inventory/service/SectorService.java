package clp.inventory.service;

import clp.inventory.dto.SectorDto;
import clp.inventory.model.Sector;
import clp.inventory.repository.InventoryRepository;
import clp.inventory.repository.ItemAuditRepository;
import clp.inventory.repository.RoomRepository;
import clp.inventory.repository.SectorAllocationRepository;
import clp.inventory.repository.SectorRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.dao.DataIntegrityViolationException;
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
  private final RoomRepository roomRepository;
  private final AuthorizationService authorizationService;

  public SectorService(
      SectorRepository sectorRepository,
      SectorAllocationRepository sectorAllocationRepository,
      InventoryRepository inventoryRepository,
      ItemAuditRepository itemAuditRepository,
      RoomRepository roomRepository,
      AuthorizationService authorizationService) {
    this.sectorRepository = sectorRepository;
    this.sectorAllocationRepository = sectorAllocationRepository;
    this.inventoryRepository = inventoryRepository;
    this.itemAuditRepository = itemAuditRepository;
    this.roomRepository = roomRepository;
    this.authorizationService = authorizationService;
  }

  @Transactional
  public Sector createSector(SectorDto dto, long actingUserId) {
    authorizationService.requireAdmin(actingUserId);
    validateFields(dto);
    String name = dto.name().trim();
    if (sectorRepository.existsByName(name)) {
      throw duplicateName(name);
    }
    try {
      // saveAndFlush: a violação da constraint por uma corrida aparece aqui e vira 400.
      return sectorRepository.saveAndFlush(new Sector(name, dto.code()));
    } catch (DataIntegrityViolationException e) {
      throw duplicateName(name);
    }
  }

  @Transactional
  public Sector updateSector(long id, SectorDto dto, long actingUserId) {
    authorizationService.requireAdmin(actingUserId);
    validateFields(dto);
    Sector sector = findSectorByIdInternal(id);
    String name = dto.name().trim();
    if (sectorRepository.existsByNameAndIdNot(name, id)) {
      throw duplicateName(name);
    }
    sector.setName(name);
    sector.setCode(dto.code());
    try {
      return sectorRepository.saveAndFlush(sector);
    } catch (DataIntegrityViolationException e) {
      throw duplicateName(name);
    }
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
    // Setor excluível não tem inventário nem auditoria, então as salas dele não têm itens ligados.
    roomRepository.deleteBySector_Id(sector.id());
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

  /** Admin vê todos os setores; os demais, os setores em que o cargo deles dá leitura. */
  public List<Sector> listAllSectors(long actingUserId) {
    return authorizationService.isAdmin(actingUserId)
        ? sectorRepository.findAll()
        : sectorRepository.findViewableByUser(actingUserId, AuthorizationService.VIEW_PERMISSIONS);
  }

  private Sector findSectorByIdInternal(long id) {
    return sectorRepository
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException("Sector not found with id: " + id));
  }

  private IllegalArgumentException duplicateName(String name) {
    return new IllegalArgumentException("Sector with name '" + name + "' already exists");
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
