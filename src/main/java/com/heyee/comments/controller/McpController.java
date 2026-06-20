package com.heyee.comments.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.heyee.comments.mcp.McpServerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
public class McpController {

    private final McpServerService mcpServerService;
    private final ObjectMapper objectMapper;

    @Value("${mcp.server.token:}")
    private String serverToken;

    public McpController(McpServerService mcpServerService, ObjectMapper objectMapper) {
        this.mcpServerService = mcpServerService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/mcp", consumes = "application/json", produces = "application/json")
    public ResponseEntity<JsonNode> handle(
            @RequestBody JsonNode request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-MCP-Token", required = false) String tokenHeader) {
        if (!isAuthorized(authorization, tokenHeader)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (request.isArray()) {
            ArrayNode responses = objectMapper.createArrayNode();
            request.forEach(message -> {
                JsonNode response = mcpServerService.handle(message);
                if (response != null) {
                    responses.add(response);
                }
            });
            return responses.size() == 0
                    ? ResponseEntity.status(HttpStatus.ACCEPTED).build()
                    : ResponseEntity.ok(responses);
        }

        JsonNode response = mcpServerService.handle(request);
        return response == null
                ? ResponseEntity.status(HttpStatus.ACCEPTED).build()
                : ResponseEntity.ok(response);
    }

    private boolean isAuthorized(String authorization, String tokenHeader) {
        if (!StringUtils.hasText(serverToken)) {
            return true;
        }
        String provided = tokenHeader;
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            provided = authorization.substring(7);
        }
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                serverToken.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}