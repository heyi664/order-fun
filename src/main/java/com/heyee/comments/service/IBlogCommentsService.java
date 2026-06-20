package com.heyee.comments.service;
import com.heyee.comments.dto.Result;

import com.heyee.comments.entity.BlogComments;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogCommentsService extends IService<BlogComments> {

    Result addComment(BlogComments comment);

    Result deleteComment(Long id);

    Result queryComments(Long blogId);

}
