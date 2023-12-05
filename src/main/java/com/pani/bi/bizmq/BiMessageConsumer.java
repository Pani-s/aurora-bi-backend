package com.pani.bi.bizmq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pani.bi.common.ErrorCode;
import com.pani.bi.constant.ChartConstant;
import com.pani.bi.exception.BusinessException;
import com.pani.bi.manager.AiManager;
import com.pani.bi.model.entity.Chart;
import com.pani.bi.service.ChartService;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static com.pani.bi.constant.CommonConstant.MODELID;

/**
 * @author Pani
 * @date Created in 2023/11/27 21:11
 * @description
 */

@Component
@Slf4j
public class BiMessageConsumer {

    @Resource
    private ChartService chartService;

    @Resource
    private AiManager aiManager;


    /**
     * 接收消息的方法
     * 使用@SneakyThrows注解简化异常处理
     * 使用@RabbitListener注解指定要监听的队列名称为"code_queue"，并设置消息的确认机制为手动确认
     *
     * @param message     接收到的消息内容，是一个字符串类型
     * @param channel     消息所在的通道，可以通过该通道与 RabbitMQ 进行交互，例如手动确认消息、拒绝消息等
     * @param deliveryTag 消息的投递标签，用于唯一标识一条消息
     */
    @SneakyThrows
    @RabbitListener(queues = {BiMqConstant.BI_QUEUE_NAME}, ackMode = "MANUAL")
    public void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("receiveMessage message = {}", message);
        long chartId;
        try {
            chartId = Long.parseLong(message);
        } catch (NumberFormatException e) {
            log.info("chartId转换失败。要不还是手动确认一下吧");
            channel.basicAck(deliveryTag, false);
            return;
            //throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }

        Chart updateChart = new Chart();
        updateChart.setId(chartId);
        // 把任务状态改为执行中 1
        updateChart.setChartState(ChartConstant.RUNNING);
        boolean b = chartService.updateById(updateChart);
        // 如果提交失败(一般情况下,更新失败可能意味着你的数据库出问题了)
        if (!b) {
            handleChartUpdateError(chartId, " 更新图表执行中状态失败");
            return;
        }
        Chart chart = chartService.getById(chartId);
        String userInput = buildUserInput(chart);
        String result = aiManager.doChatMq(MODELID, userInput);
        if (result == null) {
            handleChartUpdateError(chartId, "AI 响应错误");
            return;
        }
        //剪切【【【【【
        String[] split = result.split("【【【【【");
        if (split.length < 3) {
            handleChartUpdateError(chartId, "AI 生成结果有错误");
            return;
        }
        String genChart = split[1].trim();
        String genResult = split[2].trim();

        // 调用AI得到结果之后,再更新一次
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setGenChart(genChart);
        updateChartResult.setGenResult(genResult);
        updateChartResult.setChartState(ChartConstant.SUCCEED);
        boolean updateResult = chartService.updateById(updateChartResult);
        if (!updateResult) {
            handleChartUpdateError(chartId, "更新图表成功状态失败");
        }


        //手动确认
        channel.basicAck(deliveryTag, false);
        /*
         * @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag是一个方法参数注解,
         * 用于从消息头中获取投递标签(deliveryTag),在RabbitMQ中,每条消息都会被分配一个唯一的投递标签，
         * 用于标识该消息在通道中的投递状态和顺序。通过使用@Header(AmqpHeaders.DELIVERY_TAG)注解,
         * 可以从消息头中提取出该投递标签,并将其赋值给long deliveryTag参数。
         */
    }
    //region 有死信队列版

