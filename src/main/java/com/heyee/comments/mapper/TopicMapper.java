package com.heyee.comments.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.heyee.comments.entity.Topic;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface TopicMapper extends BaseMapper<Topic> {

    @Update("UPDATE tb_topic SET heat_score = GREATEST(CAST(heat_score AS SIGNED) + #{delta}, 0) WHERE id = #{id}")
    int incrementHeat(@Param("id") Long id, @Param("delta") long delta);

    @Update("UPDATE tb_topic SET blog_count = blog_count + 1 WHERE id = #{id}")
    int incrementBlogCount(@Param("id") Long id);
}

