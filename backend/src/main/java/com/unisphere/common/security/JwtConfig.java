package com.unisphere.common.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.unisphere.common.config.UniSphereProperties;
import com.unisphere.user.UserRepository;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * HMAC-SHA256 signed access tokens using Spring Security's Nimbus integration (no hand-rolled JWT parsing).
 * The decoder validates signature, expiry, issuer, and — via {@link ActiveSessionValidator} — that the
 * account is still active and the token was not issued before the user's sessions were invalidated.
 */
@Configuration
public class JwtConfig {

    private static final int MIN_SECRET_BYTES = 32;

    @Bean
    public SecretKey jwtSigningKey(UniSphereProperties properties) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(properties.security().jwt().secret());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SECRET must be Base64 encoded", e);
        }
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must decode to at least 32 bytes (256 bits)");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSigningKey, UniSphereProperties properties, UserRepository users) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.security().jwt().issuer()),
                new ActiveSessionValidator(users)));
        return decoder;
    }
}
