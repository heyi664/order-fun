-- Allow publishing a blog without associating it with a shop.
-- Safe to run against an existing hycomment database.

ALTER TABLE `tb_blog`
  MODIFY COLUMN `shop_id` bigint(20) NULL DEFAULT NULL COMMENT '商户id，可不关联';