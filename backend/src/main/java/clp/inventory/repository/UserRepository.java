package clp.inventory.repository;

import clp.inventory.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    List<User> findBySector_Id(long sectorId);

    List<User> findByProfile_Id(long profileId);
}
