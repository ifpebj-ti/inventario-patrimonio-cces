package clp.inventory.repository;

import clp.inventory.model.Profile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, Long> {

  Optional<Profile> findByName(String name);

  boolean existsByName(String name);

  boolean existsByNameAndIdNot(String name, long id);
}
