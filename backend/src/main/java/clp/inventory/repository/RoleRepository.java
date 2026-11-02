package clp.inventory.repository;

import clp.inventory.model.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

  Optional<Role> findByName(String name);

  boolean existsByName(String name);

  boolean existsByNameAndIdNot(String name, long id);
}
