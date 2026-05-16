package com.example.tgs_dev.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for JwtService.
 *
 * No Spring context needed: we generate a throwaway HS512 key per test,
 * inject the @Value fields via ReflectionTestUtils, and call the service directly.
 */
@DisplayName("JwtService")
class JwtServiceTest {

    private static final String ISSUER            = "test-issuer";
    private static final long   ACCESS_EXPIRY_MS  = 3_600_000L;   // 1 hour
    private static final long   REFRESH_EXPIRY_MS = 86_400_000L;  // 24 hours

    private JwtService sut;
    private SecretKey  testKey;

    @BeforeEach
    void setUp() {
        testKey = Jwts.SIG.HS512.key().build();
        sut = new JwtService(testKey);
        ReflectionTestUtils.setField(sut, "expirationMs",        ACCESS_EXPIRY_MS);
        ReflectionTestUtils.setField(sut, "refreshExpirationMs", REFRESH_EXPIRY_MS);
        ReflectionTestUtils.setField(sut, "issuer",              ISSUER);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UserDetails user(String username, String... roles) {
        return new User(username, "pw",
                List.of(roles).stream().map(SimpleGrantedAuthority::new).toList());
    }

    private JwtService serviceWithIssuer(String issuer) {
        JwtService s = new JwtService(testKey);
        ReflectionTestUtils.setField(s, "expirationMs",        ACCESS_EXPIRY_MS);
        ReflectionTestUtils.setField(s, "refreshExpirationMs", REFRESH_EXPIRY_MS);
        ReflectionTestUtils.setField(s, "issuer",              issuer);
        return s;
    }

    // ── generateAccessToken ───────────────────────────────────────────────────

    @Nested @DisplayName("generateAccessToken")
    class GenerateAccessToken {

        @Test @DisplayName("subject equals the username")
        void subject_equalsUsername() {
            String token = sut.generateAccessToken(user("jdoe", "ROLE_ADMIN"));
            assertThat(sut.extractUsername(token)).isEqualTo("jdoe");
        }

        @Test @DisplayName("type claim is 'access'")
        void typeClaim_isAccess() {
            String token = sut.generateAccessToken(user("jdoe", "ROLE_ADMIN"));
            assertThat(sut.validateAndExtract(token).get("type", String.class))
                    .isEqualTo("access");
        }

        @Test @DisplayName("issuer claim matches the configured issuer")
        void issuerClaim_matchesConfig() {
            String token = sut.generateAccessToken(user("jdoe", "ROLE_ADMIN"));
            assertThat(sut.validateAndExtract(token).getIssuer()).isEqualTo(ISSUER);
        }

        @Test @DisplayName("roles claim contains all user authorities")
        void rolesClaim_containsAllAuthorities() {
            String token = sut.generateAccessToken(user("jdoe", "ROLE_ADMIN", "ROLE_USER"));
            @SuppressWarnings("unchecked")
            List<String> roles = sut.validateAndExtract(token).get("roles", List.class);
            assertThat(roles).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
        }

        @Test @DisplayName("two consecutive calls produce different tokens (unique jti)")
        void consecutiveCalls_produceUniqueTokens() {
            UserDetails u = user("jdoe", "ROLE_USER");
            assertThat(sut.generateAccessToken(u)).isNotEqualTo(sut.generateAccessToken(u));
        }
    }

    // ── generateRefreshToken ──────────────────────────────────────────────────

    @Nested @DisplayName("generateRefreshToken")
    class GenerateRefreshToken {

        @Test @DisplayName("type claim is 'refresh'")
        void typeClaim_isRefresh() {
            String token = sut.generateRefreshToken(user("jdoe", "ROLE_ADMIN"));
            assertThat(sut.validateAndExtract(token).get("type", String.class))
                    .isEqualTo("refresh");
        }

        @Test @DisplayName("subject equals the username")
        void subject_equalsUsername() {
            String token = sut.generateRefreshToken(user("ana", "ROLE_USER"));
            assertThat(sut.extractUsername(token)).isEqualTo("ana");
        }

        @Test @DisplayName("access and refresh tokens for the same user are different")
        void accessAndRefreshTokens_areDifferent() {
            UserDetails u = user("jdoe", "ROLE_USER");
            assertThat(sut.generateAccessToken(u))
                    .isNotEqualTo(sut.generateRefreshToken(u));
        }
    }

    // ── validateAndExtract ────────────────────────────────────────────────────

    @Nested @DisplayName("validateAndExtract")
    class ValidateAndExtract {

        @Test @DisplayName("valid token returns claims without throwing")
        void validToken_returnsClaims() {
            String token = sut.generateAccessToken(user("ana", "ROLE_USER"));
            Claims claims = sut.validateAndExtract(token);
            assertThat(claims.getSubject()).isEqualTo("ana");
        }

        @Test @DisplayName("tampered signature throws JwtException")
        void tamperedSignature_throwsJwtException() {
            String token   = sut.generateAccessToken(user("ana", "ROLE_USER"));
            String tampered = token.substring(0, token.length() - 6) + "XXXXXX";
            assertThatThrownBy(() -> sut.validateAndExtract(tampered))
                    .isInstanceOf(JwtException.class);
        }

        @Test @DisplayName("token signed with a different key throws JwtException")
        void differentKey_throwsJwtException() {
            SecretKey otherKey     = Jwts.SIG.HS512.key().build();
            JwtService otherService = new JwtService(otherKey);
            ReflectionTestUtils.setField(otherService, "expirationMs",        ACCESS_EXPIRY_MS);
            ReflectionTestUtils.setField(otherService, "refreshExpirationMs", REFRESH_EXPIRY_MS);
            ReflectionTestUtils.setField(otherService, "issuer",              ISSUER);

            String foreign = otherService.generateAccessToken(user("eve", "ROLE_USER"));
            assertThatThrownBy(() -> sut.validateAndExtract(foreign))
                    .isInstanceOf(JwtException.class);
        }

        @Test @DisplayName("expired token throws ExpiredJwtException")
        void expiredToken_throwsExpiredJwtException() {
            // Set expiry to 1 hour in the past — JJWT will reject it immediately
            ReflectionTestUtils.setField(sut, "expirationMs", -3_600_000L);
            String token = sut.generateAccessToken(user("old", "ROLE_USER"));
            assertThatThrownBy(() -> sut.validateAndExtract(token))
                    .isInstanceOf(ExpiredJwtException.class);
        }

        @Test @DisplayName("token issued by wrong issuer throws JwtException")
        void wrongIssuer_throwsJwtException() {
            // Token signed with ISSUER = "other-issuer"; sut requires "test-issuer"
            String tokenWithOtherIssuer = serviceWithIssuer("other-issuer")
                    .generateAccessToken(user("eve", "ROLE_USER"));
            assertThatThrownBy(() -> sut.validateAndExtract(tokenWithOtherIssuer))
                    .isInstanceOf(JwtException.class);
        }

        @Test @DisplayName("completely invalid string throws JwtException")
        void garbage_throwsJwtException() {
            assertThatThrownBy(() -> sut.validateAndExtract("not.a.jwt.at.all"))
                    .isInstanceOf(JwtException.class);
        }
    }

    // ── extractUsername ───────────────────────────────────────────────────────

    @Nested @DisplayName("extractUsername")
    class ExtractUsername {

        @Test @DisplayName("returns the subject from a valid token")
        void validToken_returnsSubject() {
            String token = sut.generateAccessToken(user("carlos", "ROLE_USER"));
            assertThat(sut.extractUsername(token)).isEqualTo("carlos");
        }
    }
}
