package com.logiopt.service;

import com.logiopt.dao.NotificationDAO;
import com.logiopt.dao.UserDAO;
import com.logiopt.exception.DataAccessException;
import com.logiopt.exception.NotFoundException;
import com.logiopt.model.Notification;
import com.logiopt.model.NotificationType;
import com.logiopt.model.User;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * In-app notifications. They are written by a managed pool of named daemon threads, after the
 * booking, payment or tracking transaction has committed, so a slow or failing notification can never
 * block or roll back that transaction: {@link #notify} never throws, failures are logged and counted,
 * and the pool keeps working. The database key (recipient, type, booking, event) makes retries
 * harmless. The administrator never receives notifications. Call {@link #shutdown} on undeploy.
 */
public class NotificationService {

    /** Default number of writer threads. */
    public static final int DEFAULT_THREADS = 1;
    /** Default queue size; when full, new notifications are dropped and logged. */
    public static final int DEFAULT_QUEUE_CAPACITY = 1000;

    private static final Logger LOG = Logger.getLogger(NotificationService.class.getName());
    private static final long SHUTDOWN_WAIT_SECONDS = 5;

    private final NotificationDAO notifications;
    private final UserDAO users;
    private final ThreadPoolExecutor executor;
    private final Object idle = new Object();
    private final AtomicLong failures = new AtomicLong();
    private int pending;

    /**
     * Creates the service and starts its threads.
     *
     * @param notifications notification DAO
     * @param users         user DAO (to skip the administrator)
     * @param threads       writer threads
     * @param queueCapacity queued notifications before new ones are dropped
     */
    public NotificationService(NotificationDAO notifications, UserDAO users, int threads, int queueCapacity) {
        if (notifications == null || users == null || threads < 1 || queueCapacity < 1) {
            throw new IllegalArgumentException("DAOs, a positive thread count and queue capacity are required");
        }
        this.notifications = notifications;
        this.users = users;
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread t = new Thread(runnable, "logiopt-notify-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        this.executor = new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(queueCapacity), factory, new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Queues a notification for a recipient. Never throws and never waits for the database.
     *
     * @param recipientUserId recipient
     * @param type            type
     * @param bookingId       related booking
     * @param eventKey        event identity within the booking (dedupe key part)
     * @param message         text
     */
    public void notify(long recipientUserId, NotificationType type, long bookingId, String eventKey, String message) {
        synchronized (idle) {
            pending++;
        }
        try {
            executor.execute(() -> {
                try {
                    write(recipientUserId, type, bookingId, eventKey, message);
                } finally {
                    done();
                }
            });
        } catch (RejectedExecutionException e) {
            done();
            failures.incrementAndGet();
            LOG.warning("Notification " + type + " for booking " + bookingId + " dropped: "
                    + (executor.isShutdown() ? "service is shut down" : "queue is full"));
        }
    }

    private void write(long recipientUserId, NotificationType type, long bookingId, String eventKey, String message) {
        try {
            Optional<User> recipient = users.findById(recipientUserId);
            if (recipient.isEmpty() || recipient.get().isAdmin()) {
                return;
            }
            if (!notifications.insert(recipientUserId, type, bookingId, eventKey, message)) {
                LOG.fine("Duplicate notification " + type + "/" + eventKey + " for booking " + bookingId + " ignored");
            }
        } catch (DataAccessException | RuntimeException e) {
            failures.incrementAndGet();
            LOG.log(Level.WARNING, "Notification " + type + " for booking " + bookingId + " could not be saved", e);
        }
    }

    private void done() {
        synchronized (idle) {
            pending--;
            idle.notifyAll();
        }
    }

    /**
     * Waits until every queued notification has been written or has failed (for tests and shutdown).
     *
     * @param timeout maximum wait
     * @return true if idle, false on timeout
     * @throws InterruptedException if interrupted
     */
    public boolean awaitIdle(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        synchronized (idle) {
            while (pending > 0) {
                long left = deadline - System.nanoTime();
                if (left <= 0) {
                    return false;
                }
                TimeUnit.NANOSECONDS.timedWait(idle, left);
            }
            return true;
        }
    }

    /** @return notifications that could not be saved or were dropped since start */
    public long getFailureCount() {
        return failures.get();
    }

    /** @return true after {@link #shutdown} */
    public boolean isShutdown() {
        return executor.isShutdown();
    }

    /**
     * Stops accepting notifications, lets queued ones finish for a few seconds, then stops the threads.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                List<Runnable> dropped = executor.shutdownNow();
                LOG.warning(dropped.size() + " queued notifications were dropped at shutdown");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * @param user signed-in user
     * @return the user's notifications, newest first
     * @throws DataAccessException on database failure
     */
    public List<Notification> list(User user) throws DataAccessException {
        return notifications.findByUser(user.getUserId());
    }

    /**
     * @param user signed-in user
     * @return number of unread notifications
     * @throws DataAccessException on database failure
     */
    public int unreadCount(User user) throws DataAccessException {
        return notifications.countUnread(user.getUserId());
    }

    /**
     * Marks one of the user's notifications as read.
     *
     * @param user           signed-in user
     * @param notificationId notification id
     * @throws NotFoundException   if missing or someone else's
     * @throws DataAccessException on database failure
     */
    public void markRead(User user, long notificationId) throws NotFoundException, DataAccessException {
        if (!notifications.markRead(notificationId, user.getUserId())) {
            throw new NotFoundException("Notification " + notificationId);
        }
    }

    /**
     * Marks all of the user's notifications as read.
     *
     * @param user signed-in user
     * @return number marked
     * @throws DataAccessException on database failure
     */
    public int markAllRead(User user) throws DataAccessException {
        return notifications.markAllRead(user.getUserId());
    }
}
