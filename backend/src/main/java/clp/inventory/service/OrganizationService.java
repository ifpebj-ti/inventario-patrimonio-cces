package clp.inventory.service;

import clp.inventory.dto.OrganizationDto;
import clp.inventory.model.Organization;
import clp.inventory.repository.OrganizationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    @Transactional
    public Organization createOrganization(OrganizationDto dto) {
        validate(dto);
        if (organizationRepository.existsByName(dto.name())) {
            throw new IllegalArgumentException("Organization with name '" + dto.name() + "' already exists");
        }
        Organization organization = new Organization(dto.name().trim(), dto.acronym(), dto.domain());
        return organizationRepository.save(organization);
    }

    @Transactional
    public Organization updateOrganization(long id, OrganizationDto dto) {
        validate(dto);
        Organization organization = findOrganizationById(id);
        if (organizationRepository.existsByNameAndIdNot(dto.name(), id)) {
            throw new IllegalArgumentException("Organization with name '" + dto.name() + "' already exists");
        }
        organization.setName(dto.name().trim());
        organization.setAcronym(dto.acronym());
        organization.setDomain(dto.domain());
        return organizationRepository.save(organization);
    }

    @Transactional
    public void deleteOrganization(long id) {
        if (!organizationRepository.existsById(id)) {
            throw new NoSuchElementException("Organization not found with id: " + id);
        }
        try {
            organizationRepository.deleteById(id);
            organizationRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("Organization has sectors and cannot be deleted");
        }
    }

    public Organization findOrganizationById(long id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Organization not found with id: " + id));
    }

    public List<Organization> listAllOrganizations() {
        return organizationRepository.findAll();
    }

    private void validate(OrganizationDto dto) {
        if (dto.name() == null || dto.name().trim().isEmpty()) {
            throw new IllegalArgumentException("Organization name cannot be null or empty");
        }
        if (dto.name().trim().length() > 255) {
            throw new IllegalArgumentException("Organization name cannot exceed 255 characters");
        }
        if (dto.acronym() != null && dto.acronym().length() > 50) {
            throw new IllegalArgumentException("Organization acronym cannot exceed 50 characters");
        }
        if (dto.domain() != null && dto.domain().length() > 255) {
            throw new IllegalArgumentException("Organization domain cannot exceed 255 characters");
        }
    }
}
