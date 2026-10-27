package com.logiopt.app;

/**
 * Result of a read-only schema check ({@link CoreRuntime#checkSchema()}): whether the database can be
 * used by this build, and the applied and required versions to show the user.
 */
public final class SchemaStatus {

    /** What the check found. */
    public enum State {
        /** The applied version is the one this build needs. */
        CURRENT,
        /** The applied version is newer than this build knows; usable, with a warning in the log. */
        NEWER,
        /** The database is behind this build; it must be migrated with MigrationTool. */
        BEHIND,
        /** The migration history records a failure or cannot be read. */
        FAILED,
        /** No connection could be opened. */
        UNREACHABLE
    }

    private final State state;
    private final Integer appliedVersion;
    private final int requiredVersion;

    /**
     * @param state           outcome
     * @param appliedVersion  applied version, or null when unknown (FAILED, UNREACHABLE)
     * @param requiredVersion version this build needs
     */
    public SchemaStatus(State state, Integer appliedVersion, int requiredVersion) {
        if (state == null) {
            throw new IllegalArgumentException("State is required");
        }
        this.state = state;
        this.appliedVersion = appliedVersion;
        this.requiredVersion = requiredVersion;
    }

    /** @return outcome */
    public State getState() {
        return state;
    }

    /** @return applied version, or null when it could not be determined */
    public Integer getAppliedVersion() {
        return appliedVersion;
    }

    /** @return version this build needs */
    public int getRequiredVersion() {
        return requiredVersion;
    }

    /** @return true if the application may use the database (CURRENT or NEWER) */
    public boolean isUsable() {
        return state == State.CURRENT || state == State.NEWER;
    }

    @Override
    public String toString() {
        return state + " (applied " + (appliedVersion == null ? "unknown" : appliedVersion) + ", required "
                + requiredVersion + ")";
    }
}
