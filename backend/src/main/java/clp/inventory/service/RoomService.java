package clp.inventory.service;

import clp.inventory.dto.RoomDto;
import clp.inventory.model.Room;
import clp.inventory.model.Sector;
import clp.inventory.repository.ItemAuditRepository;
import clp.inventory.repository.ItemRepository;
import clp.inventory.repository.RoomRepository;
import clp.inventory.repository.SectorRepository;
import clp.inventory.utils.RoomNames;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Salas por setor. Quem lê o setor lista as salas; criar, renomear e excluir é do administrador e
 * do gestor do setor. O nome é único no setor sem diferenciar caixa, acento e espaços.
 */
@Service
public class RoomService {

  private final RoomRepository roomRepository;
  private final SectorRepository sectorRepository;
  private final ItemRepository itemRepository;
  private final ItemAuditRepository itemAuditRepository;
  private final AuthorizationService authorizationService;

  public RoomService(
      RoomRepository roomRepository,
      SectorRepository sectorRepository,
      ItemRepository itemRepository,
      ItemAuditRepository itemAuditRepository,
      AuthorizationService authorizationService) {
    this.roomRepository = roomRepository;
    this.sectorRepository = sectorRepository;
    this.itemRepository = itemRepository;
    this.itemAuditRepository = itemAuditRepository;
    this.authorizationService = authorizationService;
  }

  @Transactional(readOnly = true)
  public List<RoomDto> list(long sectorId, long actingUserId) {
    requireSector(sectorId);
    authorizationService.requireViewSector(actingUserId, sectorId);
    return roomRepository.findBySector_IdOrderByName(sectorId).stream().map(this::toDto).toList();
  }

  @Transactional
  public RoomDto create(long sectorId, String name, long actingUserId) {
    Sector sector = requireSector(sectorId);
    authorizationService.requireSectorPermission(actingUserId, sectorId);
    String display = validName(name);
    String normalized = RoomNames.normalize(display);
    if (roomRepository.findBySector_IdAndNormalizedName(sectorId, normalized).isPresent()) {
      throw duplicate(display);
    }
    try {
      // saveAndFlush: se outra requisição criou a mesma sala entre a conferência e a gravação, a
      // constraint recusa aqui e vira 400, em vez de 500 no commit.
      return toDto(roomRepository.saveAndFlush(new Room(sector, display, normalized)));
    } catch (DataIntegrityViolationException e) {
      throw duplicate(display);
    }
  }

  @Transactional
  public RoomDto rename(long roomId, String name, long actingUserId) {
    Room room = requireRoom(roomId);
    long sectorId = room.sector().id();
    authorizationService.requireSectorPermission(actingUserId, sectorId);
    String display = validName(name);
    String normalized = RoomNames.normalize(display);
    roomRepository
        .findBySector_IdAndNormalizedName(sectorId, normalized)
        .filter(other -> other.id() != room.id())
        .ifPresent(
            other -> {
              throw duplicate(display);
            });
    room.rename(display, normalized);
    try {
      return toDto(roomRepository.saveAndFlush(room));
    } catch (DataIntegrityViolationException e) {
      throw duplicate(display);
    }
  }

  @Transactional
  public void delete(long roomId, long actingUserId) {
    Room room = requireRoom(roomId);
    authorizationService.requireSectorPermission(actingUserId, room.sector().id());
    if (!isDeletable(room.id())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "A sala possui itens vinculados e não pode ser excluída; mova os itens antes.");
    }
    roomRepository.delete(room);
  }

  /** O setor tem ao menos uma sala cadastrada (a partir daí validar exige informar a sala). */
  @Transactional(readOnly = true)
  public boolean sectorHasRooms(long sectorId) {
    return roomRepository.existsBySector_Id(sectorId);
  }

  /** Sala do setor pelo id; erro 400 quando não existe ou é de outro setor. */
  @Transactional(readOnly = true)
  public Room requireRoomOfSector(long roomId, long sectorId) {
    return roomRepository
        .findById(roomId)
        .filter(room -> room.sector().id() == sectorId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A sala informada não pertence ao setor do item."));
  }

  /**
   * Acha a sala do setor pelo nome (sem diferenciar caixa, acento e espaços) ou cria. Nome vazio
   * devolve nulo. Usado pela importação da planilha e pelo cadastro avulso de item.
   */
  @Transactional
  public Room findOrCreate(Sector sector, String name) {
    String display = RoomNames.display(name);
    if (display == null) {
      return null;
    }
    if (display.length() > RoomNames.MAX_LENGTH) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "O nome da sala deve ter no máximo " + RoomNames.MAX_LENGTH + " caracteres.");
    }
    String normalized = RoomNames.normalize(display);
    // Insere só se faltar e relê: duas importações simultâneas com a mesma sala acabam com a mesma
    // linha, sem violação de constraint (que abortaria a transação e daria 500).
    roomRepository.insertIfAbsent(sector.id(), display, normalized);
    return roomRepository
        .findBySector_IdAndNormalizedName(sector.id(), normalized)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.CONFLICT, "Não foi possível registrar a sala; tente novamente."));
  }

  /** Nomes de sala (já normalizados) que existem no setor: para a pré-validação da planilha. */
  @Transactional(readOnly = true)
  public List<Room> roomsOf(long sectorId) {
    return roomRepository.findBySector_IdOrderByName(sectorId);
  }

  public long countRooms(long sectorId) {
    return roomRepository.countBySector_Id(sectorId);
  }

  private boolean isDeletable(long roomId) {
    return itemRepository.countByRoom_Id(roomId) == 0
        && itemRepository.countByFoundRoom_Id(roomId) == 0
        && !itemAuditRepository.existsByFoundRoom_IdOrExpectedRoom_Id(roomId, roomId);
  }

  private RoomDto toDto(Room room) {
    return RoomDto.from(room, itemRepository.countByRoom_Id(room.id()), isDeletable(room.id()));
  }

  private String validName(String name) {
    String display = RoomNames.display(name);
    if (display == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o nome da sala.");
    }
    if (display.length() > RoomNames.MAX_LENGTH) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "O nome da sala deve ter no máximo " + RoomNames.MAX_LENGTH + " caracteres.");
    }
    return display;
  }

  private ResponseStatusException duplicate(String name) {
    return new ResponseStatusException(
        HttpStatus.BAD_REQUEST, "Já existe uma sala \"" + name + "\" neste setor.");
  }

  private Sector requireSector(long sectorId) {
    return sectorRepository
        .findById(sectorId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Setor não encontrado."));
  }

  private Room requireRoom(long roomId) {
    return roomRepository
        .findById(roomId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sala não encontrada."));
  }
}
