package com.telemed.notifications.application.port.in;

import com.telemed.notifications.domain.model.InboxNotification;

import java.util.List;

/**
 * @param total notifications matching the query, on every page
 */
public record InboxPage(
        List<InboxNotification> items,
        int page,
        int limit,
        long total
) {

    public InboxPage {
        items = List.copyOf(items);
    }

    public long totalPages() {
        return (total + limit - 1) / limit;
    }
}
