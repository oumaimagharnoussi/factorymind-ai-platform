package com.factorymind.notification.delivery;

import com.factorymind.notification.common.PageResponse;
import com.factorymind.notification.delivery.DeliveryDtos.DeliveryResponse;
import com.factorymind.notification.routing.Channel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications/deliveries")
@Tag(name = "Deliveries", description = "Every e-mail and Teams message: sent, pending retry, failed or throttled")
public class DeliveryController {

    private final DeliveryService deliveries;

    public DeliveryController(DeliveryService deliveries) {
        this.deliveries = deliveries;
    }

    @GetMapping
    public PageResponse<DeliveryResponse> search(@RequestParam(required = false) DeliveryStatus status,
                                                 @RequestParam(required = false) Channel channel,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return deliveries.search(status, channel, Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Send a failed or throttled delivery again")
    public DeliveryResponse retry(@PathVariable UUID id) {
        return deliveries.retry(id);
    }
}
