package com.heyee.comments.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.heyee.comments.dto.Result;
import com.heyee.comments.entity.Blog;
import com.heyee.comments.entity.BlogFavorite;
import com.heyee.comments.entity.User;
import com.heyee.comments.mapper.BlogFavoriteMapper;
import com.heyee.comments.mapper.BlogMapper;
import com.heyee.comments.service.IBlogFavoriteService;
import com.heyee.comments.service.IUserService;
import com.heyee.comments.utils.SystemConstants;
import com.heyee.comments.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BlogFavoriteServiceImpl implements IBlogFavoriteService {

    @Resource private BlogFavoriteMapper blogFavoriteMapper;
    @Resource private BlogMapper blogMapper;
    @Resource private IUserService userService;

    @Override
    @Transactional
    public Result toggleFavorite(Long blogId) {
        Long userId = UserHolder.getUser().getId();
        if (blogMapper.selectById(blogId) == null) return Result.fail("帖子不存在");
        BlogFavorite favorite = blogFavoriteMapper.selectOne(new QueryWrapper<BlogFavorite>()
                .eq("user_id", userId).eq("blog_id", blogId));
        boolean favorited;
        if (favorite == null) {
            BlogFavorite entity = new BlogFavorite();
            entity.setUserId(userId);
            entity.setBlogId(blogId);
            blogFavoriteMapper.insert(entity);
            favorited = true;
        } else {
            blogFavoriteMapper.deleteById(favorite.getId());
            favorited = false;
        }
        return Result.ok(Collections.singletonMap("favorited", favorited));
    }

    @Override
    public Result queryFavoriteStatus(Long blogId) {
        Long userId = UserHolder.getUser().getId();
        boolean favorited = blogFavoriteMapper.selectCount(new QueryWrapper<BlogFavorite>()
                .eq("user_id", userId).eq("blog_id", blogId)) > 0;
        return Result.ok(Collections.singletonMap("favorited", favorited));
    }

    @Override
    public Result queryMyFavorites(Integer current) {
        Long userId = UserHolder.getUser().getId();
        Page<BlogFavorite> page = blogFavoriteMapper.selectPage(new Page<>(current == null ? 1 : current,
                SystemConstants.MAX_PAGE_SIZE), new QueryWrapper<BlogFavorite>().eq("user_id", userId)
                .orderByDesc("create_time"));
        List<Long> ids = page.getRecords().stream().map(BlogFavorite::getBlogId).collect(Collectors.toList());
        if (ids.isEmpty()) return Result.ok(Collections.emptyList());
        Map<Long, Blog> blogMap = blogMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Blog::getId, item -> item));
        List<Blog> blogs = ids.stream().map(blogMap::get).filter(item -> item != null).collect(Collectors.toList());
        for (Blog blog : blogs) {
            User user = userService.getById(blog.getUserId());
            if (user != null) {
                blog.setName(user.getNickName());
                blog.setIcon(user.getIcon());
            }
            blog.setIsFavorite(true);
        }
        return Result.ok(blogs);
    }
}
