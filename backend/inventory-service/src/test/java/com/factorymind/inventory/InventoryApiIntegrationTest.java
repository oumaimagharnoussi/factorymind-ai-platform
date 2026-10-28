package com.factorymind.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factorymind.inventory.common.BusinessRuleException;
import com.factorymind.inventory.part.PartCategory;
import com.factorymind.inventory.part.PartDtos.CreatePartRequest;
import com.factorymind.inventory.part.SparePartService;
import com.factorymind.inventory.stock.StockDtos.ReceiptRequest;
import com.factorymind.inventory.stock.StockDtos.WorkOrderMovementRequest;
import com.factorymind.inventory.stock.StockService;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Flyway schema and demo store, stock movements and the row lock, against a real PostgreSQL. Requires Docker. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class InventoryApiIntegrationTest {

    private static final String BEARING_6205 = "44444444-0000-0000-0000-000000000002";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    MockMvc mvc;

    @Autowired
    SparePartService parts;

    @Autowired
    StockService stock;

    private final JwtRequestPostProcessor engineer = jwt()
            .jwt(token -> token.claim("preferred_username", "engineer"))
            .authorities(new SimpleGrantedAuthority("ROLE_ENGINEER"));

    @Test
    void demoStoreShowsWhatIsRunningLow() throws Exception {
        mvc.perform(get("/api/inventory/parts").param("lowStock", "true").param("size", "50").with(engineer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.partNumber == 'AC-2914-9306')].quantityOnHand").value(1))
                .andExpect(jsonPath("$.content[?(@.partNumber == 'ACC-IEPE-100')].quantityOnHand").value(0))
                .andExpect(jsonPath("$.content[?(@.partNumber == 'SKF-6205-2RS')]").isEmpty());
    }

    @Test
    void issuingForAWorkOrderIsRecordedOnceEvenWhenRetried() throws Exception {
        String body = """
                {"quantity": 2, "requestId": "%s", "workOrderId": "%s", "workOrderCode": "WO-00123"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
        int before = parts.findById(UUID.fromString(BEARING_6205)).quantityOnHand();

        String first = mvc.perform(post("/api/inventory/parts/{id}/issues", BEARING_6205).with(engineer)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("ISSUE"))
                .andExpect(jsonPath("$.change").value(-2))
                .andExpect(jsonPath("$.quantityAfter").value(before - 2))
                .andExpect(jsonPath("$.performedBy").value("engineer"))
                .andReturn().getResponse().getContentAsString();
        String replay = mvc.perform(post("/api/inventory/parts/{id}/issues", BEARING_6205).with(engineer)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(replay, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
        assertThat(parts.findById(UUID.fromString(BEARING_6205)).quantityOnHand()).isEqualTo(before - 2);
    }

    @Test
    void cannotTakeMoreThanIsOnTheShelf() throws Exception {
        mvc.perform(post("/api/inventory/parts/{id}/issues", BEARING_6205).with(engineer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 9999, \"requestId\": \"%s\", \"workOrderId\": \"%s\"}"
                                .formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(startsWith("Only ")));
    }

    @Test
    void concurrentIssuesNeverTakeMoreThanTheStock() throws Exception {
        var part = parts.create(new CreatePartRequest("TEST-" + UUID.randomUUID().toString().substring(0, 8), "Fuse 10 A",
                null, PartCategory.ELECTRICAL, "pcs", 1, new BigDecimal("1.20"), null));
        stock.receive(part.id(), new ReceiptRequest(5, null, "DN-1", null), "engineer");

        List<Callable<Boolean>> technicians = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            technicians.add(() -> {
                try {
                    stock.issue(part.id(), new WorkOrderMovementRequest(1, UUID.randomUUID(), UUID.randomUUID(), null),
                            "engineer");
                    return true;
                } catch (BusinessRuleException e) {
                    return false;
                }
            });
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        long served = 0;
        for (Future<Boolean> result : pool.invokeAll(technicians)) {
            served += result.get() ? 1 : 0;
        }
        pool.shutdown();

        assertThat(served).isEqualTo(5);
        assertThat(parts.findById(part.id()).quantityOnHand()).isZero();
    }
}
