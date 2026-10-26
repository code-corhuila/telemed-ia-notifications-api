package com.telemed.notifications.adapter.out.email;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.telemed.notifications.application.exception.EmailDeliveryException;
import com.telemed.notifications.domain.model.NotificationType;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.net.ServerSocket;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HU-17: the patient is told of a confirmed, rescheduled or cancelled appointment. HU-18: and
 * reminded of an upcoming one. Times are in Bogota.
 */
class SmtpAppointmentEmailSenderTest {

    private static final String FROM = "no-reply@telemed.test";
    private static final String EMAIL = "patient@example.com";

    // 9:00 and 10:30 in Bogota (UTC-5).
    private static final Instant STARTS_AT = Instant.parse("2026-11-03T14:00:00Z");
    private static final Instant PREVIOUS_START = Instant.parse("2026-11-02T15:30:00Z");

    @RegisterExtension
    static final GreenMailExtension GREEN_MAIL = new GreenMailExtension(ServerSetupTest.SMTP);

    @Test
    void shouldTellThePatientTheConfirmedTime() throws Exception {
        sender(GREEN_MAIL.getSmtp().getPort())
                .send(EMAIL, NotificationType.APPOINTMENT_CONFIRMED, STARTS_AT, null);

        MimeMessage message = onlyMessage();
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo(EMAIL);
        assertThat(message.getFrom()[0].toString()).isEqualTo(FROM);
        assertThat(message.getContentType()).startsWith("text/plain");
        assertThat(message.getSubject()).isEqualTo("Tu cita en TeleMed IA está confirmada");
        assertThat((String) message.getContent())
                .contains("confirmada")
                .contains("3 de noviembre de 2026 a las 09:00");
    }

    @Test
    void shouldTellThePatientTheOldAndTheNewTimeOfARescheduledAppointment() throws Exception {
        sender(GREEN_MAIL.getSmtp().getPort())
                .send(EMAIL, NotificationType.APPOINTMENT_RESCHEDULED, STARTS_AT, PREVIOUS_START);

        MimeMessage message = onlyMessage();
        assertThat(message.getSubject()).isEqualTo("Tu cita en TeleMed IA fue reprogramada");
        assertThat((String) message.getContent())
                .contains("2 de noviembre de 2026 a las 10:30")
                .contains("3 de noviembre de 2026 a las 09:00");
    }

    @Test
    void shouldTellThePatientWhichAppointmentWasCancelled() throws Exception {
        sender(GREEN_MAIL.getSmtp().getPort())
                .send(EMAIL, NotificationType.APPOINTMENT_CANCELLED, STARTS_AT, null);

        MimeMessage message = onlyMessage();
        assertThat(message.getSubject()).isEqualTo("Tu cita en TeleMed IA fue cancelada");
        assertThat((String) message.getContent())
                .contains("cancelada")
                .contains("3 de noviembre de 2026 a las 09:00");
    }

    @Test
    void shouldRemindThePatientOfTheAppointmentTime() throws Exception {
        sender(GREEN_MAIL.getSmtp().getPort())
                .send(EMAIL, NotificationType.APPOINTMENT_REMINDER, STARTS_AT, null);

        MimeMessage message = onlyMessage();
        assertThat(message.getSubject()).isEqualTo("Recordatorio de tu cita en TeleMed IA");
        assertThat((String) message.getContent())
                .contains("Te recordamos")
                .contains("3 de noviembre de 2026 a las 09:00")
                .contains("Si no puedes asistir");
    }

    @Test
    void shouldWriteOnlyAboutAppointments() {
        SmtpAppointmentEmailSender sender = sender(GREEN_MAIL.getSmtp().getPort());

        assertThatThrownBy(() -> sender.send(EMAIL, NotificationType.DOCUMENT_READY, STARTS_AT, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sender.send(EMAIL, NotificationType.PASSWORD_RECOVERY, STARTS_AT, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(GREEN_MAIL.getReceivedMessages()).isEmpty();
    }

    @Test
    void shouldReportAnUnreachableServerWithoutTheAddress() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        SmtpAppointmentEmailSender sender = sender(closedPort);

        assertThatThrownBy(() -> sender.send(EMAIL, NotificationType.APPOINTMENT_CONFIRMED, STARTS_AT, null))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageNotContaining(EMAIL);
    }

    private static MimeMessage onlyMessage() {
        MimeMessage[] received = GREEN_MAIL.getReceivedMessages();
        assertThat(received).hasSize(1);
        return received[0];
    }

    private static SmtpAppointmentEmailSender sender(int port) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("localhost");
        mailSender.setPort(port);
        mailSender.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "2000");

        return new SmtpAppointmentEmailSender(
                mailSender,
                new NotificationMailProperties(FROM, ZoneId.of("America/Bogota"))
        );
    }
}
