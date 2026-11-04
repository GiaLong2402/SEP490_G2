package com.hbsisms.feature.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbsisms.feature.catalog.dto.CategoryRequest;
import com.hbsisms.feature.catalog.dto.CategoryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

@JsonTest
class CategoryDtoJacksonTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void request_readsIsActiveFalse() throws Exception {
        String json =
                """
                {"code":"cha-muc","name":"Chả mực","description":null,"parentId":null,"sortOrder":1,"isActive":false}
                """;
        CategoryRequest request = objectMapper.readValue(json, CategoryRequest.class);
        assertThat(request.isActive()).isFalse();
    }

    @Test
    void request_readsIsActiveTrue() throws Exception {
        String json =
                """
                {"code":"cha-muc","name":"Chả mực","description":null,"parentId":null,"sortOrder":1,"isActive":true}
                """;
        CategoryRequest request = objectMapper.readValue(json, CategoryRequest.class);
        assertThat(request.isActive()).isTrue();
    }

    @Test
    void response_writesIsActiveNotActive() throws Exception {
        CategoryResponse response =
                new CategoryResponse(1L, "cha-muc", "Chả mực", null, null, null, 1, false, null, 0L);
        String json = objectMapper.writeValueAsString(response);
        assertThat(json).contains("\"isActive\":false");
        assertThat(json).doesNotContain("\"active\":");
    }
}
