package com.telemed.notifications.adapter.out.email;

import com.telemed.notifications.application.exception.EmailDeliveryException;
import com.telemed.notifications.application.port.out.PasswordRecoveryEmailSenderPort;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Plain-text e-mail: no HTML, so nothing in the message can be rendered as
 * markup by the mail client.
 */
public class SmtpPasswordRecoveryEmailSender
        implements PasswordRecoveryEmailSenderPort {

    static final String SUBJECT =
            "Recupera tu contraseña de TeleMed IA";

    private static final DateTimeFormatter EXPIRATION_FORMAT =
            DateTimeFormatter.ofPattern(
                    "d 'de' MMMM 'de' yyyy 'a las' HH:mm (z)",
                    Locale.forLanguageTag("es-CO")
            );

    private final JavaMailSender mailSender;
    private final NotificationMailProperties properties;

    public SmtpPasswordRecoveryEmailSender(
            JavaMailSender mailSender,
            NotificationMailProperties properties
    ) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(
            String recipientEmail,
            String resetUrl,
            Instant expiresAt
    ) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(recipientEmail);
        message.setSubject(SUBJECT);
        message.setText(body(resetUrl, expiresAt));

        try {
            mailSender.send(message);

        } catch (MailException exception) {
            // The cause may carry the recipient address; the message must not.
            throw new EmailDeliveryException(
                    "Password recovery e-mail could not be sent.",
                    exception
            );
        }
    }

    private String body(
            String resetUrl,
            Instant expiresAt
    ) {
        String expiration =
                EXPIRATION_FORMAT.format(
                        expiresAt.atZone(properties.timeZone())
                );

        return """
                Hola,

                Recibimos una solicitud para restablecer la contraseña de tu cuenta de TeleMed IA.

                Para crear una nueva contraseña, abre este enlace:
                %s

                El enlace se puede usar una sola vez y vence el %s.

                Si no solicitaste este cambio, ignora este correo: tu contraseña seguirá siendo la misma.

                Equipo TeleMed IA
                """.formatted(resetUrl, expiration);
    }
}
