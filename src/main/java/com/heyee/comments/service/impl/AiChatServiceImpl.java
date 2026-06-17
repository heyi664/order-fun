package com.heyee.comments.service.impl;

import com.heyee.comments.dto.AgentChatRequestDTO;
import com.heyee.comments.dto.AiChatRequestDTO;
import com.heyee.comments.dto.AiChatResponseDTO;
import com.heyee.comments.dto.Result;
import com.heyee.comments.dto.UserDTO;
import com.heyee.comments.service.IAiChatService;
import com.heyee.comments.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Slf4j
@Service
public class AiChatServiceImpl implements IAiChatService {

    private static final String CHAT_PATH = "/v1/agent/chat";

    @Resource
    private RestTemplate restTemplate;

    @Value("${agent.service-url:http://127.0.0.1:8000}")
    private String agentServiceUrl;

    @Override
    public Result chat(AiChatRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getMessage())) {
            return Result.fail("请输入聊天内容");
        }

        UserDTO user = UserHolder.getUser();
        AgentChatRequestDTO agentRequest = new AgentChatRequestDTO();
        agentRequest.setUserId(user == null ? null : user.getId());
        agentRequest.setConversationId(request.getConversationId());
        agentRequest.setMessage(request.getMessage().trim());
        agentRequest.setHistory(request.getHistory());

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

    private String buildAgentChatUrl() {
        String baseUrl = agentServiceUrl;
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + CHAT_PATH;
    }
}
