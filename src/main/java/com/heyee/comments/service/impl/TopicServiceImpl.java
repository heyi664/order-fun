package com.heyee.comments.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heyee.comments.dto.HotTopicDTO;
import com.heyee.comments.dto.Result;
import com.heyee.comments.entity.Blog;
import com.heyee.comments.entity.BlogTopic;
import com.heyee.comments.entity.Topic;
import com.heyee.comments.entity.User;
import com.heyee.comments.mapper.BlogMapper;
import com.heyee.comments.mapper.BlogTopicMapper;
import com.heyee.comments.mapper.TopicMapper;
import com.heyee.comments.mapper.UserMapper;
import com.heyee.comments.service.ITopicService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.heyee.comments.utils.RedisConstants.TOPIC_RANK_KEY;

@Service
public class TopicServiceImpl extends ServiceImpl<TopicMapper, Topic> implements ITopicService {

    private static final Pattern TOPIC_PATTERN = Pattern.compile("#([\\p{L}\\p{N}_-]{2,30})");
    private static final int RANK_CACHE_SIZE = 100;

    @Resource
    private BlogTopicMapper blogTopicMapper;

    @Resource
    private BlogMapper blogMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional
    public void bindTopicsToBlog(Blog blog) {
        Set<String> topicNames = extractTopics(blog.getTitle(), blog.getContent());
        if (topicNames.isEmpty()) {
            return;
        }

        long initialHeat = calculateBlogHeat(blog);
        for (String topicName : topicNames) {
            Topic topic = getOrCreateTopic(topicName);
            BlogTopic relation = new BlogTopic().setBlogId(blog.getId()).setTopicId(topic.getId());
            try {
                blogTopicMapper.insert(relation);
            } catch (DuplicateKeyException ignored) {
                continue;
            }
            baseMapper.incrementBlogCount(topic.getId());
            if (initialHeat > 0) {
                baseMapper.incrementHeat(topic.getId(), initialHeat);
            }
            refreshTopicCache(topic.getId());
        }
    }

    @Override
    @Transactional
    public void adjustHeatForBlog(Long blogId, long delta) {
        if (delta == 0) {
            return;
        }
        List<BlogTopic> relations = blogTopicMapper.selectList(
                new QueryWrapper<BlogTopic>().eq("blog_id", blogId));
        for (BlogTopic relation : relations) {
            baseMapper.incrementHeat(relation.getTopicId(), delta);
            refreshTopicCache(relation.getTopicId());
        }
    }

    @Override
    public Result queryHotTopics(Integer limit) {
        int safeLimit = limit == null ? 10 : Math.max(1, Math.min(limit, 50));
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeWithScores(TOPIC_RANK_KEY, 0, safeLimit - 1);
        if (tuples == null || tuples.isEmpty()) {
            rebuildRanking();
            tuples = stringRedisTemplate.opsForZSet()
                    .reverseRangeWithScores(TOPIC_RANK_KEY, 0, safeLimit - 1);
        }
        if (tuples == null || tuples.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<HotTopicDTO> result = new ArrayList<>(tuples.size());
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            Topic topic = getById(Long.valueOf(tuple.getValue()));
            if (topic == null) {
                continue;
            }
            long heat = tuple.getScore() == null ? 0L : tuple.getScore().longValue();
            result.add(new HotTopicDTO()
                    .setId(topic.getId())
                    .setName(topic.getName())
                    .setRank(rank++)
                    .setHeat(heat)
                    .setBlogCount(topic.getBlogCount()));
        }
        return Result.ok(result);
    }

    @Override
    public Result queryBlogsByTopic(Long topicId, Integer current) {
        int pageNo = current == null ? 1 : Math.max(current, 1);
        Page<BlogTopic> page = blogTopicMapper.selectPage(
                new Page<>(pageNo, 10),
                new QueryWrapper<BlogTopic>().eq("topic_id", topicId).orderByDesc("blog_id"));
        if (page.getRecords().isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> blogIds = page.getRecords().stream().map(BlogTopic::getBlogId).collect(Collectors.toList());
        List<Blog> blogs = blogMapper.selectBatchIds(blogIds);
        blogs.sort((left, right) -> Integer.compare(blogIds.indexOf(left.getId()), blogIds.indexOf(right.getId())));
        for (Blog blog : blogs) {
            User user = userMapper.selectById(blog.getUserId());
            if (user != null) {
                blog.setName(user.getNickName());
                blog.setIcon(user.getIcon());
            }
        }
        return Result.ok(blogs);
    }

    @Scheduled(initialDelay = 10000L, fixedDelay = 3600000L)
    public void backfillExistingTopics() {
        List<Blog> blogs = blogMapper.selectList(new QueryWrapper<Blog>()
                .and(wrapper -> wrapper.like("title", "#").or().like("content", "#")));
        for (Blog blog : blogs) {
            Integer relationCount = blogTopicMapper.selectCount(
                    new QueryWrapper<BlogTopic>().eq("blog_id", blog.getId()));
            if (relationCount == null || relationCount == 0) {
                bindTopicsToBlog(blog);
            }
        }
    }

    @Override
    @Scheduled(initialDelay = 30000L, fixedDelay = 300000L)
    public void rebuildRanking() {
        List<Topic> topics = query()
                .gt("heat_score", 0)
                .orderByDesc("heat_score")
                .last("LIMIT " + RANK_CACHE_SIZE)
                .list();
        String tempKey = TOPIC_RANK_KEY + ":rebuild";
        stringRedisTemplate.delete(tempKey);
        for (Topic topic : topics) {
            stringRedisTemplate.opsForZSet().add(tempKey, topic.getId().toString(), topic.getHeatScore());
        }
        if (topics.isEmpty()) {
            stringRedisTemplate.delete(TOPIC_RANK_KEY);
        } else {
            stringRedisTemplate.rename(tempKey, TOPIC_RANK_KEY);
        }
    }

    private Set<String> extractTopics(String title, String content) {
        String text = (title == null ? "" : title) + " " + (content == null ? "" : content);
        Matcher matcher = TOPIC_PATTERN.matcher(text.replace('＃', '#'));
        Set<String> topics = new LinkedHashSet<>();
        while (matcher.find()) {
            topics.add(matcher.group(1));
        }
        return topics;
    }

    private Topic getOrCreateTopic(String name) {
        Topic existing = query().eq("name", name).one();
        if (existing != null) {
            return existing;
        }
        Topic topic = new Topic().setName(name).setHeatScore(0L).setBlogCount(0);
        try {
            save(topic);
            return topic;
        } catch (DuplicateKeyException ignored) {
            return query().eq("name", name).one();
        }
    }

    private long calculateBlogHeat(Blog blog) {
        long views = blog.getViews() == null ? 0 : blog.getViews();
        long likes = blog.getLiked() == null ? 0 : blog.getLiked();
        long comments = blog.getComments() == null ? 0 : blog.getComments();
        return views + likes * 5L + comments * 8L;
    }

    private void refreshTopicCache(Long topicId) {
        Topic topic = getById(topicId);
        if (topic != null) {
            stringRedisTemplate.opsForZSet().add(TOPIC_RANK_KEY, topicId.toString(), topic.getHeatScore());
        }
    }
}

