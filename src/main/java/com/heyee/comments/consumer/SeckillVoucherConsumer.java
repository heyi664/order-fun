package com.heyee.comments.consumer;

import com.heyee.comments.entity.VoucherOrder;
import com.heyee.comments.service.IVoucherOrderService;
import com.heyee.comments.utils.RocketMqConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = RocketMqConstants.SECKILL_VOUCHER_ORDER_TOPIC,
        consumerGroup = RocketMqConstants.SECKILL_VOUCHER_ORDER_CONSUMER_GROUP,
        maxReconsumeTimes = 3
)
public class SeckillVoucherConsumer implements RocketMQListener<VoucherOrder> {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Override
    public void onMessage(VoucherOrder voucherOrder) {
        try {
            voucherOrderService.createVoucherOrder(voucherOrder);
        } catch (Exception e) {
            log.error("秒杀订单消费失败，orderId={}", voucherOrder.getId(), e);
            throw new IllegalStateException("秒杀订单消费失败", e);
        }
    }
}
