package com.unisphere.support;

import com.unisphere.common.config.UniSphereProperties;
import com.unisphere.user.Role;
import com.unisphere.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.test.util.ReflectionTestUtils;

public final class TestFixtures {

    private TestFixtures() {
    }

    public static UniSphereProperties properties() {
        return new UniSphereProperties(
                "http://localhost:5173",
                "UniSphere University",
                new UniSphereProperties.Security(
                        new UniSphereProperties.Jwt("unused-in-unit-tests", "unisphere", Duration.ofMinutes(15)),
                        Duration.ofDays(7),
                        true,
                        List.of(),
                        new UniSphereProperties.Lockout(5, Duration.ofMinutes(15)),
                        new UniSphereProperties.RateLimit(10),
                        Duration.ofHours(72),
                        Duration.ofMinutes(30)),
                new UniSphereProperties.Mail(false),
                new UniSphereProperties.BootstrapAdmin(null, null, null, null),
                new UniSphereProperties.Finance("KES"),
                new UniSphereProperties.Notifications("-", Duration.ofHours(48), Duration.ofDays(14)));
    }

    /** An active user with the given id and password hash, as if loaded from the database. */
    public static User activeUser(long id, String email, String passwordHash, Instant now, Role... roles) {
        User user = new User(email, "Test", "User", Set.of(roles), now);
        user.activate(passwordHash);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
