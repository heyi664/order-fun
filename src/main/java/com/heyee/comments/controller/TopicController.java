package com.heyee.comments.controller;

import com.heyee.comments.dto.Result;
import com.heyee.comments.service.ITopicService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/topics")
public class TopicController {

    @Resource
    private ITopicService topicService;

    @GetMapping("/hot")
    public Result queryHotTopics(@RequestParam(value = "limit", defaultValue = "10") Integer limit) {
        return topicService.queryHotTopics(limit);
    }

    @GetMapping("/{id}/blogs")
    public Result queryBlogsByTopic(
            @PathVariable("id") Long id,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return topicService.queryBlogsByTopic(id, current);
    }
}

