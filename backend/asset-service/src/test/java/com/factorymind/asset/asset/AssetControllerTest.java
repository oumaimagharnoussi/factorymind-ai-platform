package com.factorymind.asset.asset;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factorymind.asset.common.NotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AssetController.class)
class AssetControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AssetService service;

    @Test
    void invalidPayloadReturnsProblemDetailWithFieldErrors() throws Exception {
        mvc.perform(post("/api/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"assetCode": "bad code!", "name": "", "criticality": "HIGH"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.assetCode").exists())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.lineId").exists());
    }

    @Test
    void unknownAssetReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id)).thenThrow(new NotFoundException("Asset", id));

        mvc.perform(get("/api/assets/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Asset " + id + " was not found"));
    }
}
