package com.kcic.connect.notification.api;

import java.time.Instant;
import java.util.UUID;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationResponse(UUID id, String type, String title, String message,
                                       String targetResourceType, UUID targetResourceId, boolean read,
                                       Instant readAt, Instant createdAt) {
    }

    public record UnreadCountResponse(long unreadCount) {
    }
}
