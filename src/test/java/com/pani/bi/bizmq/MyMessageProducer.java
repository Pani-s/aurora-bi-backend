package com.pani.bi.bizmq;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
/**
 * @author Pani
 * @date Created in 2023/11/27 21:10
 * @descriptionservice作基础业务层 biz作为应用层业务层
 */

@Component
public class MyMessageProducer {

    @Resource
    private RabbitTemplate rabbitTemplate;
    /**
     * 发送消息的方法
     *
     * @param exchange   交换机名称，指定消息要发送到哪个交换机
     * @param routingKey 路由键，指定消息要根据什么规则路由到相应的队列
     * @param message    消息内容，要发送的具体消息
     */
    public void sendMessage(String exchange, String routingKey, String message) {
        // 使用rabbitTemplate的convertAndSend方法将消息发送到指定的交换机和路由键
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
    }

}