package com.unisphere.user;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    /** Emails are stored normalised to lower case; the unique index is on lower(email). */
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmail(@Param("email") String email);

    @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
    boolean existsByEmail(@Param("email") String email);

    @Query("select count(u) > 0 from User u join u.roles r where r = :role")
    boolean existsByRole(@Param("role") Role role);

    /** Minimal projection used on every authenticated request to check that a session is still valid. */
    @Query("select new com.unisphere.user.UserRepository$SessionState(u.status, u.sessionsValidAfter) from User u where u.id = :id")
    Optional<SessionState> findSessionState(@Param("id") Long id);

    record SessionState(UserStatus status, Instant sessionsValidAfter) {
    }
}
