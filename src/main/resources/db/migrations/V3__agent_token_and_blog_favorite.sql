-- Run once for an existing database. This project does not enable Flyway automatically.
ALTER TABLE `tb_voucher`
    ADD COLUMN `token_amount` bigint(20) UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Token 包可兑换的 Token 数量' AFTER `actual_value`;

CREATE TABLE `tb_user_token_account` (
    `user_id` bigint(20) UNSIGNED NOT NULL,
    `balance` bigint(20) UNSIGNED NOT NULL DEFAULT 0,
    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `tb_token_transaction` (
    `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id` bigint(20) UNSIGNED NOT NULL,
    `amount` bigint(20) UNSIGNED NOT NULL,
    `type` varchar(32) NOT NULL,
    `source_order_id` bigint(20) NOT NULL,
    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_token_transaction_order_type` (`source_order_id`, `type`),
    KEY `idx_token_transaction_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `tb_blog_favorite` (
    `id` bigint(20) UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id` bigint(20) UNSIGNED NOT NULL,
    `blog_id` bigint(20) UNSIGNED NOT NULL,
    `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_blog_favorite_user_blog` (`user_id`, `blog_id`),
    KEY `idx_blog_favorite_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
