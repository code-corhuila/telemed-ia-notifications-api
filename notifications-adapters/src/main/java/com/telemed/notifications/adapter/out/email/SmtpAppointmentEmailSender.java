package com.telemed.notifications.adapter.out.email;

import com.telemed.notifications.application.exception.EmailDeliveryException;
import com.telemed.notifications.application.port.out.AppointmentEmailSenderPort;
import com.telemed.notifications.domain.model.NotificationType;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Plain-text e-mail, like password recovery. It names no one: the patient sees
 * the professional and the details in the appointments portal.
 */
public class SmtpAppointmentEmailSender implements AppointmentEmailSenderPort {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern(
                    "d 'de' MMMM 'de' yyyy 'a las' HH:mm (z)",
                    Locale.forLanguageTag("es-CO")
            );

    private final JavaMailSender mailSender;
    private final NotificationMailProperties properties;

    public SmtpAppointmentEmailSender(
            JavaMailSender mailSender,
            NotificationMailProperties properties
    ) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(
            String recipientEmail,
            NotificationType change,
            Instant startsAt,
            Instant previousStartsAt
    ) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(recipientEmail);

        switch (change) {
            case APPOINTMENT_CONFIRMED -> {
                message.setSubject("Tu cita en TeleMed IA está confirmada");
                message.setText(body("Tu cita quedó confirmada para el " + time(startsAt) + "."));
            }
            case APPOINTMENT_RESCHEDULED -> {
                message.setSubject("Tu cita en TeleMed IA fue reprogramada");
                message.setText(body("Tu cita del " + time(previousStartsAt)
                        + " se reprogramó para el " + time(startsAt) + "."));
            }
            case APPOINTMENT_CANCELLED -> {
                message.setSubject("Tu cita en TeleMed IA fue cancelada");
                message.setText(body("Tu cita del " + time(startsAt) + " fue cancelada.\n\n"
                        + "Si aún necesitas atención, puedes agendar una nueva cita en TeleMed IA."));
            }
            case APPOINTMENT_REMINDER -> {
                message.setSubject("Recordatorio de tu cita en TeleMed IA");
                message.setText(body("Te recordamos tu cita del " + time(startsAt) + ".\n\n"
                        + "Si no puedes asistir, cancélala o reprográmala en TeleMed IA."));
            }
            default -> throw new IllegalArgumentException("Not an appointment notice: " + change + ".");
        }

        try {
            mailSender.send(message);

        } catch (MailException exception) {
            // The cause may carry the recipient address; the message must not.
            throw new EmailDeliveryException(
                    "Appointment e-mail could not be sent.",
                    exception
            );
        }
    }

    private String time(Instant instant) {
        return TIME_FORMAT.format(instant.atZone(properties.timeZone()));
    }

    private static String body(String news) {
        return """
                Hola,

                %s

                Puedes ver los detalles en la sección de citas de TeleMed IA.

                Equipo TeleMed IA
                """.formatted(news);
    }
}
