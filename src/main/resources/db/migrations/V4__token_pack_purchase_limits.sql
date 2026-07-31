-- Run once after V3 for an existing database.
ALTER TABLE `tb_voucher`
    ADD COLUMN `per_order_limit` int(11) UNSIGNED NOT NULL DEFAULT 1 COMMENT '每人单次可购买数量' AFTER `token_amount`,
    ADD COLUMN `per_user_limit` int(11) UNSIGNED NOT NULL DEFAULT 1 COMMENT '每人累计可购买数量' AFTER `per_order_limit`;

ALTER TABLE `tb_voucher_order`
    ADD COLUMN `quantity` int(11) UNSIGNED NOT NULL DEFAULT 1 COMMENT '本订单购买份数' AFTER `voucher_id`;
