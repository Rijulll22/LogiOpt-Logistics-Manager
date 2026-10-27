package com.logiopt.service;

import com.logiopt.dao.UserDAO;
import com.logiopt.exception.AccountChangeException;
import com.logiopt.exception.DataAccessException;
import com.logiopt.exception.InvalidRegistrationException;
import com.logiopt.exception.NotFoundException;
import com.logiopt.model.AccountProfile;
import com.logiopt.model.PasswordHash;
import com.logiopt.model.Role;
import com.logiopt.model.User;
import com.logiopt.util.Strings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Registration, sign-in and admin seeding.
 *
 * <ul>
 *     <li>Registration always creates an enabled CUSTOMER. A taken or reserved email gives the same
 *     outcome as a new one (nothing tells the caller which addresses exist), and the password is
 *     hashed in every case so the response time does not reveal it either.</li>
 *     <li>Sign-in returns one generic failure for unknown email, wrong password and disabled
 *     account; unknown emails are checked against a dummy hash to keep the timing similar.
 *     Failures are throttled per (email, client address).</li>
 *     <li>The single ADMIN ({@link #ADMIN_EMAIL}) is created or updated from LOGIOPT_ADMIN_PASSWORD;
 *     without it nothing is seeded.</li>
 * </ul>
 */
public class AuthService {

    /** Fixed email of the seeded administrator. */
    public static final String ADMIN_EMAIL = "admin@logiopt.local";
    /** Environment variable holding the administrator password. */
    public static final String ENV_ADMIN_PASSWORD = "LOGIOPT_ADMIN_PASSWORD";
    /** Minimum password length. */
    public static final int MIN_PASSWORD_LENGTH = 8;
    /** Maximum password length (bounds the work per request). */
    public static final int MAX_PASSWORD_LENGTH = 128;
    /** Maximum email length (users.email VARCHAR(255)). */
    public static final int MAX_EMAIL_LENGTH = 255;
    /** Maximum name length (users.name VARCHAR(100)). */
    public static final int MAX_NAME_LENGTH = 100;
    /** Refusal for the administrator, whose password comes from the environment. */
    public static final String ADMIN_PASSWORD_FIXED = "The administrator password is set by the LOGIOPT_ADMIN_PASSWORD "
            + "environment variable and cannot be changed here.";
    /** Generic refusal of a wrong current password. */
    public static final String WRONG_CURRENT_PASSWORD = "The current password is incorrect.";
    /** Refusal while too many wrong passwords were entered for this account and client. */
    public static final String PASSWORD_LOCKED = "Too many wrong passwords. Please wait a few minutes and try again.";
    /** The new password and its confirmation differ. */
    public static final String PASSWORDS_DIFFER = "The new passwords do not match.";

    private static final Logger LOG = Logger.getLogger(AuthService.class.getName());
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final String ADMIN_NAME = "Administrator";

    private final UserDAO users;
    private final PasswordHasher hasher;
    private final LoginThrottle throttle;
    private final PasswordHash dummyHash;

    /** Outcome of a sign-in attempt. */
    public enum LoginStatus {
        /** Signed in. */
        SUCCESS,
        /** Unknown email, wrong password or disabled account. */
        INVALID,
        /** Too many failures for this email and client address. */
        LOCKED
    }

    /** Result of {@link #login(String, char[], String)}. */
    public static final class LoginResult {
        private final LoginStatus status;
        private final User user;

        LoginResult(LoginStatus status, User user) {
            this.status = status;
            this.user = user;
        }

        /** @return outcome */
        public LoginStatus getStatus() {
            return status;
        }

        /** @return the signed-in user, or null unless SUCCESS */
        public User getUser() {
            return user;
        }
    }

    /** What {@link #seedAdmin(String)} did. */
    public enum AdminSeedResult {
        /** No password configured; nothing seeded. */
        SKIPPED_NOT_CONFIGURED,
        /** Configured password shorter than the minimum; nothing seeded. */
        SKIPPED_INVALID_PASSWORD,
        /** The admin email belongs to a non-admin account, or another admin exists; nothing changed. */
        SKIPPED_CONFLICT,
        /** Admin account created. */
        CREATED,
        /** Existing admin's password or enabled flag updated to match the configuration. */
        UPDATED,
        /** Existing admin already matches the configuration. */
        UNCHANGED
    }

    /**
     * Creates the service.
     *
     * @param users    user DAO
     * @param hasher   password hasher
     * @param throttle sign-in throttle
     */
    public AuthService(UserDAO users, PasswordHasher hasher, LoginThrottle throttle) {
        if (users == null || hasher == null || throttle == null) {
            throw new IllegalArgumentException("User DAO, hasher and throttle are required");
        }
        this.users = users;
        this.hasher = hasher;
        this.throttle = throttle;
        this.dummyHash = hasher.hash("logiopt-timing-dummy".toCharArray());
    }

    /**
     * Normalizes an email for storage and lookup: trimmed and lowercase.
     *
     * @param email raw email
     * @return normalized email ("" for null)
     */
    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Registers a CUSTOMER. Returns normally both when the account was created and when the email was
     * already registered (or is the reserved admin email), so callers show the same response.
     *
     * @param email    email
     * @param name     display name
     * @param password password (cleared by this method)
     * @throws InvalidRegistrationException if any field is invalid (all errors together)
     * @throws DataAccessException          on database failure
     */
    public void register(String email, String name, char[] password)
            throws InvalidRegistrationException, DataAccessException {
        try {
            String normalized = normalizeEmail(email);
            String trimmedName = name == null ? "" : name.trim();
            List<String> errors = new ArrayList<>();
            if (normalized.isEmpty()) {
                errors.add("Email is required.");
            } else if (normalized.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(normalized).matches()) {
                errors.add("Enter a valid email address.");
            }
            errors.addAll(nameErrors(trimmedName));
            errors.addAll(passwordErrors(password));
            if (!errors.isEmpty()) {
                throw new InvalidRegistrationException(errors);
            }
            PasswordHash hash = hasher.hash(password);
            if (ADMIN_EMAIL.equals(normalized)) {
                LOG.warning("Registration attempted with the reserved administrator email; ignored");
                return;
            }
            Optional<Long> id = users.insert(normalized, trimmedName, hash, Role.CUSTOMER);
            if (id.isPresent()) {
                LOG.info("Registered user " + id.get());
            } else {
                LOG.info("Registration for an already registered email ignored");
            }
        } finally {
            clear(password);
        }
    }

    /**
     * Signs a user in.
     *
     * @param email         submitted email
     * @param password      submitted password (cleared by this method)
     * @param clientAddress client IP address for throttling
     * @return SUCCESS with the user, or INVALID / LOCKED
     * @throws DataAccessException on database failure
     */
    public LoginResult login(String email, char[] password, String clientAddress) throws DataAccessException {
        try {
            String normalized = normalizeEmail(email);
            if (throttle.isLocked(normalized, clientAddress)) {
                return new LoginResult(LoginStatus.LOCKED, null);
            }
            char[] submitted = password == null ? new char[0] : password;
            Optional<UserDAO.Credentials> found = normalized.isEmpty() || submitted.length > MAX_PASSWORD_LENGTH
                    ? Optional.empty() : users.findCredentialsByEmail(normalized);
            boolean matches;
            if (found.isPresent() && found.get().getPasswordHash() != null) {
                matches = hasher.verify(submitted, found.get().getPasswordHash());
            } else {
                hasher.verify(submitted, dummyHash);
                matches = false;
            }
            if (!matches || !found.get().getUser().isEnabled()) {
                throttle.recordFailure(normalized, clientAddress);
                return new LoginResult(throttle.isLocked(normalized, clientAddress) ? LoginStatus.LOCKED
                        : LoginStatus.INVALID, null);
            }
            throttle.recordSuccess(normalized, clientAddress);
            User user = found.get().getUser();
            if (hasher.needsRehash(found.get().getPasswordHash())) {
                users.updateCredentials(user.getUserId(), hasher.hash(submitted), true);
            }
            return new LoginResult(LoginStatus.SUCCESS, user);
        } finally {
            clear(password);
        }
    }

    /**
     * Creates or updates the single administrator {@link #ADMIN_EMAIL} from the configured password.
     * The configured password is the source of truth: if it changes, the stored hash is replaced.
     *
     * @param configuredPassword value of LOGIOPT_ADMIN_PASSWORD (null or blank: seed nothing)
     * @return what happened
     * @throws DataAccessException on database failure
     */
    public AdminSeedResult seedAdmin(String configuredPassword) throws DataAccessException {
        if (Strings.isBlank(configuredPassword)) {
            LOG.info(ENV_ADMIN_PASSWORD + " is not set; no administrator account was seeded.");
            return AdminSeedResult.SKIPPED_NOT_CONFIGURED;
        }
        if (configuredPassword.length() < MIN_PASSWORD_LENGTH || configuredPassword.length() > MAX_PASSWORD_LENGTH) {
            LOG.severe(ENV_ADMIN_PASSWORD + " must be " + MIN_PASSWORD_LENGTH + " to " + MAX_PASSWORD_LENGTH
                    + " characters; no administrator account was seeded.");
            return AdminSeedResult.SKIPPED_INVALID_PASSWORD;
        }
        char[] password = configuredPassword.toCharArray();
        try {
            Optional<UserDAO.Credentials> existing = users.findCredentialsByEmail(ADMIN_EMAIL);
            if (existing.isPresent()) {
                User user = existing.get().getUser();
                if (!user.isAdmin()) {
                    LOG.severe(ADMIN_EMAIL + " belongs to a non-admin account; administrator not seeded.");
                    return AdminSeedResult.SKIPPED_CONFLICT;
                }
                PasswordHash stored = existing.get().getPasswordHash();
                if (user.isEnabled() && hasher.verify(password, stored) && !hasher.needsRehash(stored)) {
                    return AdminSeedResult.UNCHANGED;
                }
                users.updateCredentials(user.getUserId(), hasher.hash(password), true);
                LOG.info("Administrator " + ADMIN_EMAIL + " updated from " + ENV_ADMIN_PASSWORD + ".");
                return AdminSeedResult.UPDATED;
            }
            Optional<User> otherAdmin = users.findAdmin();
            if (otherAdmin.isPresent()) {
                LOG.severe("Another administrator account (user " + otherAdmin.get().getUserId()
                        + ") exists; " + ADMIN_EMAIL + " was not seeded.");
                return AdminSeedResult.SKIPPED_CONFLICT;
            }
            if (users.insert(ADMIN_EMAIL, ADMIN_NAME, hasher.hash(password), Role.ADMIN).isEmpty()) {
                LOG.severe("Administrator could not be created because the email or admin slot is taken.");
                return AdminSeedResult.SKIPPED_CONFLICT;
            }
            LOG.info("Administrator " + ADMIN_EMAIL + " created from " + ENV_ADMIN_PASSWORD + ".");
            return AdminSeedResult.CREATED;
        } catch (DataAccessException e) {
            LOG.log(Level.SEVERE, "Administrator seeding failed", e);
            throw e;
        } finally {
            clear(password);
        }
    }

    /**
     * Lists all users for the administrator.
     *
     * @param actor signed-in user
     * @return users without password data
     * @throws DataAccessException on database failure
     * @throws SecurityException   if the actor is not the administrator
     */
    public List<User> listUsers(User actor) throws DataAccessException {
        if (actor == null || !actor.isAdmin()) {
            throw new SecurityException("Administrator only");
        }
        return users.findAll();
    }

    /**
     * Loads the signed-in user's own account details.
     *
     * @param actor signed-in user
     * @return name, email, role and registration time (no password data)
     * @throws NotFoundException   if the account no longer exists
     * @throws DataAccessException on database failure
     */
    public AccountProfile profile(User actor) throws NotFoundException, DataAccessException {
        requireActor(actor);
        return users.findProfile(actor.getUserId()).orElseThrow(() -> new NotFoundException("Account"));
    }

    /**
     * Changes the signed-in user's own display name, with the same rules as registration.
     *
     * @param actor signed-in user (the only account this can change)
     * @param name  new name
     * @return the user with the new name, to keep in the session
     * @throws AccountChangeException if the name is invalid
     * @throws NotFoundException      if the account no longer exists
     * @throws DataAccessException    on database failure
     */
    public User changeName(User actor, String name)
            throws AccountChangeException, NotFoundException, DataAccessException {
        requireActor(actor);
        String trimmed = name == null ? "" : name.trim();
        List<String> errors = nameErrors(trimmed);
        if (!errors.isEmpty()) {
            throw new AccountChangeException(errors);
        }
        if (!users.updateName(actor.getUserId(), trimmed)) {
            throw new NotFoundException("Account");
        }
        LOG.info("User " + actor.getUserId() + " changed their name");
        return new User(actor.getUserId(), actor.getEmail(), trimmed, actor.getRole(), actor.isEnabled());
    }

    /**
     * Changes the signed-in user's own password. The new password follows the registration rules and
     * is hashed with the current PBKDF2 settings; the current password is verified with the hasher's
     * constant-time comparison. Wrong current passwords count towards the same throttle as sign-in
     * (per email and client address) and get one generic message. The administrator's password
     * comes from LOGIOPT_ADMIN_PASSWORD and cannot be changed here. The session stays valid. All
     * three arrays are cleared.
     *
     * @param actor         signed-in user (the only account this can change)
     * @param current       current password
     * @param newPassword   new password
     * @param confirm       new password again
     * @param clientAddress client address for throttling
     * @throws AccountChangeException if refused (administrator, locked, wrong current, weak or mismatched new)
     * @throws NotFoundException      if the account no longer exists
     * @throws DataAccessException    on database failure
     */
    public void changePassword(User actor, char[] current, char[] newPassword, char[] confirm, String clientAddress)
            throws AccountChangeException, NotFoundException, DataAccessException {
        try {
            requireActor(actor);
            if (actor.isAdmin()) {
                throw new AccountChangeException(ADMIN_PASSWORD_FIXED);
            }
            String email = normalizeEmail(actor.getEmail());
            if (throttle.isLocked(email, clientAddress)) {
                throw new AccountChangeException(PASSWORD_LOCKED);
            }
            List<String> errors = new ArrayList<>(passwordErrors(newPassword));
            if (errors.isEmpty() && !Arrays.equals(newPassword, confirm)) {
                errors.add(PASSWORDS_DIFFER);
            }
            if (!errors.isEmpty()) {
                throw new AccountChangeException(errors);
            }
            Optional<UserDAO.Credentials> found = users.findCredentialsByEmail(email);
            if (found.isEmpty() || found.get().getUser().getUserId() != actor.getUserId()) {
                throw new NotFoundException("Account");
            }
            PasswordHash stored = found.get().getPasswordHash();
            char[] submitted = current == null ? new char[0] : current;
            boolean matches = submitted.length <= MAX_PASSWORD_LENGTH
                    && hasher.verify(submitted, stored != null ? stored : dummyHash) && stored != null;
            if (!matches) {
                throttle.recordFailure(email, clientAddress);
                throw new AccountChangeException(throttle.isLocked(email, clientAddress) ? PASSWORD_LOCKED
                        : WRONG_CURRENT_PASSWORD);
            }
            users.updateCredentials(actor.getUserId(), hasher.hash(newPassword), found.get().getUser().isEnabled());
            throttle.recordSuccess(email, clientAddress);
            LOG.info("User " + actor.getUserId() + " changed their password");
        } finally {
            clear(current);
            clear(newPassword);
            clear(confirm);
        }
    }

    private static void requireActor(User actor) {
        if (actor == null) {
            throw new IllegalArgumentException("A signed-in user is required");
        }
    }

    /** @return the registration rules' errors for a (trimmed) name, in order */
    private static List<String> nameErrors(String trimmedName) {
        List<String> errors = new ArrayList<>();
        if (trimmedName.isEmpty()) {
            errors.add("Name is required.");
        } else if (trimmedName.length() > MAX_NAME_LENGTH || hasControlCharacters(trimmedName)) {
            errors.add("Name must be at most " + MAX_NAME_LENGTH + " characters.");
        }
        return errors;
    }

    /** @return the registration rules' errors for a password */
    private static List<String> passwordErrors(char[] password) {
        int length = password == null ? 0 : password.length;
        if (length < MIN_PASSWORD_LENGTH) {
            return List.of("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (length > MAX_PASSWORD_LENGTH) {
            return List.of("Password must be at most " + MAX_PASSWORD_LENGTH + " characters.");
        }
        return List.of();
    }

    private static boolean hasControlCharacters(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isISOControl(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static void clear(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }
}
