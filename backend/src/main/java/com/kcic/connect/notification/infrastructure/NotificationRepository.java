package com.kcic.connect.notification.infrastructure;

import com.kcic.connect.notification.domain.Notification;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientUserIdOrderByCreatedAtDesc(UUID recipientUserId, Pageable pageable);

    Page<Notification> findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(UUID recipientUserId,
                                                                               Pageable pageable);

    Optional<Notification> findByIdAndRecipientUserId(UUID id, UUID recipientUserId);

    long countByRecipientUserIdAndReadAtIsNull(UUID recipientUserId);

    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.recipientUserId = :recipientId and n.readAt is null")
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("now") Instant now);
}
