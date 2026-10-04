package com.unisphere.common.security;

import com.unisphere.common.config.UniSphereProperties;
import com.unisphere.user.Role;
import com.unisphere.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues short-lived access tokens. Claims are minimal: subject (user id), roles, standard time claims. */
@Service
public class AccessTokenService {

    public static final String ROLES_CLAIM = "roles";

    private final JwtEncoder encoder;
    private final UniSphereProperties.Jwt settings;
    private final Clock clock;

    public AccessTokenService(JwtEncoder encoder, UniSphereProperties properties, Clock clock) {
        this.encoder = encoder;
        this.settings = properties.security().jwt();
        this.clock = clock;
    }

    public AccessToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(settings.accessTokenTtl());
        List<String> roles = user.getRoles().stream().map(Role::name).sorted().toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(settings.issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(ROLES_CLAIM, roles)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(token, expiresAt, settings.accessTokenTtl().toSeconds());
    }

    public record AccessToken(String value, Instant expiresAt, long expiresInSeconds) {
    }
}
