package com.factorymind.notification.routing;

import com.factorymind.notification.common.ConflictException;
import com.factorymind.notification.common.NotFoundException;
import com.factorymind.notification.routing.RuleDtos.RuleRequest;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuleService {

    private final RoutingRuleRepository rules;
    private final Clock clock;

    public RuleService(RoutingRuleRepository rules, Clock clock) {
        this.rules = rules;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RoutingRule> all() {
        return rules.findAllByOrderByChannelAscNameAsc();
    }

    @Transactional(readOnly = true)
    public RoutingRule get(UUID id) {
        return rules.findById(id).orElseThrow(() -> new NotFoundException("Routing rule", id));
    }

    @Transactional
    public RoutingRule create(RuleRequest request, String user) {
        return rules.save(new RoutingRule(request.name(), request.source(), request.minSeverity(), request.channel(),
                request.recipient(), request.throttle(), request.enabled(), user, clock.instant()));
    }

    @Transactional
    public RoutingRule update(UUID id, RuleRequest request, String user) {
        RoutingRule rule = get(id);
        if (request.version() != null && request.version() != rule.getVersion()) {
            throw new ConflictException("The rule was changed by someone else. Reload it and try again.");
        }
        rule.update(request.name(), request.source(), request.minSeverity(), request.channel(), request.recipient(),
                request.throttle(), request.enabled(), user, clock.instant());
        return rule;
    }

    @Transactional
    public void delete(UUID id) {
        rules.delete(get(id));
    }
}
