package com.heyee.comments.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiChatResponseDTO {
    private String conversationId;
    private String reply;
    private String createdAt;
    private List<String> sources;
    private List<String> toolCalls;
}
