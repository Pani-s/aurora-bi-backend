package com.pani.bi.bizmq;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author Pani
 * @date Created in 2023/11/27 21:10
 * @description service作基础业务层 biz作为应用层业务层
 */

@Component
public class BiMessageProducer {

    @Resource
    private RabbitTemplate rabbitTemplate;
    /**
     * 发送消息的方法
     *
     * @param message    消息内容，要发送的具体消息
     */
    public void sendMessage(String message) {
        // 使用rabbitTemplate的convertAndSend方法将消息发送到指定的交换机和路由键
        rabbitTemplate.convertAndSend(BiMqConstant.BI_EXCHANGE_NAME, BiMqConstant.BI_ROUTING_KEY, message);
    }

}