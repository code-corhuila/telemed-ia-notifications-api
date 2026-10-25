package com.telemed.notifications.adapter.in.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.telemed.notifications.application.exception.NotificationNotFoundException;
import com.telemed.notifications.application.port.in.InboxPage;
import com.telemed.notifications.application.port.in.InboxQuery;
import com.telemed.notifications.application.port.in.MarkNotificationsReadUseCase;
import com.telemed.notifications.application.port.in.ViewNotificationInboxUseCase;
import com.telemed.notifications.application.port.out.AccessTokenVerifierPort;
import com.telemed.notifications.application.port.out.AccessTokenVerifierPort.AuthenticatedUser;
import com.telemed.notifications.domain.model.InboxNotification;
import com.telemed.notifications.domain.model.NotificationType;
import com.telemed.notifications.domain.model.SubjectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU-17: the caller's own inbox, identified by the access token only. */
class NotificationInboxControllerTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private static final UUID NOTIFICATION_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private static final UUID APPOINTMENT_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

    private final ViewNotificationInboxUseCase view = mock(ViewNotificationInboxUseCase.class);

    private final MarkNotificationsReadUseCase mark = mock(MarkNotificationsReadUseCase.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AccessTokenVerifierPort verifier = mock(AccessTokenVerifierPort.class);
        when(verifier.verify("patient-token")).thenReturn(new AuthenticatedUser(USER_ID, "PATIENT"));
        // As Spring Boot configures it: RFC 3339 dates, not timestamps.
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationInboxController(view, mark))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .addFilters(new CorrelationIdFilter(), new AuthenticationFilter(verifier, objectMapper))
                .build();
    }

    @Test
    void shouldListTheCallersInboxWithDataAndMeta() throws Exception {
        InboxNotification reminder = new InboxNotification(
                NOTIFICATION_ID,
                NotificationType.APPOINTMENT_REMINDER,
                new InboxNotification.Subject(SubjectType.APPOINTMENT, APPOINTMENT_ID,
                        Instant.parse("2026-11-02T14:30:00Z")),
                null,
                Instant.parse("2026-11-01T14:30:00Z")
        );
        when(view.list(USER_ID, new InboxQuery(1, InboxQuery.DEFAULT_LIMIT, false, null)))
                .thenReturn(new InboxPage(List.of(reminder), 1, 20, 21));

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer patient-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(NOTIFICATION_ID.toString()))
                .andExpect(jsonPath("$.data[0].type").value("APPOINTMENT_REMINDER"))
                .andExpect(jsonPath("$.data[0].subject.type").value("APPOINTMENT"))
                .andExpect(jsonPath("$.data[0].subject.id").value(APPOINTMENT_ID.toString()))
                .andExpect(jsonPath("$.data[0].subject.at").value("2026-11-02T14:30:00Z"))
                .andExpect(jsonPath("$.data[0].read").value(false))
                .andExpect(jsonPath("$.data[0].readAt").doesNotExist())
                .andExpect(jsonPath("$.data[0].createdAt").value("2026-11-01T14:30:00Z"))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.limit").value(20))
                .andExpect(jsonPath("$.meta.total").value(21))
                .andExpect(jsonPath("$.meta.totalPages").value(2));
    }

    @Test
    void shouldPassTheFiltersAndPage() throws Exception {
        when(view.list(USER_ID, new InboxQuery(2, 5, true, NotificationType.DOCUMENT_READY)))
                .thenReturn(new InboxPage(List.of(), 2, 5, 0));

        mockMvc.perform(get("/api/v1/notifications?unread=true&type=DOCUMENT_READY&page=2&limit=5")
                        .header("Authorization", "Bearer patient-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.page").value(2));
    }

    @Test
    void shouldRejectInvalidParametersNamingTheField() throws Exception {
        assertInvalid("/api/v1/notifications?limit=abc", "limit");
        assertInvalid("/api/v1/notifications?limit=101", "limit");
        assertInvalid("/api/v1/notifications?page=0", "page");
        assertInvalid("/api/v1/notifications?unread=yes", "unread");
        assertInvalid("/api/v1/notifications?type=SOMETHING", "type");
        assertInvalid("/api/v1/notifications?type=PASSWORD_RECOVERY", "type");
    }

    @Test
    void shouldCountTheCallersUnreadNotifications() throws Exception {
        when(view.countUnread(USER_ID)).thenReturn(3L);

        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", "Bearer patient-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3));
    }

    @Test
    void shouldMarkOneOfTheCallersNotificationsRead() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/" + NOTIFICATION_ID + "/read")
                        .header("Authorization", "Bearer patient-token"))
                .andExpect(status().isNoContent());

        verify(mark).markRead(USER_ID, NOTIFICATION_ID);
    }

    @Test
    void shouldAnswer404ForAnotherUsersOrAMissingNotification() throws Exception {
        doThrow(new NotificationNotFoundException()).when(mark).markRead(USER_ID, NOTIFICATION_ID);

        mockMvc.perform(put("/api/v1/notifications/" + NOTIFICATION_ID + "/read")
                        .header("Authorization", "Bearer patient-token")
                        .header("X-Correlation-Id", "trace-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").value("trace-404"));
    }

    @Test
    void shouldRejectAnIdThatIsNotAUuid() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/42/read").header("Authorization", "Bearer patient-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("id"));

        verify(mark, never()).markRead(any(), any());
    }

    @Test
    void shouldMarkAllOfTheCallersNotificationsRead() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/read-all").header("Authorization", "Bearer patient-token"))
                .andExpect(status().isNoContent());

        verify(mark).markAllRead(USER_ID);
    }

    @Test
    void shouldAnswer405WithTheCommonEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/notifications").header("Authorization", "Bearer patient-token"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
    }

    private void assertInvalid(String url, String field) throws Exception {
        mockMvc.perform(get(url).header("Authorization", "Bearer patient-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value(field));
    }
}
