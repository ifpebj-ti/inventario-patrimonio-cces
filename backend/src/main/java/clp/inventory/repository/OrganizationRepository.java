package clp.inventory.repository;

import clp.inventory.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, long id);
}