    /**
     * 消费正常消息
     *
     * @param message     消息内容
     * @param channel     通道
     * @param deliveryTag 标签
     */
    @RabbitListener(queues = {BiMqConstant.BI_QUEUE_NAME}, ackMode = "MANUAL")
    public void receiveMessageNew(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        if (StringUtils.isBlank(message)) {
            throwExceptionAndNackMessage(channel, deliveryTag);
            return;
        }
        log.info("receiveMessage message = {}", message);
        Chart chart = chartService.getById(message);
        if (chart == null) {
            throwExceptionAndNackMessage(channel, deliveryTag);
            return;
        }
        Long userId = chart.getUserId();
        // 检查用户任务计数器
        int userTaskCount = (int) getRunningTaskCount(userId);
        try {
            if (userTaskCount <= BiMqConstant.MAX_CONCURRENT_CHARTS) {
                //这位老哥的代码，因为我没把数据分离就不用了
                /*
                chartService.updateById(new Chart(Long.parseLong(message), ChartConstant.RUNNING,""));
                String csvData = ChartDataUtil.changeDataToCSV(chartMapper.queryChartData(Long.parseLong(message)));
                ThrowUtils.throwIf(StringUtils.isBlank(csvData), ErrorCode.PARAMS_ERROR);
                ChartGenResult genResult = ChartDataUtil.getGenResult(aiManager, chart.getGoal(), csvData, chart.getChartType());
                boolean result = chartService.updateById(new Chart(chart.getId(), genResult.getGenChart(), genResult.getGenResult(), ChartConstant.SUCCEED, ""));
                if (!result) {
                    throwExceptionAndNackMessage(channel, deliveryTag);
                }
                 */

                boolean b = chartService.updateById(new Chart(Long.parseLong(message), ChartConstant.RUNNING));
                Long chartId = chart.getId();
                if (!b) {
                    throwExceptionAndNackMessage(channel, deliveryTag);
                    return;
                }
                String userInput = buildUserInput(chart);
                String result = aiManager.doChatMq(MODELID, userInput);
                if (result == null) {
                    throwExceptionAndNackMessage(channel, deliveryTag);
                    return;
                }
                //剪切【【【【【
                String[] split = result.split("【【【【【");
                if (split.length < 3) {
                    throwExceptionAndNackMessage(channel, deliveryTag);
                    return;
                }
                String genChart = split[1].trim();
                String genResult = split[2].trim();

                // 调用AI得到结果之后,再更新一次
                Chart updateChartResult = new Chart();
                updateChartResult.setId(chartId);
                updateChartResult.setGenChart(genChart);
                updateChartResult.setGenResult(genResult);
                updateChartResult.setChartState(ChartConstant.SUCCEED);
                boolean updateResult = chartService.updateById(updateChartResult);
                if (!updateResult) {
                    throwExceptionAndNackMessage(channel, deliveryTag);
                    return;
                }

                channel.basicAck(deliveryTag, false);
                return;
            } else {
                channel.basicNack(deliveryTag, false, true);
            }
        } catch (Exception e) {
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            log.error(e.getMessage());
        }
    }

    /**
     * 死信消费异常消息
     *
     * @param message
     * @param channel
     * @param deliveryTag
     */
    @RabbitListener(queues = {BiMqConstant.BI_DLX_QUEUE_NAME}, ackMode = "MANUAL")
    public void receiveErrorMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        if (StringUtils.isBlank(message)) {
            throwExceptionAndNackMessage(channel, deliveryTag);
        }
        log.info("receiveErrorMessage message = {}", message);
        Chart chart = chartService.getById(message);
        if (chart == null) {
            throwExceptionAndNackMessage(channel, deliveryTag);
        }
        //todo:失败原因我怎么传？？？
        chartService.updateById(new Chart(Long.parseLong(message), ChartConstant.FAILED, "执行失败"));
        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    /**
     * 抛异常同时拒绝消息
     *
     * @param channel
     * @param deliveryTag
     */
    private void throwExceptionAndNackMessage(Channel channel, long deliveryTag) {
        try {
            channel.basicNack(deliveryTag, false, false);
        } catch (IOException e) {
            log.error(e.getMessage());
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR);
    }

    /**
     * 获取当前用户正在运行的任务数量，就算服务器出现问题，数据已经持久化到硬盘之中
     *
     * @param userId
     * @return
     */
    private long getRunningTaskCount(Long userId) {
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", userId).eq("chartState", ChartConstant.RUNNING);
        return chartService.count(queryWrapper);
    }

    //endregion


    private void handleChartUpdateError(long chartId, String execMessage) {
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setChartState(ChartConstant.FAILED);
        String time = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss | ").format(LocalDateTime.now());
        updateChartResult.setExecMessage(time + execMessage);
        boolean updateResult = chartService.updateById(updateChartResult);
        if (!updateResult) {
            log.error("更新图表失败状态失败" + chartId + "," + execMessage);
        }
    }


    private String buildUserInput(Chart chart) {
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求：");

        // 拼接分析目标
        String userGoal = chart.getGoal();
        // 如果图表类型不为空
        if (StringUtils.isNotBlank(chart.getChartType())) {
            // 就将分析目标拼接上“请使用”+图表类型
            userGoal += "，请使用" + chart.getChartType();
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");

        // 压缩后的数据（把multipartFile传进来）
        userInput.append(chart.getChartData()).append("\n");
        return userInput.toString();
    }


}
