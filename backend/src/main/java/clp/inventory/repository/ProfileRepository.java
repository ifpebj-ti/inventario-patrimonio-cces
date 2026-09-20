package clp.inventory.repository;

import clp.inventory.model.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, long id);
}
