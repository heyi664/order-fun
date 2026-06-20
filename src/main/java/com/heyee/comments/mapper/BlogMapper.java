package com.heyee.comments.mapper;

import com.heyee.comments.entity.Blog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface BlogMapper extends BaseMapper<Blog> {

    @Update("UPDATE tb_blog SET comments = GREATEST(CAST(comments AS SIGNED) + #{delta}, 0) WHERE id = #{blogId}")
    int incrementComments(@Param("blogId") Long blogId, @Param("delta") int delta);

}
