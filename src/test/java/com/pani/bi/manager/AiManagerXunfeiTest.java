package com.pani.bi.manager;

import io.github.briqt.spark4j.SparkClient;
import io.github.briqt.spark4j.constant.SparkApiVersion;
import io.github.briqt.spark4j.model.SparkMessage;
import io.github.briqt.spark4j.model.SparkSyncChatResponse;
import io.github.briqt.spark4j.model.request.SparkRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Pani
 * @date Created in 2024/3/13 18:46
 * @description
 */
@SpringBootTest
public class AiManagerXunfeiTest {
    @Resource
    private SparkClient sparkClient;

    /**
     * AI 生成问题的预设条件
     */
    public static final String PRECONDITION = "你是一个数据分析师和前端开发专家，接下来我会按照以下固定格式给你提供内容：\n" +
            "分析需求：\n" +
            "{数据分析的需求或者目标}\n" +
            "原始数据：\n" +
            "{csv格式的原始数据，用,作为分隔符}\n" +
            "请根据这两部分内容，严格按照以下指定格式生成内容（此外不要输出任何多余的开头、结尾、注释）\n" +
            "【【【【【\n" +
            "{前端 Echarts V5 的 option 配置对象 JSON 代码, 不要生成任何多余的内容，比如注释和代码块标记}\n" +
            "【【【【【\n" +
            "{明确的数据分析结论、越详细越好，不要生成多余的注释} \n" +
            "最终格式是:  【【【【【 (前端代码内容)【【【【【(分析结论) \n";

    static final String content = "易班客户端即时动态有效发布数统计\n" +
            "学院,1月,2月,3月,4月,5月,6月,7月,8月,9月,10月,11月,12月,总计\n" +
            "材料学院,21,16,28,17,16,0,98\n" +
            "材料示范学院,35,28,33,25,31,15,167\n" +
            "交通物流学院,20,15,24,25,20,3,107\n" +
            "船海能动学院,28,48,52,24,47,7,206\n" +
            "汽车学院,0,0,46,24,22,2,94\n" +
            "机电学院,0,0,0,20,20,0,40\n" +
            "土建学院,0,0,0,21,17,5,43\n" +
            "资环学院,0,0,0,4,11,5,20\n" +
            "信息学院,60,41,60,103,82,69,415\n" +
            "计算机智能学院,15,29,57,64,45,39,249\n" +
            "自动化学院,15,25,25,36,24,13,138\n" +
            "航运学院,2,3,4,3,20,4,36\n" +
            "理学院,0,0,53,66,57,10,186\n" +
            "化生学院,4,6,28,30,41,26,135\n" +
            "管理学院,20,20,26,20,20,21,127\n" +
            "经济学院,19,14,0,14,2,4,53\n" +
            "艺设学院,8,27,46,56,46,0,183\n" +
            "外国语学院,0,1,3,1,25,1,31\n" +
            "马克思学院,20,37,49,80,74,63,323\n" +
            "法学社会学院,12,8,28,37,25,17,127\n" +
            "安全应急学院,0,2,53,36,43,17,151\n" +
            "国教学院,32,27,41,32,26,28,186\n";

    @Test
    void testV35(){
        // 消息列表，可以在此列表添加历史对话记录
        List<SparkMessage> messages = new ArrayList<>();
        messages.add(SparkMessage.systemContent(PRECONDITION));
        messages.add(SparkMessage.userContent(content));
        // 构造请求
        SparkRequest sparkRequest = SparkRequest.builder()
                // 消息列表
                .messages(messages)
                // 模型回答的tokens的最大长度,非必传，默认为2048。
                // V1.5取值为[1,4096]
                // V2.0取值为[1,8192]
                // V3.0取值为[1,8192]
                .maxTokens(8192)
                // 核采样阈值。用于决定结果随机性,取值越高随机性越强即相同的问题得到的不同答案的可能性越高 非必传,取值为[0,1],默认为0.5
                .temperature(0.3)
                .apiVersion(SparkApiVersion.V3_5)
                .build();
        SparkSyncChatResponse chatResponse = sparkClient.chatSync(sparkRequest);
        System.err.println("--------------");
        System.out.println(chatResponse.getContent());


    }
}
