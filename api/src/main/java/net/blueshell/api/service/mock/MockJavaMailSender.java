package net.blueshell.api.service.mock;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test double for JavaMailSender capturing outbox for assertions.
 */
@Slf4j
@Component
@Primary
@Profile("test | dev")
public class MockJavaMailSender implements JavaMailSender {

    private final Session session = Session.getInstance(new Properties());

    @Getter
    private final List<MimeMessage> outbox = new CopyOnWriteArrayList<>();

    @Getter
    private final List<SimpleMailMessage> simpleOutbox = new CopyOnWriteArrayList<>();

    private static String safeSubject(MimeMessage m) {
        try {
            return m.getSubject();
        } catch (Exception ignored) {
            return "<n/a>";
        }
    }

    private static List<String> safeRecipients(MimeMessage m) {
        try {
            return Arrays.stream(Objects.requireNonNullElse(m.getAllRecipients(), new jakarta.mail.Address[0]))
                    .map(Object::toString).toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    @Override
    public @NotNull MimeMessage createMimeMessage() {
        return new MimeMessage(session);
    }

    @Override
    public @NotNull MimeMessage createMimeMessage(java.io.@NotNull InputStream contentStream) throws MailException {
        try {
            return new MimeMessage(session, contentStream);
        } catch (Exception e) {
            throw new MailSendException("Failed to create MimeMessage from stream", e);
        }
    }

    @Override
    public void send(@NotNull MimeMessage mimeMessage) throws MailException {
        outbox.add(cloneMessage(mimeMessage));
        log.info(
                "[mail-mock] captured email: subject='{}' to={}",
                safeSubject(mimeMessage),
                safeRecipients(mimeMessage)
        );
    }

    @Override
    public void send(MimeMessage... mimeMessages) throws MailException {
        for (MimeMessage m : mimeMessages) send(m);
    }

    @Override
    public void send(@NotNull MimeMessagePreparator mimeMessagePreparator) throws MailException {
        MimeMessage m = createMimeMessage();
        try {
            mimeMessagePreparator.prepare(m);
        } catch (Exception e) {
            throw new MailPreparationException(e);
        }
        send(m);
    }

    @Override
    public void send(MimeMessagePreparator... mimeMessagePreparators) throws MailException {
        for (MimeMessagePreparator p : mimeMessagePreparators) send(p);
    }

    /**
     * Clear outbox between tests.
     */
    public void clear() {
        outbox.clear();
    }

    private MimeMessage cloneMessage(MimeMessage original) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            original.saveChanges();
            original.writeTo(bos);
            try (ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray())) {
                return new MimeMessage(session, bis);
            }
        } catch (Exception e) {
            throw new MailSendException("Failed to clone MimeMessage", e);
        }
    }

    @Override
    public void send(@NotNull SimpleMailMessage simpleMessage) throws MailException {
        simpleOutbox.add(new SimpleMailMessage(simpleMessage));
        log.info(
                "[mail-mock] captured simple email: subject='{}' to={}",
                simpleMessage.getSubject(),
                Arrays.toString(simpleMessage.getTo())
        );
    }

    @Override
    public void send(SimpleMailMessage @NotNull ... simpleMessages) throws MailException {
        for (SimpleMailMessage sm : simpleMessages) {
            send(sm);
        }
    }
}
