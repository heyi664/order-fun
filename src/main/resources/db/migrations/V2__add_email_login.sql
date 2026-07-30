-- Run once for an existing database before enabling email verification login.
ALTER TABLE `tb_user`
    MODIFY COLUMN `phone` varchar(11) NULL DEFAULT NULL COMMENT '手机号码',
    ADD COLUMN `email` varchar(191) NULL DEFAULT NULL COMMENT '登录邮箱' AFTER `phone`,
    ADD UNIQUE INDEX `uniqe_key_email` (`email`);
