package com.factorymind.notification.routing;

import com.factorymind.notification.event.EventSource;
import com.factorymind.notification.event.Severity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class RuleDtos {

    private RuleDtos() {
    }

    /**
     * @param source  null for every source
     * @param version the version that was read, for an update (optimistic locking); ignored on create
     */
    public record RuleRequest(
            @NotBlank @Size(max = 120) String name,
            EventSource source,
            @NotNull Severity minSeverity,
            @NotNull Channel channel,
            @NotBlank @Size(max = 1000) String recipient,
            @Min(0) @Max(1440) Integer throttleMinutes,
            boolean enabled,
            Long version) {

        public int throttle() {
            return throttleMinutes == null ? 0 : throttleMinutes;
        }
    }

    public record RuleResponse(
            UUID id,
            String name,
            EventSource source,
            Severity minSeverity,
            Channel channel,
            String recipient,
            int throttleMinutes,
            boolean enabled,
            long version,
            Instant updatedAt,
            String updatedBy) {

        /** @param revealSecrets false hides the secret path of a Teams webhook (only admins see it) */
        static RuleResponse of(RoutingRule r, boolean revealSecrets) {
            String recipient = revealSecrets ? r.getRecipient() : Recipients.display(r.getChannel(), r.getRecipient());
            return new RuleResponse(r.getId(), r.getName(), r.getSource(), r.getMinSeverity(), r.getChannel(),
                    recipient, r.getThrottleMinutes(), r.isEnabled(), r.getVersion(), r.getUpdatedAt(),
                    r.getUpdatedBy());
        }
    }
}
