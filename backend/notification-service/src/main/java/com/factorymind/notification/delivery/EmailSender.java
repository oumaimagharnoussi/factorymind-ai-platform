package com.factorymind.notification.delivery;

import com.factorymind.notification.routing.Channel;
import com.factorymind.notification.routing.Recipients;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/** E-mail over SMTP (Mailpit in the demo: every message shows at http://localhost:8025). */
@Component
public class EmailSender implements ChannelSender {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    private final JavaMailSender mail;
    private final DeliveryProperties properties;

    public EmailSender(JavaMailSender mail, DeliveryProperties properties) {
        this.mail = mail;
        this.properties = properties;
    }

    @Override
    public Channel channel() {
        return Channel.EMAIL;
    }

    @Override
    public void send(String recipient, OutboundMessage message) {
        try {
            MimeMessage mime = mail.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(properties.mailFrom());
            helper.setTo(Recipients.emails(recipient).toArray(String[]::new));
            helper.setSubject(message.subject());
            helper.setText(text(message), html(message));
            mail.send(mime);
        } catch (MailException | MessagingException e) {
            throw new DeliveryException("SMTP: " + e.getMessage());
        }
    }

    static String text(OutboundMessage m) {
        StringBuilder text = new StringBuilder()
                .append(m.title()).append("\n\n")
                .append(m.message()).append("\n\n")
                .append(m.sourceLabel()).append(" · ").append(m.severity());
        if (m.assetLabel() != null) {
            text.append(" · ").append(m.assetLabel());
        }
        text.append(" · ").append(WHEN.format(m.occurredAt())).append('\n');
        if (m.link() != null) {
            text.append("\nOpen: ").append(m.link()).append('\n');
        }
        return text.append("\n-- \nFactoryMind AI (demo data). Routing rules: Notifications > Routing rules.\n").toString();
    }

    /** Every value is escaped: titles and messages carry machine names and alert text from other services. */
    static String html(OutboundMessage m) {
        String color = switch (m.severity()) {
            case CRITICAL -> "#c62828";
            case WARNING -> "#ef6c00";
            case INFO -> "#1565c0";
        };
        StringBuilder html = new StringBuilder()
                .append("<!doctype html><html><body style=\"font-family:Segoe UI,Arial,sans-serif;color:#1f2933\">")
                .append("<div style=\"max-width:560px;border:1px solid #e0e4e8;border-radius:8px;overflow:hidden\">")
                .append("<div style=\"background:").append(color).append(";color:#fff;padding:12px 16px\">")
                .append("<strong>").append(esc(m.severity().name())).append("</strong> · ")
                .append(esc(m.sourceLabel())).append("</div>")
                .append("<div style=\"padding:16px\">")
                .append("<h2 style=\"margin:0 0 8px;font-size:18px\">").append(esc(m.title())).append("</h2>")
                .append("<p style=\"margin:0 0 12px\">").append(esc(m.message())).append("</p>")
                .append("<p style=\"margin:0 0 16px;color:#52606d;font-size:13px\">");
        if (m.assetLabel() != null) {
            html.append(esc(m.assetLabel())).append(" · ");
        }
        html.append(esc(WHEN.format(m.occurredAt()))).append("</p>");
        if (m.link() != null) {
            html.append("<a href=\"").append(esc(m.link()))
                    .append("\" style=\"background:#1f2933;color:#fff;padding:8px 14px;border-radius:4px;")
                    .append("text-decoration:none\">Open in FactoryMind</a>");
        }
        return html.append("</div></div><p style=\"color:#9aa5b1;font-size:12px\">FactoryMind AI · demo data</p>")
                .append("</body></html>").toString();
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
