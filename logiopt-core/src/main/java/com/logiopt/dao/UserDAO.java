package com.logiopt.dao;

import com.logiopt.exception.DataAccessException;
import com.logiopt.model.AccountProfile;
import com.logiopt.model.PasswordHash;
import com.logiopt.model.Role;
import com.logiopt.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * JDBC access to the users table. Password data only leaves this class inside {@link Credentials}.
 */
public class UserDAO {

    /** MySQL error code for a duplicate key. */
    static final int ER_DUP_ENTRY = 1062;

    private static final String USER_COLUMNS = "user_id, email, name, role, enabled";
    private static final String CREDENTIAL_COLUMNS = USER_COLUMNS
            + ", password_hash, password_salt, password_iterations, password_algorithm";
    private static final String INSERT_SQL = "INSERT INTO users (name, email, password_hash, password_salt, "
            + "password_iterations, password_algorithm, role, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, TRUE)";
    private static final String FIND_BY_EMAIL_SQL = "SELECT " + CREDENTIAL_COLUMNS + " FROM users WHERE email = ?";
    private static final String FIND_ADMIN_SQL = "SELECT " + USER_COLUMNS + " FROM users WHERE role = 'ADMIN'";
    private static final String UPDATE_CREDENTIALS_SQL = "UPDATE users SET password_hash = ?, password_salt = ?, "
            + "password_iterations = ?, password_algorithm = ?, enabled = ? WHERE user_id = ?";
    private static final String LIST_SQL = "SELECT " + USER_COLUMNS + " FROM users ORDER BY user_id";
    private static final String PROFILE_SQL = "SELECT user_id, email, name, role, created_at FROM users WHERE user_id = ?";
    private static final String UPDATE_NAME_SQL = "UPDATE users SET name = ? WHERE user_id = ?";

    private final DBConnection db;

    /** A user together with their stored password; null hash when the account has no usable password. */
    public static final class Credentials {
        private final User user;
        private final PasswordHash passwordHash;

        Credentials(User user, PasswordHash passwordHash) {
            this.user = user;
            this.passwordHash = passwordHash;
        }

        /** @return the user */
        public User getUser() {
            return user;
        }

        /** @return stored password, or null if this account cannot sign in with a password */
        public PasswordHash getPasswordHash() {
            return passwordHash;
        }
    }

    /**
     * Creates the DAO.
     *
     * @param db connection factory
     */
    public UserDAO(DBConnection db) {
        this.db = db;
    }

    /**
     * Inserts an enabled user, unless the email is already registered.
     *
     * @param email normalized email
     * @param name  display name
     * @param hash  stored password
     * @param role  role
     * @return the new user id, or empty if the email (or, for ADMIN, the single admin slot) is taken
     * @throws DataAccessException on any other database failure
     */
    public Optional<Long> insert(String email, String name, PasswordHash hash, Role role) throws DataAccessException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, email);
            setHash(ps, 3, hash);
            ps.setString(7, role.name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new DataAccessException("No generated key returned for user");
                }
                return Optional.of(keys.getLong(1));
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            if (e.getErrorCode() == ER_DUP_ENTRY) {
                return Optional.empty();
            }
            throw new DataAccessException("Failed to insert user", e);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert user", e);
        }
    }

    /**
     * Finds a user and their stored password by normalized email.
     *
     * @param email normalized email
     * @return credentials, or empty if no such user
     * @throws DataAccessException on database failure
     */
    public Optional<Credentials> findCredentialsByEmail(String email) throws DataAccessException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(FIND_BY_EMAIL_SQL)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Credentials(mapUser(rs), mapHash(rs)));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load user", e);
        }
    }

    /**
     * Finds a user by id, without password data.
     *
     * @param userId user id
     * @return the user, or empty
     * @throws DataAccessException on database failure
     */
    public Optional<User> findById(long userId) throws DataAccessException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT " + USER_COLUMNS + " FROM users WHERE user_id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapUser(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load user " + userId, e);
        }
    }

    /**
     * Finds the administrator.
     *
     * @return the ADMIN user, or empty if none exists
     * @throws DataAccessException on database failure
     */
    public Optional<User> findAdmin() throws DataAccessException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(FIND_ADMIN_SQL);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(mapUser(rs)) : Optional.empty();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load the administrator", e);
        }
    }

    /**
     * Replaces a user's stored password and enabled flag.
     *
     * @param userId  user id
     * @param hash    new stored password
     * @param enabled whether the account may sign in
     * @throws DataAccessException on database failure
     */
    public void updateCredentials(long userId, PasswordHash hash, boolean enabled) throws DataAccessException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(UPDATE_CREDENTIALS_SQL)) {
            setHash(ps, 1, hash);
            ps.setBoolean(5, enabled);
            ps.setLong(6, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update user " + userId, e);
        }
    }

    /**
     * Loads a user's own account details, without password data.
     *
     * @param userId user id
     * @return the profile, or empty
     * @throws DataAccessException on database failure
     */
    public Optional<AccountProfile> findProfile(long userId) throws DataAccessException {
        try (Connection con = db.getConnection(); PreparedStatement ps = con.prepareStatement(PROFILE_SQL)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Timestamp created = rs.getTimestamp("created_at");
                return Optional.of(new AccountProfile(rs.getLong("user_id"), rs.getString("email"), rs.getString("name"),
                        Role.valueOf(rs.getString("role")), created == null ? null : created.toLocalDateTime()));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load the profile of user " + userId, e);
        }
    }

    /**
     * Changes a user's display name.
     *
     * @param userId user id
     * @param name   new name (already validated)
     * @return true if the user exists and was updated
     * @throws DataAccessException on database failure
     */
    public boolean updateName(long userId, String name) throws DataAccessException {
        try (Connection con = db.getConnection(); PreparedStatement ps = con.prepareStatement(UPDATE_NAME_SQL)) {
            ps.setString(1, name);
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to rename user " + userId, e);
        }
    }

    /**
     * Lists all users without password data.
     *
     * @return users ordered by id
     * @throws DataAccessException on database failure
     */
    public List<User> findAll() throws DataAccessException {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(LIST_SQL);
             ResultSet rs = ps.executeQuery()) {
            List<User> users = new ArrayList<>();
            while (rs.next()) {
                users.add(mapUser(rs));
            }
            return users;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to list users", e);
        }
    }

    private static void setHash(PreparedStatement ps, int first, PasswordHash hash) throws SQLException {
        Base64.Encoder b64 = Base64.getEncoder();
        ps.setString(first, b64.encodeToString(hash.getHash()));
        ps.setString(first + 1, b64.encodeToString(hash.getSalt()));
        ps.setInt(first + 2, hash.getIterations());
        ps.setString(first + 3, hash.getAlgorithm());
    }

    private static User mapUser(ResultSet rs) throws SQLException {
        return new User(rs.getLong("user_id"), rs.getString("email"), rs.getString("name"),
                Role.valueOf(rs.getString("role")), rs.getBoolean("enabled"));
    }

    private static PasswordHash mapHash(ResultSet rs) throws SQLException {
        String algorithm = rs.getString("password_algorithm");
        String salt = rs.getString("password_salt");
        int iterations = rs.getInt("password_iterations");
        if (algorithm == null || salt == null || iterations <= 0) {
            return null;
        }
        try {
            Base64.Decoder b64 = Base64.getDecoder();
            return new PasswordHash(algorithm, iterations, b64.decode(salt), b64.decode(rs.getString("password_hash")));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
