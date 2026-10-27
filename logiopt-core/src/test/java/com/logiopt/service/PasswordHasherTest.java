package com.logiopt.service;

import com.logiopt.model.PasswordHash;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher(PasswordHasher.MIN_ITERATIONS, new SecureRandom());

    @Test
    void verifiesOnlyTheRightPassword() {
        PasswordHash hash = hasher.hash("correct horse".toCharArray());
        assertTrue(hasher.verify("correct horse".toCharArray(), hash));
        assertFalse(hasher.verify("correct horsE".toCharArray(), hash));
        assertFalse(hasher.verify(new char[0], hash));
        assertFalse(hasher.verify("correct horse".toCharArray(), null));
    }

    @Test
    void storesAlgorithmIterationsAndPerPasswordSaltButNeverThePassword() {
        String password = "same-password-1";
        PasswordHash a = hasher.hash(password.toCharArray());
        PasswordHash b = hasher.hash(password.toCharArray());
        assertEquals(PasswordHasher.ALGORITHM, a.getAlgorithm());
        assertEquals(PasswordHasher.MIN_ITERATIONS, a.getIterations());
        assertEquals(16, a.getSalt().length);
        assertEquals(32, a.getHash().length);
        assertFalse(Arrays.equals(a.getSalt(), b.getSalt()));
        assertFalse(Arrays.equals(a.getHash(), b.getHash()));
        byte[] plain = password.getBytes(StandardCharsets.UTF_8);
        for (byte[] stored : new byte[][] {a.getHash(), a.getSalt()}) {
            assertFalse(new String(stored, StandardCharsets.ISO_8859_1).contains(password));
            assertNotEquals(Base64.getEncoder().encodeToString(plain), Base64.getEncoder().encodeToString(stored));
        }
        assertFalse(a.toString().contains(password));
    }

    @Test
    void verifiesWithTheStoredIterationCountAndFlagsWeakerHashes() {
        PasswordHasher stronger = new PasswordHasher(PasswordHasher.MIN_ITERATIONS + 1000, new SecureRandom());
        PasswordHash old = hasher.hash("rotate-me-now".toCharArray());
        assertTrue(stronger.verify("rotate-me-now".toCharArray(), old));
        assertTrue(stronger.needsRehash(old));
        assertFalse(hasher.needsRehash(old));
    }

    @Test
    void otherAlgorithmsAndWeakConfigurationsRejected() {
        PasswordHash hash = hasher.hash("password123".toCharArray());
        PasswordHash other = new PasswordHash("PBKDF2WithHmacSHA1", hash.getIterations(), hash.getSalt(), hash.getHash());
        assertFalse(hasher.verify("password123".toCharArray(), other));
        assertThrows(IllegalArgumentException.class, () -> new PasswordHasher(1000, new SecureRandom()));
        assertEquals(600_000, PasswordHasher.DEFAULT_ITERATIONS);
    }
}
