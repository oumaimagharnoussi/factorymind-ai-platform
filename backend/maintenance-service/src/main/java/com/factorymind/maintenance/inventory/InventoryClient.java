package com.factorymind.maintenance.inventory;

import com.factorymind.maintenance.common.BusinessRuleException;
import com.factorymind.maintenance.common.NotFoundException;
import com.factorymind.maintenance.common.ServiceUnavailableException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Takes parts out of stock (and puts them back) in inventory-service. This is a synchronous call on purpose:
 * the technician must know at once whether the part is there. The caller's own access token is passed on,
 * so inventory-service applies its own role rules to the same user.
 *
 * <p>Each call carries a request id; inventory-service records a movement once per id, so retrying a call
 * after a timeout can never take the part out twice.
 */
@Component
public class InventoryClient {

    private final RestClient http;

    public InventoryClient(@Value("${maintenance.inventory-url:http://localhost:8086}") String baseUrl) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(2));
        timeouts.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(timeouts)
                .requestInterceptor((request, body, execution) -> {
                    if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
                        request.getHeaders().setBearerAuth(jwt.getToken().getTokenValue());
                    }
                    return execution.execute(request, body);
                })
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }

    public IssuedStock issue(UUID partId, int quantity, UUID requestId, UUID workOrderId, String workOrderCode) {
        return move("issues", partId, quantity, requestId, workOrderId, workOrderCode);
    }

    public IssuedStock giveBack(UUID partId, int quantity, UUID requestId, UUID workOrderId, String workOrderCode) {
        return move("returns", partId, quantity, requestId, workOrderId, workOrderCode);
    }

    private IssuedStock move(String kind, UUID partId, int quantity, UUID requestId, UUID workOrderId,
                             String workOrderCode) {
        try {
            return http.post()
                    .uri("/api/inventory/parts/{partId}/{kind}", partId, kind)
                    .body(Map.of("quantity", quantity, "requestId", requestId, "workOrderId", workOrderId,
                            "workOrderCode", workOrderCode))
                    .retrieve()
                    .body(IssuedStock.class);
        } catch (RestClientResponseException e) {
            throw translate(e, partId);
        } catch (ResourceAccessException e) {
            throw new ServiceUnavailableException("The inventory service did not answer. Nothing was taken out of stock; try again.", e);
        }
    }

    /** Errors from inventory-service keep their meaning (and their message) for the user. */
    private static RuntimeException translate(RestClientResponseException e, UUID partId) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        if (status == HttpStatus.NOT_FOUND) {
            return new NotFoundException("Spare part", partId);
        }
        if (status == HttpStatus.FORBIDDEN || status == HttpStatus.UNAUTHORIZED) {
            return new AccessDeniedException("Inventory refused the stock movement");
        }
        if (status == HttpStatus.CONFLICT || status == HttpStatus.UNPROCESSABLE_CONTENT
                || status == HttpStatus.BAD_REQUEST) {
            ProblemDetail problem = e.getResponseBodyAs(ProblemDetail.class);
            String detail = problem == null || problem.getDetail() == null ? "Inventory refused the stock movement"
                    : problem.getDetail();
            return new BusinessRuleException(detail);
        }
        return new ServiceUnavailableException("The inventory service failed (" + e.getStatusCode().value()
                + "). Nothing was taken out of stock; try again.", e);
    }
}
