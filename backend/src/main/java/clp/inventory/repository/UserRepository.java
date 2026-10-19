package clp.inventory.repository;

import clp.inventory.model.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByEmail(String email);

  Optional<User> findByEmailIgnoreCase(String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where lower(u.email) = :email")
  Optional<User> findByEmailForUpdate(@Param("email") String email);

  // Listagens não mostram a conta de sistema; findById/findByEmail seguem enxergando-a, porque o
  // login, o /auth/me e a autorização dependem delas.
  List<User> findAllBySystemUserFalse();
}
