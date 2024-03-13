package com.pani.bi.bizmq;
import com.esotericsoftware.minlog.Log;
import com.pani.bi.bizmq.BiMqConstant;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.util.HashMap;
import java.util.Map;

/**
 * 用于创建测试程序用到的交换机和队列（只用在程序启动前执行一次）
 */
public class BiInitMain {

    public static void main(String[] args) {
        System.out.println("[[[[[[BiInitMain]]]]]]]]]]");
        try {
            // 创建连接工厂
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("localhost");
//            factory.setHost("60.204.248.10");
//            factory.setUsername("pani");
//            factory.setPassword("123456");
            // 创建连接
            Connection connection = factory.newConnection();
            // 创建通道
            Channel channel = connection.createChannel();

            // 声明死信队列
            channel.exchangeDeclare(BiMqConstant.BI_DLX_EXCHANGE_NAME, "direct");
            channel.queueDeclare(BiMqConstant.BI_DLX_QUEUE_NAME, true, false, false, null);
            channel.queueBind(BiMqConstant.BI_DLX_QUEUE_NAME, BiMqConstant.BI_DLX_EXCHANGE_NAME,
                    BiMqConstant.BI_DLX_ROUTING_KEY);

            // 声明交换机，指定交换机类型为 direct
            channel.exchangeDeclare(BiMqConstant.BI_EXCHANGE_NAME, "direct");

            Map<String, Object> arg = new HashMap<>();
            arg.put("x-dead-letter-exchange", BiMqConstant.BI_DLX_EXCHANGE_NAME);
            arg.put("x-dead-letter-routing-key", BiMqConstant.BI_DLX_ROUTING_KEY);
            // 声明队列，设置队列持久化、非独占、非自动删除，并传入额外的参数为 map 的arg!!!!
            channel.queueDeclare(BiMqConstant.BI_QUEUE_NAME, true, false, false, arg);
            // 将队列绑定到交换机，指定路由键为 "my_routingKey"
            channel.queueBind(BiMqConstant.BI_QUEUE_NAME, BiMqConstant.BI_EXCHANGE_NAME,
                    BiMqConstant.BI_ROUTING_KEY);
        } catch (Exception e) {
            // 异常处理
            Log.error(e.getMessage());
        }
    }
}
