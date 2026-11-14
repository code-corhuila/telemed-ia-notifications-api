package com.telemed.notifications.adapter.in.http;

import com.telemed.notifications.application.exception.InvalidInboxQueryException;
import com.telemed.notifications.application.port.in.InboxPage;
import com.telemed.notifications.application.port.in.InboxQuery;
import com.telemed.notifications.application.port.in.MarkNotificationsReadUseCase;
import com.telemed.notifications.application.port.in.ViewNotificationInboxUseCase;
import com.telemed.notifications.domain.model.InboxNotification;
import com.telemed.notifications.domain.model.NotificationType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * HU-17 inbox of the caller. The recipient is always the user of the access
 * token: no endpoint takes a user id.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationInboxController {

    private final ViewNotificationInboxUseCase view;
    private final MarkNotificationsReadUseCase mark;

    public NotificationInboxController(
            ViewNotificationInboxUseCase view,
            MarkNotificationsReadUseCase mark
    ) {
        this.view = view;
        this.mark = mark;
    }

    @GetMapping
    public InboxResponse list(
            @RequestParam(required = false) String unread,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String limit,
            HttpServletRequest request
    ) {
        InboxQuery query = new InboxQuery(
                number(page, 1, "page", "page must be a number from 1."),
                number(limit, InboxQuery.DEFAULT_LIMIT, "limit", "limit must be between 1 and 100."),
                flag(unread),
                type(type)
        );

        return InboxResponse.of(view.list(caller(request), query));
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(HttpServletRequest request) {
        return new UnreadCountResponse(view.countUnread(caller(request)));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markRead(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        mark.markRead(caller(request), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllRead(HttpServletRequest request) {
        mark.markAllRead(caller(request));
        return ResponseEntity.noContent().build();
    }

    private static UUID caller(HttpServletRequest request) {
        return AuthenticationFilter.currentUser(request).userId();
    }

    private static int number(String value, int defaultValue, String field, String message) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new InvalidInboxQueryException(field, message);
        }
    }

    private static boolean flag(String value) {
        if (value == null || value.equals("false")) {
            return false;
        }
        if (value.equals("true")) {
            return true;
        }
        throw new InvalidInboxQueryException("unread", "unread must be true or false.");
    }

    private static NotificationType type(String value) {
        if (value == null) {
            return null;
        }
        try {
            return NotificationType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidInboxQueryException("type", "type is not an inbox notification type.");
        }
    }

    public record InboxResponse(List<Item> data, Meta meta) {

        static InboxResponse of(InboxPage page) {
            return new InboxResponse(
                    page.items().stream().map(Item::of).toList(),
                    new Meta(page.page(), page.limit(), page.total(), page.totalPages())
            );
        }
    }

    public record Item(
            UUID id,
            NotificationType type,
            Subject subject,
            boolean read,
            Instant readAt,
            Instant createdAt
    ) {

        static Item of(InboxNotification notification) {
            InboxNotification.Subject subject = notification.subject();
            return new Item(
                    notification.id(),
                    notification.type(),
                    subject == null ? null : new Subject(subject.type().name(), subject.id(), subject.at()),
                    notification.readAt() != null,
                    notification.readAt(),
                    notification.createdAt()
            );
        }
    }

    public record Subject(String type, UUID id, Instant at) {
    }

    public record Meta(int page, int limit, long total, long totalPages) {
    }

    public record UnreadCountResponse(long unreadCount) {
    }
}
