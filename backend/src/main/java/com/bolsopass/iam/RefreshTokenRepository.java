package com.bolsopass.iam;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
  @Modifying
  @Query("update RefreshToken t set t.revoked = true where t.userId = :userId")
  void revokeAllForUser(String userId);
}
