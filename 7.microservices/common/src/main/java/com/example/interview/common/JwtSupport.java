package com.example.interview.common;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;

import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

public final class JwtSupport {
    private JwtSupport() {}

    public static NimbusJwtDecoder decoder(Resource publicKey, String issuer) throws IOException {
        RSAPublicKey key;
        try (InputStream input = publicKey.getInputStream()) {
            key = RsaKeyConverters.x509().convert(input);
        }
        // Verification needs only the public key; downstream services cannot mint tokens.
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                jwt -> jwt.getAudience().contains("interview-api")
                        && jwt.getExpiresAt() != null
                        && jwt.getSubject() != null
                        && jwt.getSubject().matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(
                                new OAuth2Error("invalid_token", "Invalid audience, expiry or subject", null))));
        return decoder;
    }
}
