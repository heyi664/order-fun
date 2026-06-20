package com.heyee.comments.mcp;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.heyee.comments.dto.Result;
import com.heyee.comments.entity.Shop;
import com.heyee.comments.service.IBlogService;
import com.heyee.comments.service.IShopService;
import com.heyee.comments.service.IShopTypeService;
import com.heyee.comments.service.ITopicService;
import com.heyee.comments.service.IVoucherService;
import com.heyee.comments.utils.SystemConstants;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class McpServerService {

    public static final String PROTOCOL_VERSION = "2025-03-26";

    private final ObjectMapper objectMapper;
    private final IShopService shopService;
    private final IShopTypeService shopTypeService;
    private final IVoucherService voucherService;
    private final IBlogService blogService;
    private final ITopicService topicService;

    public McpServerService(ObjectMapper objectMapper,
                            IShopService shopService,
                            IShopTypeService shopTypeService,
                            IVoucherService voucherService,
                            IBlogService blogService,
                            ITopicService topicService) {
        this.objectMapper = objectMapper;
        this.shopService = shopService;
        this.shopTypeService = shopTypeService;
        this.voucherService = voucherService;
        this.blogService = blogService;
        this.topicService = topicService;
    }

    public JsonNode handle(JsonNode request) {
        JsonNode id = request.get("id");
        if (!request.isObject() || !"2.0".equals(request.path("jsonrpc").asText())
                || !request.hasNonNull("method")) {
            return error(id, -32600, "Invalid JSON-RPC request");
        }

        String method = request.path("method").asText();
        try {
            switch (method) {
                case "initialize":
                    return response(id, initializeResult());
                case "ping":
                    return response(id, objectMapper.createObjectNode());
                case "tools/list":
                    return response(id, toolsListResult());
                case "tools/call":
                    return response(id, callTool(request.path("params")));
                case "notifications/initialized":
                case "notifications/cancelled":
                    return null;
                default:
                    return id == null ? null : error(id, -32601, "Method not found: " + method);
            }
        } catch (IllegalArgumentException e) {
            return id == null ? null : error(id, -32602, e.getMessage());
        } catch (Exception e) {
            return id == null ? null : error(id, -32603, "Internal MCP server error");
        }
    }

    private ObjectNode initializeResult() {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools").put("listChanged", false);
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", "heyee-comments-mcp");
        serverInfo.put("version", "1.0.0");
        result.put("instructions", "Read-only HYEEE tools for shops, vouchers, blogs and topics.");
        return result;
    }

    private ObjectNode toolsListResult() {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode tools = result.putArray("tools");
        tools.add(tool("search_shops", "Search shops by keyword or category with optional coordinates.",
                properties(
                        property("keyword", "string", "Shop name keyword"),
                        property("type_id", "integer", "Shop category ID"),
                        property("page", "integer", "Page number, starting from 1"),
                        property("longitude", "number", "Longitude for distance sorting"),
                        property("latitude", "number", "Latitude for distance sorting")
                ), Collections.<String>emptyList()));
        tools.add(tool("get_shop_detail", "Get a shop by ID.",
                properties(property("shop_id", "integer", "Shop ID")),
                Collections.singletonList("shop_id")));
        tools.add(tool("list_shop_types", "List all shop categories in display order.",
                properties(), Collections.<String>emptyList()));
        tools.add(tool("list_shop_vouchers", "List available vouchers for a shop.",
                properties(property("shop_id", "integer", "Shop ID")),
                Collections.singletonList("shop_id")));
        tools.add(tool("list_hot_blogs", "List popular local-life posts.",
                properties(property("page", "integer", "Page number, starting from 1")),
                Collections.<String>emptyList()));
        tools.add(tool("list_hot_topics", "List the realtime topic ranking.",
                properties(property("limit", "integer", "Number of topics, from 1 to 50")),
                Collections.<String>emptyList()));
        tools.add(tool("list_topic_blogs", "List posts associated with a topic.",
                properties(
                        property("topic_id", "integer", "Topic ID"),
                        property("page", "integer", "Page number, starting from 1")
                ), Collections.singletonList("topic_id")));
        return result;
    }

    private ObjectNode callTool(JsonNode params) throws JsonProcessingException {
        String name = requiredText(params, "name");
        JsonNode arguments = params.path("arguments");
        if (!arguments.isObject() && !arguments.isMissingNode() && !arguments.isNull()) {
            throw new IllegalArgumentException("Tool arguments must be an object");
        }

        try {
            Object data;
            switch (name) {
                case "search_shops":
                    data = searchShops(arguments);
                    break;
                case "get_shop_detail":
                    data = getShopDetail(requiredLong(arguments, "shop_id"));
                    break;
                case "list_shop_types":
                    data = shopTypeService.query().orderByAsc("sort").list();
                    break;
                case "list_shop_vouchers":
                    data = unwrap(voucherService.queryVoucherOfShop(requiredLong(arguments, "shop_id")));
                    break;
                case "list_hot_blogs":
                    data = unwrap(blogService.queryHotBlog(positiveInt(arguments, "page", 1, 10000)));
                    break;
                case "list_hot_topics":
                    data = unwrap(topicService.queryHotTopics(positiveInt(arguments, "limit", 10, 50)));
                    break;
                case "list_topic_blogs":
                    data = unwrap(topicService.queryBlogsByTopic(
                            requiredLong(arguments, "topic_id"),
                            positiveInt(arguments, "page", 1, 10000)));
                    break;
                default:
                    throw new IllegalArgumentException("Unknown tool: " + name);
            }
            return toolResult(data, false);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (ToolExecutionException e) {
            return toolResult(Collections.singletonMap("error", e.getMessage()), true);
        } catch (Exception e) {
            return toolResult(Collections.singletonMap("error", "Tool execution failed"), true);
        }
    }

    private Object searchShops(JsonNode arguments) {
        int page = positiveInt(arguments, "page", 1, 10000);
        JsonNode typeNode = arguments.path("type_id");
        if (typeNode.isIntegralNumber()) {
            Integer typeId = typeNode.intValue();
            if (typeId <= 0) {
                throw new IllegalArgumentException("type_id must be greater than 0");
            }
            Double longitude = optionalDouble(arguments, "longitude");
            Double latitude = optionalDouble(arguments, "latitude");
            if ((longitude == null) != (latitude == null)) {
                throw new IllegalArgumentException("longitude and latitude must be provided together");
            }
            return unwrap(shopService.queryShopByType(typeId, page, longitude, latitude));
        }

        String keyword = optionalText(arguments, "keyword");
        Page<Shop> shops = shopService.query()
                .like(StrUtil.isNotBlank(keyword), "name", keyword)
                .page(new Page<>(page, SystemConstants.MAX_PAGE_SIZE));
        return shops.getRecords();
    }

    private Shop getShopDetail(Long shopId) {
        Shop shop = shopService.getById(shopId);
        if (shop == null) {
            throw new ToolExecutionException("Shop not found: " + shopId);
        }
        return shop;
    }

    private Object unwrap(Result result) {
        if (result == null || !Boolean.TRUE.equals(result.getSuccess())) {
            String message = result == null ? "Business service returned no result" : result.getErrorMsg();
            throw new ToolExecutionException(message);
        }
        return result.getData();
    }

    private ObjectNode toolResult(Object data, boolean isError) throws JsonProcessingException {
        JsonNode dataNode = objectMapper.valueToTree(data);
        ObjectNode result = objectMapper.createObjectNode();
        ObjectNode structuredContent = result.putObject("structuredContent");
        structuredContent.set("data", dataNode);
        ObjectNode content = result.putArray("content").addObject();
        content.put("type", "text");
        content.put("text", objectMapper.writeValueAsString(data));
        result.put("isError", isError);
        return result;
    }

    private ObjectNode tool(String name, String description, ObjectNode propertyDefinitions,
                            List<String> required) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", name);
        tool.put("description", description);
        ObjectNode schema = tool.putObject("inputSchema");
        schema.put("type", "object");
        schema.set("properties", propertyDefinitions);
        ArrayNode requiredNode = schema.putArray("required");
        for (String field : required) {
            requiredNode.add(field);
        }
        schema.put("additionalProperties", false);
        return tool;
    }

    private ObjectNode properties(ObjectNode... definitions) {
        ObjectNode properties = objectMapper.createObjectNode();
        for (ObjectNode definition : definitions) {
            String name = definition.remove("name").asText();
            properties.set(name, definition);
        }
        return properties;
    }

    private ObjectNode property(String name, String type, String description) {
        ObjectNode property = objectMapper.createObjectNode();
        property.put("name", name);
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    private String requiredText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private String optionalText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText().trim() : null;
    }

    private Long requiredLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || value.longValue() <= 0) {
            throw new IllegalArgumentException(field + " must be a positive integer");
        }
        return value.longValue();
    }

    private int positiveInt(JsonNode node, String field, int defaultValue, int maximum) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return defaultValue;
        }
        if (!value.isIntegralNumber() || value.intValue() <= 0) {
            throw new IllegalArgumentException(field + " must be a positive integer");
        }
        return Math.min(value.intValue(), maximum);
    }

    private Double optionalDouble(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isNumber()) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        return value.doubleValue();
    }

    private ObjectNode response(JsonNode id, JsonNode result) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id == null || id.isNull()) {
            response.putNull("id");
        } else {
            response.set("id", id);
        }
        response.set("result", result);
        return response;
    }

    private ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id == null || id.isNull()) {
            response.putNull("id");
        } else {
            response.set("id", id);
        }
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message);
        return response;
    }

    private static class ToolExecutionException extends RuntimeException {
        private ToolExecutionException(String message) {
            super(message);
        }
    }
}