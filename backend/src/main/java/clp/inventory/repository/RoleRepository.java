package clp.inventory.repository;

import clp.inventory.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

  boolean existsByName(String name);

  boolean existsByNameAndIdNot(String name, long id);
}
