package clp.inventory.repository;

import clp.inventory.model.UserTokens;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserTokensRepository extends JpaRepository<UserTokens, Long> {

    Optional<UserTokens> findByToken(String token);
}
