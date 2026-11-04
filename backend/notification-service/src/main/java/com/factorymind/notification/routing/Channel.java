package com.factorymind.notification.routing;

public enum Channel {
    /** Recipient: one or more e-mail addresses separated by commas. */
    EMAIL,
    /** Recipient: the HTTPS URL of a Teams channel webhook (Workflows "post to a channel when a webhook request is received"). */
    TEAMS,
    /** Recipient: a role (ADMIN, ENGINEER, OPERATOR). Everyone with the role sees it in the app's bell. */
    IN_APP
}
