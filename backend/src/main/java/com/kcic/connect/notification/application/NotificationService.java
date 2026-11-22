package com.kcic.connect.notification.application;

import com.kcic.connect.notification.api.NotificationDtos.NotificationResponse;
import com.kcic.connect.notification.domain.Notification;
import com.kcic.connect.notification.infrastructure.NotificationRepository;
import com.kcic.connect.shared.api.ApiException;
import com.kcic.connect.shared.api.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-app notifications are created inside the same transaction as the business
 * action that triggers them. They never grant access: target links are re-checked
 * by the owning module when opened.
 */
@Service
public class NotificationService {

    private static final int MAX_TITLE = 180;
    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Notification create(UUID recipientUserId, String type, String title, String message,
                               String targetResourceType, UUID targetResourceId,
                               Map<String, Object> safeMetadata) {
        Instant now = Instant.now(clock);
        return repository.save(new Notification(UUID.randomUUID(), recipientUserId, type,
                truncate(title, MAX_TITLE), message, targetResourceType, targetResourceId, now,
                safeMetadata == null ? Map.of() : safeMetadata));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UUID actor, boolean unreadOnly, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<Notification> result = unreadOnly
                ? repository.findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(actor, pageable)
                : repository.findByRecipientUserIdOrderByCreatedAtDesc(actor, pageable);
        List<NotificationResponse> items = result.getContent().stream().map(NotificationService::toResponse).toList();
        return PageResponse.of(items, safePage, safeSize, result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID actor) {
        return repository.countByRecipientUserIdAndReadAtIsNull(actor);
    }

    /** Resolves by recipient as well as id so another user's notification is a 404, not a 403. */
    @Transactional
    public NotificationResponse markRead(UUID actor, UUID notificationId) {
        Notification notification = repository.findByIdAndRecipientUserId(notificationId, actor)
                .orElseThrow(() -> ApiException.notFound("Notifikasi tidak ditemukan."));
        notification.markRead(Instant.now(clock));
        return toResponse(notification);
    }

    @Transactional
    public int markAllRead(UUID actor) {
        return repository.markAllRead(actor, Instant.now(clock));
    }

    private static NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getNotificationType(),
                notification.getTitle(), notification.getMessage(), notification.getTargetResourceType(),
                notification.getTargetResourceId(), notification.getReadAt() != null,
                notification.getReadAt(), notification.getCreatedAt());
    }

    private static String truncate(String value, int max) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
