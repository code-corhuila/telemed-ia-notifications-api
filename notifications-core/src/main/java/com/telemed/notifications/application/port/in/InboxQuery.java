package com.telemed.notifications.application.port.in;

import com.telemed.notifications.application.exception.InvalidInboxQueryException;
import com.telemed.notifications.domain.model.NotificationType;

/**
 * @param page       from 1
 * @param limit      1 to 100
 * @param unreadOnly only notifications not read yet
 * @param type       only this type, or null for every inbox type
 */
public record InboxQuery(
        int page,
        int limit,
        boolean unreadOnly,
        NotificationType type
) {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    public InboxQuery {

        if (page < 1) {
            throw new InvalidInboxQueryException("page", "page must be a number from 1.");
        }

        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidInboxQueryException("limit", "limit must be between 1 and 100.");
        }

        if (type != null && !type.shownInInbox()) {
            throw new InvalidInboxQueryException("type", "type is not an inbox notification type.");
        }
    }
}
