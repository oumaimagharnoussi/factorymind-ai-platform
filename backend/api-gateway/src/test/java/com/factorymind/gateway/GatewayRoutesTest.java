package com.factorymind.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

@SpringBootTest
class GatewayRoutesTest {

    @Autowired
    RouteLocator routes;

    @Test
    void assetRoutesPointToAssetService() {
        Route route = routes.getRoutes()
                .filter(r -> r.getId().equals("asset-service"))
                .blockFirst();

        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("http://localhost:8081");
    }

    @Test
    void telemetryRoutesPointToTelemetryService() {
        Route route = routes.getRoutes()
                .filter(r -> r.getId().equals("telemetry-service"))
                .blockFirst();

        assertThat(route).isNotNull();
        assertThat(route.getUri().toString()).isEqualTo("http://localhost:8082");
    }
}
