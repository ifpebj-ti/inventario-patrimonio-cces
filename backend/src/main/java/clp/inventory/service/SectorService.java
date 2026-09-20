package clp.inventory.service;

import clp.inventory.dto.SectorDto;
import clp.inventory.model.Organization;
import clp.inventory.model.Sector;
import clp.inventory.repository.OrganizationRepository;
import clp.inventory.repository.SectorRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
public class SectorService {

    private final SectorRepository sectorRepository;
    private final OrganizationRepository organizationRepository;

    public SectorService(SectorRepository sectorRepository, OrganizationRepository organizationRepository) {
        this.sectorRepository = sectorRepository;
        this.organizationRepository = organizationRepository;
    }

    @Transactional
    public Sector createSector(SectorDto dto) {
        validateFields(dto);
        Organization organization = findOrganization(dto.organizationId());
        Sector parentSector = resolveParent(dto.parentSectorId(), organization);
        validateUniqueName(organization, parentSector, dto.name(), null);

        Sector sector = new Sector(dto.name().trim(), dto.code(), organization, parentSector);
        return sectorRepository.save(sector);
    }

    @Transactional
    public Sector updateSector(long id, SectorDto dto) {
        validateFields(dto);
        Sector sector = findSectorById(id);
        Sector parentSector = resolveParent(dto.parentSectorId(), sector.organization());
        if (parentSector != null) {
            ensureNoCycle(sector, parentSector);
        }
        validateUniqueName(sector.organization(), parentSector, dto.name(), id);

        sector.setName(dto.name().trim());
        sector.setCode(dto.code());
        sector.setParentSector(parentSector);
        return sectorRepository.save(sector);
    }

    @Transactional
    public void deleteSector(long id) {
        if (!sectorRepository.existsById(id)) {
            throw new NoSuchElementException("Sector not found with id: " + id);
        }
        try {
            sectorRepository.deleteById(id);
            sectorRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("Sector has child sectors and cannot be deleted");
        }
    }

    public Sector findSectorById(long id) {
        return sectorRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Sector not found with id: " + id));
    }

    public List<Sector> listSectors(Long organizationId) {
        return organizationId != null
                ? sectorRepository.findByOrganization_Id(organizationId)
                : sectorRepository.findAll();
    }

    private Organization findOrganization(long organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found with id: " + organizationId));
    }

    private Sector resolveParent(Long parentSectorId, Organization organization) {
        if (parentSectorId == null) {
            return null;
        }
        Sector parent = sectorRepository.findById(parentSectorId)
                .orElseThrow(() -> new IllegalArgumentException("Parent sector not found with id: " + parentSectorId));
        if (parent.organization().id() != organization.id()) {
            throw new IllegalArgumentException("Parent sector must belong to the same organization");
        }
        return parent;
    }

    private void ensureNoCycle(Sector sector, Sector newParent) {
        Sector current = newParent;
        while (current != null) {
            if (current.id() == sector.id()) {
                throw new IllegalArgumentException("Sector cannot be its own ancestor");
            }
            current = current.parentSector();
        }
    }

    private void validateUniqueName(Organization organization, Sector parentSector, String name, Long excludeId) {
        boolean duplicate = sectorRepository.findByOrganization_Id(organization.id()).stream()
                .filter(s -> excludeId == null || s.id() != excludeId)
                .filter(s -> Objects.equals(
                        s.parentSector() != null ? s.parentSector().id() : null,
                        parentSector != null ? parentSector.id() : null))
                .anyMatch(s -> s.name().equals(name.trim()));
        if (duplicate) {
            throw new IllegalArgumentException("Sector with name '" + name + "' already exists under the same parent");
        }
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
