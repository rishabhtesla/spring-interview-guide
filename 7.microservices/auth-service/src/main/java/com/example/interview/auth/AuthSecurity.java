package com.example.interview.auth;

import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class AuthSecurity {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    JwtEncoder jwtEncoder(@Value("${app.jwt.private-key}") Resource privateKey,
                          @Value("${app.jwt.public-key}") Resource publicKey) throws IOException {
        RSAPrivateKey privateRsa;
        RSAPublicKey publicRsa;
        try (InputStream input = privateKey.getInputStream()) {
            privateRsa = RsaKeyConverters.pkcs8().convert(input);
        }
        try (InputStream input = publicKey.getInputStream()) {
            publicRsa = RsaKeyConverters.x509().convert(input);
        }
        RSAKey key = new RSAKey.Builder(publicRsa).privateKey(privateRsa).keyID("local-lab").build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        // No cookie/session authentication: these JSON endpoints do not use ambient credentials.
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/register", "/auth/login", "/actuator/health").permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}
