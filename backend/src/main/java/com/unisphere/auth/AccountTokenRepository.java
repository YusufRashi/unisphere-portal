package com.unisphere.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {

    @Query("select t from AccountToken t join fetch t.user where t.tokenHash = :hash and t.purpose = :purpose")
    Optional<AccountToken> findByHashAndPurpose(@Param("hash") String hash, @Param("purpose") AccountTokenPurpose purpose);

    /** Invalidates earlier outstanding tokens so only the most recently issued link works. */
    @Modifying(flushAutomatically = true)
    @Query("update AccountToken t set t.usedAt = :now where t.user.id = :userId and t.purpose = :purpose and t.usedAt is null")
    int invalidateOutstanding(@Param("userId") Long userId, @Param("purpose") AccountTokenPurpose purpose,
                              @Param("now") Instant now);
}
