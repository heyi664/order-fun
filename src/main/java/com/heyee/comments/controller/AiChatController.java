package com.heyee.comments.controller;

import com.heyee.comments.dto.AiChatRequestDTO;
import com.heyee.comments.dto.Result;
import com.heyee.comments.limiter.annotation.RateLimiter;
import com.heyee.comments.service.IAiChatService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.annotation.Resource;

@RestController
public class AiChatController {

    @Resource
    private IAiChatService aiChatService;

    @PostMapping("/ai/chat")
    @RateLimiter(key = "rate_limit:ai_chat:", window = 60, limit = 30,
            type = RateLimiter.LimitType.USER, message = "AI 对话请求过于频繁，请稍后再试")
    public Result chat(@RequestBody AiChatRequestDTO request) {
        return aiChatService.chat(request);
    }

    @PostMapping(value = "/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimiter(key = "rate_limit:ai_chat:", window = 60, limit = 30,
            type = RateLimiter.LimitType.USER, message = "AI 对话请求过于频繁，请稍后再试")
    public ResponseEntity<StreamingResponseBody> streamChat(@RequestBody AiChatRequestDTO request) {
        return aiChatService.streamChat(request);
    }

    @PostMapping("/ai/chat/stream/{taskId}/cancel")
    public Result cancelStream(@PathVariable String taskId) {
        return aiChatService.cancelStream(taskId);
    }
}
