package com.pani.bi.bizmq;

import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
/**
 * @author Pani
 * @date Created in 2023/11/27 21:11
 * @description
 */

@Component
@Slf4j
public class MyMessageConsumer {

    /**
     * 接收消息的方法
     *使用@SneakyThrows注解简化异常处理
     * 使用@RabbitListener注解指定要监听的队列名称为"code_queue"，并设置消息的确认机制为手动确认
     * @param message      接收到的消息内容，是一个字符串类型
     * @param channel      消息所在的通道，可以通过该通道与 RabbitMQ 进行交互，例如手动确认消息、拒绝消息等
     * @param deliveryTag  消息的投递标签，用于唯一标识一条消息
     *
     */
    @SneakyThrows
    @RabbitListener(queues = {"code_queue"}, ackMode = "MANUAL")
    public void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("receiveMessage message = {}", message);
        //手动确认
        channel.basicAck(deliveryTag, false);
        /*
         * @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag是一个方法参数注解,
         * 用于从消息头中获取投递标签(deliveryTag),在RabbitMQ中,每条消息都会被分配一个唯一的投递标签，
         * 用于标识该消息在通道中的投递状态和顺序。通过使用@Header(AmqpHeaders.DELIVERY_TAG)注解,
         * 可以从消息头中提取出该投递标签,并将其赋值给long deliveryTag参数。
         */
    }

}
