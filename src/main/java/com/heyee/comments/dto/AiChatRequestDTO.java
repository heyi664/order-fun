package com.heyee.comments.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiChatRequestDTO {
    private String conversationId;
    private String message;
    private List<AiChatHistoryDTO> history;
}
