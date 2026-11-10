
package com.atlascinema.booking.security.guest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GuestBookingTokenServiceTest {

    private GuestBookingTokenService guestBookingTokenService;

    @BeforeEach
    void setUp() {
        guestBookingTokenService = new GuestBookingTokenService();
    }

    @Test
    void generateToken_shouldReturnUrlSafeTokenWithExpectedLength() {
        String token = guestBookingTokenService.generateToken();

        assertThat(token)
                .hasSize(43)
                .matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void generateToken_shouldGenerateDifferentTokens() {
        String firstToken = guestBookingTokenService.generateToken();
        String secondToken = guestBookingTokenService.generateToken();

        assertThat(firstToken).isNotEqualTo(secondToken);
    }

    @Test
    void hashToken_shouldReturnSha256Hash() {
        String token = guestBookingTokenService.generateToken();

        String tokenHash = guestBookingTokenService.hashToken(token);

        assertThat(tokenHash)
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .isNotEqualTo(token);
    }

    @Test
    void hashToken_shouldReturnSameHashForSameToken() {
        String token = guestBookingTokenService.generateToken();

        String firstHash = guestBookingTokenService.hashToken(token);
        String secondHash = guestBookingTokenService.hashToken(token);

        assertThat(firstHash).isEqualTo(secondHash);
    }

    @Test
    void matchesToken_shouldReturnTrueForCorrectToken() {
        String token = guestBookingTokenService.generateToken();
        String storedHash = guestBookingTokenService.hashToken(token);

        boolean matches = guestBookingTokenService.matchesToken(
                token,
                storedHash);

        assertThat(matches).isTrue();
    }

    @Test
    void matchesToken_shouldReturnFalseForDifferentToken() {
        String originalToken = guestBookingTokenService.generateToken();
        String differentToken = guestBookingTokenService.generateToken();

        String storedHash = guestBookingTokenService.hashToken(originalToken);

        boolean matches = guestBookingTokenService.matchesToken(
                differentToken,
                storedHash);

        assertThat(matches).isFalse();
    }

    @Test
    void matchesToken_shouldReturnFalseWhenTokenIsNull() {
        String validToken = guestBookingTokenService.generateToken();
        String storedHash = guestBookingTokenService.hashToken(validToken);

        boolean matches = guestBookingTokenService.matchesToken(
                null,
                storedHash);

        assertThat(matches).isFalse();
    }

    @Test
    void matchesToken_shouldReturnFalseWhenStoredHashIsNull() {
        String token = guestBookingTokenService.generateToken();

        boolean matches = guestBookingTokenService.matchesToken(token, null);

        assertThat(matches).isFalse();
    }

    @Test
    void matchesToken_shouldReturnFalseForInvalidTokenLength() {
        String token = guestBookingTokenService.generateToken();
        String storedHash = guestBookingTokenService.hashToken(token);

        boolean matches = guestBookingTokenService.matchesToken(
                "invalid-token",
                storedHash);

        assertThat(matches).isFalse();
    }

    @Test
    void matchesToken_shouldReturnFalseForMalformedStoredHash() {
        String token = guestBookingTokenService.generateToken();

        boolean matches = guestBookingTokenService.matchesToken(token,
                "z".repeat(64));

        assertThat(matches).isFalse();
    }

    @Test
    void matchesToken_shouldReturnFalseForIncorrectStoredHashLength() {
        String token = guestBookingTokenService.generateToken();

        boolean matches = guestBookingTokenService.matchesToken(token, "abc123");

        assertThat(matches).isFalse();
    }
}
