package com.atlascinema.booking.security.guest;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generates and verifies secure tokens granting access to guest booking payments.
 * Only SHA-256 token hashes should be persisted in the database.
 */
@Service
public class GuestBookingTokenService {

    private static final int TOKEN_SIZE_BYTES = 32;
    private static final String HASH_ALGORITHM = "SHA-256";

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates a cryptographically secure token for a guest booking.
     *
     * @return a URL-safe, randomly generated token
     */
    public String generateToken() {
        byte[] randomBytes = new byte[TOKEN_SIZE_BYTES];
        secureRandom.nextBytes(randomBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    /**
     * Calculates the SHA-256 hash of a guest booking token.
     *
     * @param guestBookingToken the original token
     * @return the hexadecimal hash suitable for database storage
     */
    public String hashToken(String guestBookingToken) {
        byte[] tokenHash = calculateHash(guestBookingToken);

        return HexFormat.of().formatHex(tokenHash);
    }

    /**
     * Verifies a supplied guest booking token against its stored hash.
     *
     * @param providedToken the token supplied by the guest
     * @param storedTokenHash the hash stored in PostgreSQL
     * @return true if the token matches the stored hash
     */
    public boolean matchesToken(
            String providedToken,
            String storedTokenHash
    ) {
        if (providedToken == null
                || providedToken.length() != 43
                || storedTokenHash == null
                || storedTokenHash.length() != 64) {
            return false;
        }

        try {
            byte[] providedTokenHash = calculateHash(providedToken);

            byte[] expectedTokenHash = HexFormat.of()
                    .parseHex(storedTokenHash);

            // compare hashes without short-circuiting on the first mismatched byte
            return MessageDigest.isEqual(providedTokenHash, expectedTokenHash);

        } catch (IllegalArgumentException _) {
            return false;
        }
    }

    private byte[] calculateHash(String token) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(HASH_ALGORITHM);

            return messageDigest.digest(token.getBytes(StandardCharsets.UTF_8));

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 hashing algorithm is unavailable",
                    exception);
        }
    }
}
