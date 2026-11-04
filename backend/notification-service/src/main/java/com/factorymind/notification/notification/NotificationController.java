package com.factorymind.notification.notification;

import com.factorymind.notification.common.PageResponse;
import com.factorymind.notification.notification.NotificationDtos.NotificationResponse;
import com.factorymind.notification.notification.NotificationDtos.UnreadCount;
import com.factorymind.notification.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "The in-app feed of the signed-in user")
public class NotificationController {

    private final NotificationService notifications;
    private final LiveFeed live;

    public NotificationController(NotificationService notifications, LiveFeed live) {
        this.notifications = notifications;
        this.live = live;
    }

    @GetMapping
    @Operation(summary = "Notifications for the caller's roles, newest first")
    public PageResponse<NotificationResponse> feed(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication auth) {
        Page<NotificationResponse> found = notifications.feed(roles(auth), CurrentUser.name(auth), unreadOnly,
                page, Math.min(Math.max(size, 1), 100));
        return PageResponse.of(found, n -> n);
    }

    @GetMapping("/unread-count")
    public UnreadCount unread(Authentication auth) {
        return new UnreadCount(notifications.unread(roles(auth), CurrentUser.name(auth)));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id, Authentication auth) {
        notifications.markRead(id, CurrentUser.name(auth));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public UnreadCount markAllRead(Authentication auth) {
        notifications.markAllRead(roles(auth), CurrentUser.name(auth));
        return new UnreadCount(0);
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "New notifications for the caller's roles as Server-Sent Events (event: notification)")
    public SseEmitter stream(Authentication auth) {
        return live.open(roles(auth));
    }

    /** ROLE_ENGINEER -> ENGINEER: the names IN_APP rules use. */
    static Set<String> roles(Authentication auth) {
        if (auth == null) {
            return Set.of();
        }
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .collect(Collectors.toUnmodifiableSet());
    }
}
