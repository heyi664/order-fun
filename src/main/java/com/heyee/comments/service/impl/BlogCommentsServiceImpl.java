package com.heyee.comments.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.heyee.comments.dto.Result;
import com.heyee.comments.entity.User;
import com.heyee.comments.mapper.BlogMapper;
import com.heyee.comments.service.ITopicService;
import com.heyee.comments.service.IUserService;
import com.heyee.comments.utils.UserHolder;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.util.List;
import static com.heyee.comments.utils.RedisConstants.TOPIC_COMMENT_SCORE;

import com.heyee.comments.entity.BlogComments;
import com.heyee.comments.mapper.BlogCommentsMapper;
import com.heyee.comments.service.IBlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

    @Resource
    private BlogMapper blogMapper;

    @Resource
    private ITopicService topicService;

    @Resource
    private IUserService userService;

    @Override
    @Transactional
    public Result addComment(BlogComments comment) {
        if (comment.getBlogId() == null || comment.getContent() == null
                || comment.getContent().trim().isEmpty()) {
            return Result.fail("评论内容不能为空");
        }
        comment.setUserId(UserHolder.getUser().getId());
        comment.setContent(comment.getContent().trim());
        comment.setParentId(comment.getParentId() == null ? 0L : comment.getParentId());
        comment.setAnswerId(comment.getAnswerId() == null ? 0L : comment.getAnswerId());
        comment.setLiked(0);
        comment.setStatus(false);
        if (!save(comment)) {
            return Result.fail("评论发布失败");
        }
        if (blogMapper.incrementComments(comment.getBlogId(), 1) == 0) {
            throw new IllegalStateException("Blog not found: " + comment.getBlogId());
        }
        topicService.adjustHeatForBlog(comment.getBlogId(), TOPIC_COMMENT_SCORE);
        return Result.ok(comment.getId());
    }

    @Override
    @Transactional
    public Result deleteComment(Long id) {
        BlogComments comment = getById(id);
        if (comment == null) {
            return Result.fail("评论不存在");
        }
        if (!comment.getUserId().equals(UserHolder.getUser().getId())) {
            return Result.fail("无权删除该评论");
        }
        if (!removeById(id)) {
            return Result.fail("评论删除失败");
        }
        blogMapper.incrementComments(comment.getBlogId(), -1);
        topicService.adjustHeatForBlog(comment.getBlogId(), -TOPIC_COMMENT_SCORE);
        return Result.ok();
    }

    @Override
    public Result queryComments(Long blogId) {
        List<BlogComments> comments = list(new QueryWrapper<BlogComments>()
                .eq("blog_id", blogId)
                .and(wrapper -> wrapper.eq("status", false).or().isNull("status"))
                .orderByDesc("create_time"));
        for (BlogComments comment : comments) {
            User user = userService.getById(comment.getUserId());
            if (user != null) {
                comment.setUserName(user.getNickName());
                comment.setUserIcon(user.getIcon());
            }
        }
        return Result.ok(comments);
    }

}
