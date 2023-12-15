package com.pani.bi.manager;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

/**
 * @author Pani
 * @date Created in 2023/11/19 14:06
 * @description
 */
@SpringBootTest
class AiManagerYuTest {

    @Resource
    private AiManagerYu aiManagerYu;

    @Test
    void doChat() {
        String s = aiManagerYu.doChat(1651468516836098050L,"Charlie Puth");
        System.out.println(s);
    }
}