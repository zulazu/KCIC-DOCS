package com.kcic.connect.notification.api;

import com.kcic.connect.identity.application.UserPrincipal;
import com.kcic.connect.notification.api.NotificationDtos.NotificationResponse;
import com.kcic.connect.notification.api.NotificationDtos.UnreadCountResponse;
import com.kcic.connect.notification.application.NotificationService;
import com.kcic.connect.shared.api.PageResponse;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/api/v1/notifications")
    public PageResponse<NotificationResponse> list(@AuthenticationPrincipal UserPrincipal principal,
                                                   @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return notificationService.list(UserPrincipal.require(principal).userId(), unreadOnly, page, size);
    }

    @GetMapping("/api/v1/notifications/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return new UnreadCountResponse(notificationService.unreadCount(UserPrincipal.require(principal).userId()));
    }

    @PostMapping("/api/v1/notifications/{notificationId}/read")
    public NotificationResponse markRead(@AuthenticationPrincipal UserPrincipal principal,
                                         @PathVariable UUID notificationId) {
        return notificationService.markRead(UserPrincipal.require(principal).userId(), notificationId);
    }

    @PostMapping("/api/v1/notifications/read-all")
    public UnreadCountResponse markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
        UUID actor = UserPrincipal.require(principal).userId();
        notificationService.markAllRead(actor);
        return new UnreadCountResponse(notificationService.unreadCount(actor));
    }
}
