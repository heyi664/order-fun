package com.heyee.comments.service;

import com.heyee.comments.dto.AiChatRequestDTO;
import com.heyee.comments.dto.Result;

public interface IAiChatService {
    Result chat(AiChatRequestDTO request);
}
