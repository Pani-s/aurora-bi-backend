package com.pani.bi.manager;

import com.github.rholder.retry.*;
import com.pani.bi.common.ErrorCode;
import com.pani.bi.constant.ChartConstant;
import com.pani.bi.exception.BusinessException;
import com.pani.bi.exception.ThrowUtils;
import com.yupi.yucongming.dev.client.YuCongMingClient;
import com.yupi.yucongming.dev.common.BaseResponse;
import com.yupi.yucongming.dev.model.DevChatRequest;
import com.yupi.yucongming.dev.model.DevChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author Pani
 * @date Created in 2023/11/19 13:57
 * @description 调用ai，用的是鱼聪明（现在底层是文心一言）
 */
@Slf4j
@Service
public class AiManagerYu {
    @Resource
    private YuCongMingClient yuCongMingClient;

    /**
     * 对话AI
     * @param message
     * @return
     */
    public String doChat(long modelId,String message){
        DevChatRequest devChatRequest = new DevChatRequest();
//        devChatRequest.setModelId(1651468516836098050L);
        devChatRequest.setModelId(modelId);
        devChatRequest.setMessage(message);

        BaseResponse<DevChatResponse> response = yuCongMingClient.doChat(devChatRequest);
        // 如果响应为null，就抛出系统异常，提示“AI 响应错误”
        if (response == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 响应错误");
        }
        //重试
        Retryer<DevChatResponse> retryer = RetryerBuilder.<DevChatResponse>newBuilder()
                //设置异常重试源 不可设置多个
                .retryIfExceptionOfType(RuntimeException.class)
                //设置根据结果重试 比如返回的格式不对
                .retryIfResult(res -> res.getContent().split(ChartConstant.AI_SPLIT_STR).length < 3)
                //固定时长等待策略
                .withWaitStrategy(WaitStrategies.fixedWait(8, TimeUnit.SECONDS))
                //重试指定次数停止
                .withStopStrategy(StopStrategies.stopAfterAttempt(1))
                .withRetryListener(new RetryListener() {
                    @Override
                    public <V> void onRetry(Attempt<V> attempt) {
                        // 重试结果: 是异常终止, 还是正常返回
                        log.info("hasException={} asResult={}", attempt.hasException(), attempt.hasResult());
                    }
                })
                .build();

        try {
            DevChatResponse devChatResponse = retryer.call(() -> yuCongMingClient.doChat(devChatRequest).getData());
            log.info("AiResult = {}", devChatResponse.getContent());
            return devChatResponse.getContent();
        } catch (ExecutionException | RetryException e) {
            log.error("AI响应失败 error = {}", e.getMessage());
        }
        return "";
    }

    /**
     * 对话AI（MQ专用版
     * @param message
     * @return
     */
    public String doChatMq(long modelId,String message){
        DevChatRequest devChatRequest = new DevChatRequest();
        devChatRequest.setModelId(modelId);
        devChatRequest.setMessage(message);

        BaseResponse<DevChatResponse> response = yuCongMingClient.doChat(devChatRequest);
        // 如果响应为null，就抛出系统异常，提示“AI 响应错误”
        if (response == null) {
            return null;
            //throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 响应错误");
        }
        return response.getData().getContent();
    }

    /**
     * AI对话重试
     */
    public String retryDoChat(DevChatRequest devChatRequest) {
        //重试
        Retryer<DevChatResponse> retryer = RetryerBuilder.<DevChatResponse>newBuilder()
                //设置异常重试源 不可设置多个
                .retryIfExceptionOfType(RuntimeException.class)
                //设置根据结果重试  比如返回的格式不对
                .retryIfResult(res -> res.getContent().split(ChartConstant.AI_SPLIT_STR).length <
                        ChartConstant.CHART_SPLIT_LENGTH)
                //固定时长等待策略
                .withWaitStrategy(WaitStrategies.fixedWait(5, TimeUnit.SECONDS))
                //重试指定次数停止
                .withStopStrategy(StopStrategies.stopAfterAttempt(1))
                .withRetryListener(new RetryListener() {
                    @Override
                    public <V> void onRetry(Attempt<V> attempt) {
                        // 重试结果: 是异常终止, 还是正常返回
                        log.info("hasException={} asResult={}", attempt.hasException(), attempt.hasResult());
                    }
                })
                .build();
        try {
            DevChatResponse chatResponse = retryer.call(() -> yuCongMingClient.doChat(devChatRequest).getData());
            ThrowUtils.throwIf(chatResponse.getContent() == null, ErrorCode.SYSTEM_ERROR, "AI响应失败");
            return chatResponse.getContent();
        } catch (ExecutionException | RetryException e) {
            log.error("AI响应失败 error = {}", e.getMessage());
        }
        return "";
    }

}
