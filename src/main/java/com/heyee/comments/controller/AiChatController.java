package com.heyee.comments.controller;

import com.heyee.comments.dto.AiChatRequestDTO;
import com.heyee.comments.dto.Result;
import com.heyee.comments.service.IAiChatService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
public class AiChatController {

    @Resource
    private IAiChatService aiChatService;

    @PostMapping("/ai/chat")
    public Result chat(@RequestBody AiChatRequestDTO request) {
        return aiChatService.chat(request);
    }
}
