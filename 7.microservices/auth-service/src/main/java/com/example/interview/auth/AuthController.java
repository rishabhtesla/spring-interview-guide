package com.example.interview.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AuthController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final String issuer;
    private final String dummyHash;

    public AuthController(JdbcTemplate jdbc, PasswordEncoder passwords, JwtEncoder encoder,
                          @Value("${app.jwt.issuer}") String issuer) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.encoder = encoder;
        this.issuer = issuer;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public record Credentials(@NotBlank @Email @Size(max = 254) String email,
                              @NotBlank @Size(min = 12, max = 72) String password) {}
    public record Account(UUID id, String email) {}
    public record Token(String accessToken, String tokenType, long expiresIn) {}
    private record StoredAccount(UUID id, String hash) {}

    @PostMapping("/auth/register")
    public ResponseEntity<Account> register(@Valid @RequestBody Credentials request) {
        checkPasswordBytes(request.password());
        UUID id = UUID.randomUUID();
        String email = normalize(request.email());
        int inserted = jdbc.update("""
                INSERT INTO accounts(id, email, password_hash) VALUES (?, ?, ?)
                ON CONFLICT (email) DO NOTHING
                """, id, email, passwords.encode(request.password()));
        if (inserted == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account already exists");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(new Account(id, email));
    }

    @PostMapping("/auth/login")
    public Token login(@Valid @RequestBody Credentials request) {
        checkPasswordBytes(request.password());
        List<StoredAccount> matches = jdbc.query(
                "SELECT id, password_hash FROM accounts WHERE email = ?",
                (row, index) -> new StoredAccount(row.getObject("id", UUID.class), row.getString("password_hash")),
                normalize(request.email()));
        // Perform a hash comparison even for unknown accounts; never disclose which credential failed.
        String hash = matches.isEmpty() ? dummyHash : matches.getFirst().hash();
        boolean valid = passwords.matches(request.password(), hash);
        if (!valid || matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer)
                .subject(matches.getFirst().id().toString()).audience(List.of("interview-api"))
                .issuedAt(now).expiresAt(now.plusSeconds(900)).id(UUID.randomUUID().toString())
                .claim("scope", "orders notifications").build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
        return new Token(token, "Bearer", 900);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static void checkPasswordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password exceeds BCrypt's 72-byte limit");
        }
    }
}
