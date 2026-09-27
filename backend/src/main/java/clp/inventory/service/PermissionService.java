package clp.inventory.service;

import clp.inventory.dto.PermissionDto;
import clp.inventory.model.Permission;
import clp.inventory.repository.PermissionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public PermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    @Transactional
    public Permission createPermission(PermissionDto dto) {
        validate(dto);
        if (permissionRepository.existsByName(dto.name())) {
            throw new IllegalArgumentException("Permission with name '" + dto.name() + "' already exists");
        }
        Permission permission = new Permission(dto.name().trim(), dto.description());
        return permissionRepository.save(permission);
    }

    @Transactional
    public Permission updatePermission(long id, PermissionDto dto) {
        validate(dto);
        Permission permission = findPermissionById(id);
        if (permissionRepository.existsByNameAndIdNot(dto.name(), id)) {
            throw new IllegalArgumentException("Permission with name '" + dto.name() + "' already exists");
        }
        permission.setName(dto.name().trim());
        permission.setDescription(dto.description());
        return permissionRepository.save(permission);
    }

    @Transactional
    public void deletePermission(long id) {
        if (!permissionRepository.existsById(id)) {
            throw new NoSuchElementException("Permission not found with id: " + id);
        }
        try {
            permissionRepository.deleteById(id);
            permissionRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("Permission is in use and cannot be deleted");
        }
    }

    public Permission findPermissionById(long id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Permission not found with id: " + id));
    }

    public List<Permission> listAllPermissions() {
        return permissionRepository.findAll();
    }

    private void validate(PermissionDto dto) {
        if (dto.name() == null || dto.name().trim().isEmpty()) {
            throw new IllegalArgumentException("Permission name cannot be null or empty");
        }
        if (dto.name().trim().length() > 100) {
            throw new IllegalArgumentException("Permission name cannot exceed 100 characters");
        }
        if (dto.description() != null && dto.description().length() > 255) {
            throw new IllegalArgumentException("Permission description cannot exceed 255 characters");
        }
    }
}
