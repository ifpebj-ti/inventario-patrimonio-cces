package clp.inventory.repository;

import clp.inventory.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByEmail(String email);

  List<User> findByProfile_Id(long profileId);

  boolean existsByProfile_Id(long profileId);

  // Listagens não mostram a conta de sistema; findById/findByEmail seguem enxergando-a, porque o
  // login, o /auth/me e a autorização dependem delas.
  List<User> findAllBySystemUserFalse();

  List<User> findByProfile_IdAndSystemUserFalse(long profileId);
}
