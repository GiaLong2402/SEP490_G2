package com.hbsisms.feature.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Smoke Swagger/OpenAPI: schemas for Doanh write DTOs keep FE field names
 * ({@code isPublished}, {@code isActive}, tiers). Requires Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CatalogOpenApiContractTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApi_productAndVariantSchemasMatchFeContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ProductRequest.properties.isPublished").exists())
                .andExpect(jsonPath("$.components.schemas.ProductRequest.properties.isActive").exists())
                .andExpect(jsonPath("$.components.schemas.ProductRequest.properties.gallery").exists())
                .andExpect(jsonPath("$.components.schemas.ProductRequest.properties.published").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ProductResponse.properties.isPublished").exists())
                .andExpect(jsonPath("$.components.schemas.ProductResponse.properties.isActive").exists())
                .andExpect(jsonPath("$.components.schemas.ProductResponse.properties.variants").exists())
                .andExpect(jsonPath("$.components.schemas.VariantRequest.properties.isActive").exists())
                .andExpect(jsonPath("$.components.schemas.VariantRequest.properties.tiers").exists())
                .andExpect(jsonPath("$.components.schemas.VariantRequest.properties.listPrice").exists())
                .andExpect(jsonPath("$.components.schemas.TierInput.properties.minQtyKhay").exists())
                .andExpect(jsonPath("$.components.schemas.TierInput.properties.unitPrice").exists())
                .andExpect(jsonPath("$.components.schemas.VariantWithTiersResponse.properties.available").exists())
                .andExpect(jsonPath("$.components.schemas.VariantWithTiersResponse.properties.isActive").exists());
    }
}
