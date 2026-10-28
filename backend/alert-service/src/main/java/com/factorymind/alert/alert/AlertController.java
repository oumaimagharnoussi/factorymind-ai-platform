package com.factorymind.alert.alert;

import com.factorymind.alert.alert.AlertDtos.AlertResponse;
import com.factorymind.alert.alert.AlertDtos.AlertSummary;
import com.factorymind.alert.alert.AlertDtos.ResolveRequest;
import com.factorymind.alert.alert.AlertSpecifications.AlertFilter;
import com.factorymind.alert.common.PageResponse;
import com.factorymind.alert.live.LiveStream;
import com.factorymind.alert.security.CurrentUser;
import com.factorymind.alert.telemetry.SensorType;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService service;
    private final LiveStream live;

    public AlertController(AlertService service, LiveStream live) {
        this.service = service;
        this.live = live;
    }

    /** Newest first. {@code status} can be repeated: {@code ?status=OPEN&status=ACKNOWLEDGED} lists unresolved alerts. */
    @GetMapping
    public PageResponse<AlertResponse> search(
            @RequestParam(required = false) List<AlertStatus> status,
            @RequestParam(required = false) AlertSeverity severity,
            @RequestParam(required = false) UUID assetId,
            @RequestParam(required = false) SensorType sensorType,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "lastRaisedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(new AlertFilter(status, severity, assetId, sensorType, active), pageable);
    }

    @GetMapping("/summary")
    public AlertSummary summary() {
        return service.summary();
    }

    /** Server-Sent Events: one {@code alert} event per change of any alert. */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return live.open();
    }

    @GetMapping("/{id}")
    public AlertResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping("/{id}/acknowledge")
    public AlertResponse acknowledge(@PathVariable UUID id, Authentication user) {
        return service.acknowledge(id, CurrentUser.name(user));
    }

    @PostMapping("/{id}/resolve")
    public AlertResponse resolve(@PathVariable UUID id, @Valid @RequestBody(required = false) ResolveRequest request,
                                 Authentication user) {
        return service.resolve(id, CurrentUser.name(user), request == null ? null : request.note());
    }
}
