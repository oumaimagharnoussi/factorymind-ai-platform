package com.factorymind.notification.routing;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutingRuleRepository extends JpaRepository<RoutingRule, UUID> {

    List<RoutingRule> findByEnabledTrue();

    List<RoutingRule> findAllByOrderByChannelAscNameAsc();
}
