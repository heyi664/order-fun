package com.heyee.comments.service.impl;

import com.heyee.comments.dto.AgentChatRequestDTO;
import com.heyee.comments.dto.AiChatRequestDTO;
import com.heyee.comments.dto.AiChatResponseDTO;
import com.heyee.comments.dto.Result;
import com.heyee.comments.dto.UserDTO;
import com.heyee.comments.service.IAiChatService;
import com.heyee.comments.utils.UserHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

@Slf4j
@Service
public class AiChatServiceImpl implements IAiChatService {

    private static final String CHAT_PATH = "/v1/agent/chat";
    private static final String STREAM_CHAT_PATH = "/v1/agent/chat/stream";

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Value("${agent.service-url:http://127.0.0.1:8000}")
    private String agentServiceUrl;

    @Value("${agent.connect-timeout:3000}")
    private int connectTimeout;

    @Value("${agent.read-timeout:90000}")
    private int readTimeout;

    @Override
    public Result chat(AiChatRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getMessage())) {
            return Result.fail("请输入聊天内容");
        }
        if (request.getMessage().trim().length() > 1000) {
            return Result.fail("聊天内容不能超过 1000 个字符");
        }
        if (request.getHistory() != null && request.getHistory().size() > 10) {
            request.setHistory(request.getHistory().subList(request.getHistory().size() - 10, request.getHistory().size()));
        }

        AgentChatRequestDTO agentRequest = buildAgentRequest(request);

        try {
            AiChatResponseDTO response = restTemplate.postForObject(
                    buildAgentChatUrl(),
                    agentRequest,
                    AiChatResponseDTO.class
            );
            if (response == null || !StringUtils.hasText(response.getReply())) {
                return Result.fail("AI 服务暂时没有返回有效回复");
            }
            if (!StringUtils.hasText(response.getCreatedAt())) {
                response.setCreatedAt(LocalDateTime.now().toString());
            }
            return Result.ok(response);
        } catch (RestClientException e) {
            log.error("调用 AI Agent 服务失败，url={}", buildAgentChatUrl(), e);
            return Result.fail("AI 服务暂时不可用，请确认 Python Agent 已启动");
        }
    }

    @Override
    public ResponseEntity<StreamingResponseBody> streamChat(AiChatRequestDTO request) {
        Result validation = validateRequest(request);
        if (validation != null) {
            StreamingResponseBody body = outputStream -> writeSseError(outputStream, validation.getErrorMsg());
            return streamResponse(body);
        }
        AgentChatRequestDTO agentRequest = buildAgentRequest(request);
        String userId = currentUserId();
        StreamingResponseBody body = outputStream -> proxyAgentStream(outputStream, agentRequest, userId);
        return streamResponse(body);
    }

    @Override
    public Result cancelStream(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return Result.fail("任务标识不能为空");
        }
        HttpURLConnection connection = null;
        try {
            connection = openConnection(buildAgentStreamUrl() + "/" + taskId + "/cancel", "POST");
            connection.setRequestProperty("X-User-Id", currentUserId());
            int status = connection.getResponseCode();
            String response = readResponse(status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream());
            if (status < 200 || status >= 300) {
                log.warn("AI Agent stream cancellation failed, status={}, taskId={}, response={}", status, taskId, response);
                return Result.fail("取消 AI 回复失败");
            }
            return Result.ok(StringUtils.hasText(response)
                    ? objectMapper.readValue(response, Map.class) : Collections.emptyMap());
        } catch (IOException e) {
            log.error("调用 AI Agent 取消流式任务失败, taskId={}", taskId, e);
            return Result.fail("取消 AI 回复失败");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private Result validateRequest(AiChatRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getMessage())) {
            return Result.fail("请输入聊天内容");
        }
        if (request.getMessage().trim().length() > 1000) {
            return Result.fail("聊天内容不能超过 1000 个字符");
        }
        if (request.getHistory() != null && request.getHistory().size() > 10) {
            request.setHistory(request.getHistory().subList(request.getHistory().size() - 10, request.getHistory().size()));
        }
        return null;
    }

    private AgentChatRequestDTO buildAgentRequest(AiChatRequestDTO request) {
        UserDTO user = UserHolder.getUser();
        AgentChatRequestDTO agentRequest = new AgentChatRequestDTO();
        agentRequest.setUserId(user == null ? null : user.getId());
        agentRequest.setConversationId(request.getConversationId());
        agentRequest.setMessage(request.getMessage().trim());
        agentRequest.setHistory(request.getHistory());
        return agentRequest;
    }

    private ResponseEntity<StreamingResponseBody> streamResponse(StreamingResponseBody body) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(body);
    }

    private void proxyAgentStream(OutputStream outputStream, AgentChatRequestDTO agentRequest, String userId) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = openConnection(buildAgentStreamUrl(), "POST");
            connection.setRequestProperty(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE);
            connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            connection.setRequestProperty("X-User-Id", userId);
            connection.setDoOutput(true);
            byte[] requestBody = objectMapper.writeValueAsBytes(agentRequest);
            try (OutputStream requestStream = connection.getOutputStream()) {
                requestStream.write(requestBody);
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                String detail = readResponse(connection.getErrorStream());
                log.warn("AI Agent stream request failed, status={}, detail={}", status, detail);
                writeSseError(outputStream, "AI 流式服务暂时不可用");
                return;
            }
            try (InputStream inputStream = connection.getInputStream()) {
                byte[] buffer = new byte[4096];
                int length;
                while ((length = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, length);
                    outputStream.flush();
                }
            }
        } catch (IOException e) {
            log.error("调用 AI Agent 流式服务失败, url={}", buildAgentStreamUrl(), e);
            writeSseError(outputStream, "AI 流式服务连接失败");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private HttpURLConnection openConnection(String url, String method) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(connectTimeout);
        connection.setReadTimeout(readTimeout);
        return connection;
    }

    private String currentUserId() {
        UserDTO user = UserHolder.getUser();
        return user == null || user.getId() == null ? "" : user.getId().toString();
    }

    private String readResponse(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        try (InputStream stream = inputStream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = stream.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
            return output.toString("UTF-8");
        }
    }

    private void writeSseError(OutputStream outputStream, String message) throws IOException {
        Map<String, String> payload = Collections.singletonMap("message", message);
        String event = "event: error\ndata: " + objectMapper.writeValueAsString(payload) + "\n\n";
        outputStream.write(event.getBytes("UTF-8"));
        outputStream.flush();
    }

    private String buildAgentChatUrl() {
        String baseUrl = agentServiceUrl;
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + CHAT_PATH;
    }

    private String buildAgentStreamUrl() {
        String baseUrl = agentServiceUrl;
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + STREAM_CHAT_PATH;
    }
}
