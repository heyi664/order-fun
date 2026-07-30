package com.heyee.comments.service;

import com.heyee.comments.dto.AiChatRequestDTO;
import com.heyee.comments.dto.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

public interface IAiChatService {
    Result chat(AiChatRequestDTO request);

    ResponseEntity<StreamingResponseBody> streamChat(AiChatRequestDTO request);

    Result cancelStream(String taskId);
}
