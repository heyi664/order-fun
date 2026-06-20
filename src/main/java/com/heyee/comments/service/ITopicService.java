package com.heyee.comments.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heyee.comments.dto.Result;
import com.heyee.comments.entity.Blog;
import com.heyee.comments.entity.Topic;

public interface ITopicService extends IService<Topic> {

    void bindTopicsToBlog(Blog blog);

    void adjustHeatForBlog(Long blogId, long delta);

    Result queryHotTopics(Integer limit);

    Result queryBlogsByTopic(Long topicId, Integer current);

    void rebuildRanking();
}

