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
class AiManagerTest {

    @Resource
    private AiManager aiManager;

    @Test
    void doChat() {
        String s = aiManager.doChat(1651468516836098050L,"Charlie Puth");
        System.out.println(s);
    }
}