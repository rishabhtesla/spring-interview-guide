package com.example.interview.common;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSupportTest {
    private NimbusJwtDecoder decoder;
    private NimbusJwtEncoder encoder;

    @BeforeEach
    void keys() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        RSAKey key = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate()).build();
        encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(pair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        decoder = JwtSupport.decoder(new ByteArrayResource(pem.getBytes(StandardCharsets.US_ASCII)), "interview-lab");
    }

    private String token(String issuer, String audience, Instant expiry) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject("11111111-1111-1111-1111-111111111111")
                .audience(List.of(audience)).issuedAt(expiry.minusSeconds(900)).expiresAt(expiry).build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }

    @Test
    void verifiesSignedTokenAndRejectsTampering() {
        String token = token("interview-lab", "interview-api", Instant.now().plusSeconds(900));
        assertThat(decoder.decode(token).getSubject()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThatThrownBy(() -> decoder.decode(token + "broken")).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWrongIssuerWrongAudienceAndExpiredTokens() {
        assertThatThrownBy(() -> decoder.decode(token("other", "interview-api", Instant.now().plusSeconds(900))))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(token("interview-lab", "other", Instant.now().plusSeconds(900))))
                .isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(token("interview-lab", "interview-api", Instant.now().minusSeconds(120))))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsUnsupportedEventVersion() {
        assertThatThrownBy(() -> new OrderCreated(2, null, null, null, null, 0, null, null).validate())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
