package com.factorymind.notification.routing;

import com.factorymind.notification.delivery.DeliveryService;
import com.factorymind.notification.delivery.DeliveryDtos.TestResult;
import com.factorymind.notification.routing.RuleDtos.RuleRequest;
import com.factorymind.notification.routing.RuleDtos.RuleResponse;
import com.factorymind.notification.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications/rules")
@Tag(name = "Routing rules", description = "Who is told about what, and how")
public class RoutingRuleController {

    private final RuleService rules;
    private final DeliveryService deliveries;

    public RoutingRuleController(RuleService rules, DeliveryService deliveries) {
        this.rules = rules;
        this.deliveries = deliveries;
    }

    @GetMapping
    @Operation(summary = "Every rule; Teams webhook URLs are shown in full to admins only")
    public List<RuleResponse> all(Authentication auth) {
        boolean admin = isAdmin(auth);
        return rules.all().stream().map(r -> RuleResponse.of(r, admin)).toList();
    }

    @PostMapping
    public ResponseEntity<RuleResponse> create(@Valid @RequestBody RuleRequest request, Authentication auth) {
        RoutingRule rule = rules.create(request, CurrentUser.name(auth));
        return ResponseEntity.created(URI.create("/api/notifications/rules/" + rule.getId()))
                .body(RuleResponse.of(rule, true));
    }

    @PutMapping("/{id}")
    public RuleResponse update(@PathVariable UUID id, @Valid @RequestBody RuleRequest request, Authentication auth) {
        return RuleResponse.of(rules.update(id, request, CurrentUser.name(auth)), true);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        rules.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "Send a test message through this rule now, even if it is disabled")
    public TestResult test(@PathVariable UUID id, Authentication auth) {
        return deliveries.sendTest(rules.get(id), CurrentUser.name(auth));
    }

    private static boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
