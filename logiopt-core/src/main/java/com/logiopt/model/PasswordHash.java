package com.logiopt.model;

import java.util.Arrays;

/**
 * A stored password: algorithm, iteration count, per-user salt and derived key. Never the password.
 */
public final class PasswordHash {

    private final String algorithm;
    private final int iterations;
    private final byte[] salt;
    private final byte[] hash;

    /**
     * Creates a stored password value.
     *
     * @param algorithm  JCA algorithm name, for example PBKDF2WithHmacSHA256
     * @param iterations iteration count
     * @param salt       per-user random salt
     * @param hash       derived key
     */
    public PasswordHash(String algorithm, int iterations, byte[] salt, byte[] hash) {
        if (algorithm == null || salt == null || hash == null || iterations < 1) {
            throw new IllegalArgumentException("Algorithm, salt, hash and a positive iteration count are required");
        }
        this.algorithm = algorithm;
        this.iterations = iterations;
        this.salt = salt.clone();
        this.hash = hash.clone();
    }

    /** @return JCA algorithm name */
    public String getAlgorithm() {
        return algorithm;
    }

    /** @return iteration count */
    public int getIterations() {
        return iterations;
    }

    /** @return copy of the salt */
    public byte[] getSalt() {
        return salt.clone();
    }

    /** @return copy of the derived key */
    public byte[] getHash() {
        return hash.clone();
    }

    @Override
    public String toString() {
        return "PasswordHash[" + algorithm + ", " + iterations + " iterations, salt " + salt.length + " bytes, hash "
                + hash.length + " bytes]";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PasswordHash)) {
            return false;
        }
        PasswordHash other = (PasswordHash) o;
        return algorithm.equals(other.algorithm) && iterations == other.iterations && Arrays.equals(salt, other.salt)
                && Arrays.equals(hash, other.hash);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(hash);
    }
}
