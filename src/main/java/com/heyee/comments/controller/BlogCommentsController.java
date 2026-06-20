package com.heyee.comments.controller;
import com.heyee.comments.dto.Result;
import com.heyee.comments.entity.BlogComments;
import com.heyee.comments.service.IBlogCommentsService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import javax.annotation.Resource;


import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog-comments")
public class BlogCommentsController {

    @Resource
    private IBlogCommentsService commentsService;

    @PostMapping
    public Result addComment(@RequestBody BlogComments comment) {
        return commentsService.addComment(comment);
    }

    @DeleteMapping("/{id}")
    public Result deleteComment(@PathVariable("id") Long id) {
        return commentsService.deleteComment(id);
    }

    @GetMapping
    public Result queryComments(Long blogId) {
        return commentsService.queryComments(blogId);
    }

}
