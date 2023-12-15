package com.pani.bi.bizmq;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

/**
 * @author Pani
 * @date Created in 2023/11/27 21:16
 * @description
 */
@SpringBootTest
class MyMessageProducerTest {
    @Resource
    MyMessageProducer myMessageProducer;

    @Test
    void sendMessage() {
        myMessageProducer.sendMessage("code_exchange", "my_routingKey",
                "又吃到了");
    }
}