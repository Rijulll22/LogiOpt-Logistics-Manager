package com.logiopt.service;

import com.logiopt.model.PasswordHash;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * PBKDF2 password hashing with the JDK only: PBKDF2WithHmacSHA256, a 16-byte random salt per
 * password and a 256-bit derived key. The iteration count is stored with every hash, so it can be
 * raised later without breaking existing passwords.
 */
public class PasswordHasher {

    /** JCA algorithm used for new hashes. */
    public static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    /** Iterations for new hashes (OWASP 2023 recommendation for PBKDF2-HMAC-SHA256). */
    public static final int DEFAULT_ITERATIONS = 600_000;
    /** Lowest iteration count the users table accepts (chk_users_password). */
    public static final int MIN_ITERATIONS = 100_000;

    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private final int iterations;
    private final SecureRandom random;

    /**
     * Creates a hasher.
     *
     * @param iterations iterations for new hashes (at least {@link #MIN_ITERATIONS})
     * @param random     source of salts
     */
    public PasswordHasher(int iterations, SecureRandom random) {
        if (iterations < MIN_ITERATIONS || random == null) {
            throw new IllegalArgumentException("At least " + MIN_ITERATIONS + " iterations and a SecureRandom are required");
        }
        this.iterations = iterations;
        this.random = random;
    }

    /** @return iterations used for new hashes */
    public int getIterations() {
        return iterations;
    }

    /**
     * Hashes a password with a new random salt. The caller should clear the array afterwards.
     *
     * @param password password characters
     * @return stored password value
     */
    public PasswordHash hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return new PasswordHash(ALGORITHM, iterations, salt, derive(password, salt, iterations, ALGORITHM));
    }

    /**
     * Checks a password against a stored value in constant time with respect to the hash bytes.
     *
     * @param password password characters
     * @param stored   stored value (null never matches)
     * @return true if the password matches
     */
    public boolean verify(char[] password, PasswordHash stored) {
        if (stored == null || !ALGORITHM.equals(stored.getAlgorithm())) {
            return false;
        }
        byte[] expected = stored.getHash();
        byte[] actual = derive(password, stored.getSalt(), stored.getIterations(), stored.getAlgorithm());
        try {
            return MessageDigest.isEqual(expected, actual);
        } finally {
            Arrays.fill(actual, (byte) 0);
        }
    }

    /**
     * Tells whether a stored value uses weaker parameters than this hasher would use now.
     *
     * @param stored stored value
     * @return true if it should be re-hashed after a successful sign-in
     */
    public boolean needsRehash(PasswordHash stored) {
        return stored == null || !ALGORITHM.equals(stored.getAlgorithm()) || stored.getIterations() < iterations;
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations, String algorithm) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(algorithm).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(algorithm + " is not available", e);
        } finally {
            spec.clearPassword();
        }
    }
}
