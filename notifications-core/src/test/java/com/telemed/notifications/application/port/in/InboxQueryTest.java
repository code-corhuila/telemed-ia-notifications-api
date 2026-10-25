package com.telemed.notifications.application.port.in;

import com.telemed.notifications.application.exception.InvalidInboxQueryException;
import com.telemed.notifications.domain.model.NotificationType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InboxQueryTest {

    @Test
    void shouldAcceptPagesFromOneAndUpToOneHundredItems() {

        InboxQuery query = new InboxQuery(3, 100, true, NotificationType.APPOINTMENT_REMINDER);

        assertThat(query.page()).isEqualTo(3);
        assertThat(query.limit()).isEqualTo(100);
    }

    @Test
    void shouldNameTheInvalidField() {

        assertThatThrownBy(() -> new InboxQuery(0, 20, false, null))
                .isInstanceOfSatisfying(InvalidInboxQueryException.class,
                        exception -> assertThat(exception.field()).isEqualTo("page"));
        assertThatThrownBy(() -> new InboxQuery(1, 0, false, null))
                .isInstanceOfSatisfying(InvalidInboxQueryException.class,
                        exception -> assertThat(exception.field()).isEqualTo("limit"));
        assertThatThrownBy(() -> new InboxQuery(1, 101, false, null))
                .isInstanceOfSatisfying(InvalidInboxQueryException.class,
                        exception -> assertThat(exception.field()).isEqualTo("limit"));
    }

    @Test
    void shouldNotFilterByATypeTheInboxNeverShows() {

        // Password recovery is an e-mail only: it is not part of the inbox.
        assertThatThrownBy(() -> new InboxQuery(1, 20, false, NotificationType.PASSWORD_RECOVERY))
                .isInstanceOfSatisfying(InvalidInboxQueryException.class,
                        exception -> assertThat(exception.field()).isEqualTo("type"));
    }

    @Test
    void shouldCountTotalPages() {

        assertThat(new InboxPage(List.of(), 1, 20, 0).totalPages()).isZero();
        assertThat(new InboxPage(List.of(), 1, 20, 20).totalPages()).isEqualTo(1);
        assertThat(new InboxPage(List.of(), 1, 20, 41).totalPages()).isEqualTo(3);
    }
}
