package clp.inventory.repository;

import clp.inventory.model.RevokedToken;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {

  // ON CONFLICT: dois logouts do mesmo token ao mesmo tempo não podem falhar um contra o outro.
  @Modifying
  @Query(
      value =
          "INSERT INTO im_revoked_token (jti, user_id, expires_at) VALUES (:jti, :userId,"
              + " :expiresAt) ON CONFLICT (jti) DO NOTHING",
      nativeQuery = true)
  void revoke(
      @Param("jti") String jti,
      @Param("userId") long userId,
      @Param("expiresAt") Instant expiresAt);

  @Modifying
  @Query("DELETE FROM RevokedToken t WHERE t.expiresAt < :now")
  int deleteExpired(@Param("now") Instant now);
}
