package com.factorymind.notification.routing;

import com.factorymind.notification.common.BusinessRuleException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Checks and normalizes the recipient of a rule, which means something different for each channel. */
public final class Recipients {

    public static final Set<String> ROLES = Set.of("ADMIN", "ENGINEER", "OPERATOR");

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s,;]+@[^@\\s,;]+\\.[^@\\s,;]+$");
    private static final int MAX_EMAILS = 20;

    private Recipients() {
    }

    public static String normalize(Channel channel, String recipient) {
        if (recipient == null || recipient.isBlank()) {
            throw new BusinessRuleException("A recipient is required");
        }
        String value = recipient.strip();
        return switch (channel) {
            case EMAIL -> String.join(",", emails(value));
            case TEAMS -> teamsWebhook(value);
            case IN_APP -> role(value);
        };
    }

    /** The addresses of an EMAIL rule's recipient. */
    public static List<String> emails(String recipient) {
        List<String> emails = Arrays.stream(recipient.split("[,;]"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        if (emails.isEmpty() || emails.size() > MAX_EMAILS) {
            throw new BusinessRuleException("Give between 1 and " + MAX_EMAILS + " e-mail addresses, separated by commas");
        }
        for (String email : emails) {
            if (!EMAIL.matcher(email).matches()) {
                throw new BusinessRuleException("Not an e-mail address: " + email);
            }
        }
        return emails;
    }

    /**
     * Only HTTPS URLs: the webhook URL is a secret that carries its own credentials, and it is called by the
     * server, so plain HTTP or an internal address would be a way to make the server call anything.
     */
    private static String teamsWebhook(String value) {
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new BusinessRuleException("A Teams webhook must be an https:// URL");
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.equals("localhost") || host.endsWith(".local") || host.endsWith(".internal")
                    || host.matches("^[0-9.]+$") || host.startsWith("[")) {
                throw new BusinessRuleException("A Teams webhook must point to a public host name");
            }
            return value;
        } catch (URISyntaxException e) {
            throw new BusinessRuleException("A Teams webhook must be an https:// URL");
        }
    }

    private static String role(String value) {
        String role = value.toUpperCase(Locale.ROOT);
        if (!ROLES.contains(role)) {
            throw new BusinessRuleException("In-app recipient must be one of " + ROLES);
        }
        return role;
    }

    /** What the delivery log shows: the host of a Teams webhook, never its secret path. */
    public static String display(Channel channel, String recipient) {
        if (channel == Channel.TEAMS) {
            try {
                return "Teams webhook on " + new URI(recipient).getHost();
            } catch (URISyntaxException e) {
                return "Teams webhook";
            }
        }
        return recipient;
    }
}
