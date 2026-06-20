package com.heyee.comments.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heyee.comments.entity.Shop;
import com.heyee.comments.service.IBlogService;
import com.heyee.comments.service.IShopService;
import com.heyee.comments.service.IShopTypeService;
import com.heyee.comments.service.ITopicService;
import com.heyee.comments.service.IVoucherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpServerServiceTest {

    private ObjectMapper objectMapper;
    private IShopService shopService;
    private McpServerService mcpServerService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        shopService = mock(IShopService.class);
        mcpServerService = new McpServerService(
                objectMapper,
                shopService,
                mock(IShopTypeService.class),
                mock(IVoucherService.class),
                mock(IBlogService.class),
                mock(ITopicService.class));
    }

    @Test
    void initializeReturnsMcpCapabilities() throws Exception {
        JsonNode response = mcpServerService.handle(objectMapper.readTree(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"));

        assertEquals("2025-03-26", response.path("result").path("protocolVersion").asText());
        assertFalse(response.path("result").path("capabilities").path("tools").isMissingNode());
    }

    @Test
    void toolsListContainsBusinessTools() throws Exception {
        JsonNode response = mcpServerService.handle(objectMapper.readTree(
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}"));

        assertEquals(7, response.path("result").path("tools").size());
        assertEquals("search_shops", response.path("result").path("tools").get(0).path("name").asText());
    }

    @Test
    void getShopDetailReturnsStructuredContent() throws Exception {
        when(shopService.getById(1L)).thenReturn(new Shop().setId(1L).setName("Test Shop"));
        JsonNode response = mcpServerService.handle(objectMapper.readTree(
                "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"get_shop_detail\",\"arguments\":{\"shop_id\":1}}}"));

        assertFalse(response.path("result").path("isError").asBoolean());
        assertEquals("Test Shop", response.path("result").path("structuredContent")
                .path("data").path("name").asText());
    }
}