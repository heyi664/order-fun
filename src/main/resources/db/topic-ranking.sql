-- Topic ranking schema migration.
-- Run once against the existing hycomment database.

ALTER TABLE `tb_blog`
  ADD COLUMN `views` int(10) UNSIGNED NOT NULL DEFAULT 0 COMMENT '浏览数量' AFTER `comments`;

UPDATE `tb_blog` SET `liked` = 0 WHERE `liked` IS NULL;
UPDATE `tb_blog` SET `comments` = 0 WHERE `comments` IS NULL;

ALTER TABLE `tb_blog`
  MODIFY COLUMN `liked` int(8) UNSIGNED NOT NULL DEFAULT 0 COMMENT '点赞数量',
  MODIFY COLUMN `comments` int(8) UNSIGNED NOT NULL DEFAULT 0 COMMENT '评论数量';

CREATE TABLE `tb_topic` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '话题名称，不包含#',
  `heat_score` bigint(20) UNSIGNED NOT NULL DEFAULT 0 COMMENT '可信累计热度',
  `blog_count` int(10) UNSIGNED NOT NULL DEFAULT 0 COMMENT '关联帖子数量',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_topic_name` (`name`) USING BTREE,
  KEY `idx_topic_heat` (`heat_score`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='话题表';

CREATE TABLE `tb_blog_topic` (
  `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `blog_id` bigint(20) UNSIGNED NOT NULL COMMENT '帖子ID',
  `topic_id` bigint(20) UNSIGNED NOT NULL COMMENT '话题ID',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_blog_topic` (`blog_id`, `topic_id`) USING BTREE,
  KEY `idx_topic_blog` (`topic_id`, `blog_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子话题关联表';

