package clp.inventory.repository;

import clp.inventory.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, long id);
}
