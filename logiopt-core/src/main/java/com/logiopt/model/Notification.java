package com.logiopt.model;

import java.time.LocalDateTime;

/**
 * An in-app notification for one recipient.
 */
public final class Notification {

    private final long notificationId;
    private final long userId;
    private final NotificationType type;
    private final long bookingId;
    private final String message;
    private final boolean read;
    private final LocalDateTime createdAt;
    private final LocalDateTime readAt;

    /**
     * Creates a notification.
     *
     * @param notificationId id
     * @param userId         recipient
     * @param type           type
     * @param bookingId      related booking
     * @param message        text
     * @param read           read flag
     * @param createdAt      creation time
     * @param readAt         time marked read, or null
     */
    public Notification(long notificationId, long userId, NotificationType type, long bookingId, String message,
                        boolean read, LocalDateTime createdAt, LocalDateTime readAt) {
        this.notificationId = notificationId;
        this.userId = userId;
        this.type = type;
        this.bookingId = bookingId;
        this.message = message;
        this.read = read;
        this.createdAt = createdAt;
        this.readAt = readAt;
    }

    /** @return id */
    public long getNotificationId() {
        return notificationId;
    }

    /** @return recipient id */
    public long getUserId() {
        return userId;
    }

    /** @return type */
    public NotificationType getType() {
        return type;
    }

    /** @return related booking id */
    public long getBookingId() {
        return bookingId;
    }

    /** @return text */
    public String getMessage() {
        return message;
    }

    /** @return true once marked read */
    public boolean isRead() {
        return read;
    }

    /** @return creation time */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** @return time marked read, or null */
    public LocalDateTime getReadAt() {
        return readAt;
    }
}
