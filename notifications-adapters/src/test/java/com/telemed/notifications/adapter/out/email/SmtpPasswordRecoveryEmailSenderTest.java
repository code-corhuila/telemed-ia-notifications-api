package com.telemed.notifications.adapter.out.email;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.telemed.notifications.application.exception.EmailDeliveryException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.net.ServerSocket;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmtpPasswordRecoveryEmailSenderTest {

    private static final String FROM = "no-reply@telemed.test";
    private static final String EMAIL = "patient@example.com";
    private static final String RESET_URL =
            "https://telemed.example.com/reset-password#token=plain-reset-token";

    // 10:30 in Bogota (UTC-5).
    private static final Instant EXPIRES_AT =
            Instant.parse("2026-10-20T15:30:00Z");

    @RegisterExtension
    static final GreenMailExtension GREEN_MAIL =
            new GreenMailExtension(ServerSetupTest.SMTP);

    @Test
    void shouldSendPlainTextSpanishEmailWithResetLinkAndLocalExpiration()
            throws Exception {
        sender(GREEN_MAIL.getSmtp().getPort())
                .send(EMAIL, RESET_URL, EXPIRES_AT);

        MimeMessage[] received = GREEN_MAIL.getReceivedMessages();
        assertThat(received).hasSize(1);

        MimeMessage message = received[0];
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo(EMAIL);
        assertThat(message.getFrom()[0].toString()).isEqualTo(FROM);
        assertThat(message.getSubject())
                .isEqualTo(SmtpPasswordRecoveryEmailSender.SUBJECT);
        assertThat(message.getContentType()).startsWith("text/plain");

        assertThat((String) message.getContent())
                .contains(RESET_URL)
                .contains("20 de octubre de 2026")
                .contains("10:30");
    }

    @Test
    void shouldReportUnreachableServerWithoutPersonalData()
            throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        SmtpPasswordRecoveryEmailSender sender = sender(closedPort);

        assertThatThrownBy(() -> sender.send(EMAIL, RESET_URL, EXPIRES_AT))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageNotContaining(EMAIL)
                .hasMessageNotContaining("plain-reset-token");
    }

    @Test
    void shouldRequireSenderAddress() {
        assertThatThrownBy(() -> new NotificationMailProperties(
                " ", ZoneId.of("America/Bogota")
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private static SmtpPasswordRecoveryEmailSender sender(
            int port
    ) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("localhost");
        mailSender.setPort(port);
        mailSender.getJavaMailProperties()
                .setProperty("mail.smtp.connectiontimeout", "2000");

        return new SmtpPasswordRecoveryEmailSender(
                mailSender,
                new NotificationMailProperties(
                        FROM, ZoneId.of("America/Bogota")
                )
        );
    }
}
