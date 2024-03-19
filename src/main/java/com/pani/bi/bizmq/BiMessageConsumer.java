package com.pani.bi.bizmq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pani.bi.common.ErrorCode;
import com.pani.bi.constant.ChartConstant;
import com.pani.bi.exception.BusinessException;
import com.pani.bi.manager.AiManagerXunFei;
import com.pani.bi.manager.AiManagerYu;
import com.pani.bi.model.bo.SaveChartResponse;
import com.pani.bi.model.entity.Chart;
import com.pani.bi.model.entity.ChartGenResult;
import com.pani.bi.model.entity.ChartRawCsv;
import com.pani.bi.service.ChartGenResultService;
import com.pani.bi.service.ChartRawCsvService;
import com.pani.bi.service.ChartService;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.aop.framework.AopContext;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static com.pani.bi.constant.CommonConstant.MODELID;

/**
 * @author Pani
 * @date Created in 2023/11/27 21:11
 * @description /*
 * -- @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag是一个方法参数注解,
 * 用于从消息头中获取投递标签(deliveryTag),在RabbitMQ中,每条消息都会被分配一个唯一的投递标签，
 * 用于标识该消息在通道中的投递状态和顺序。通过使用@Header(AmqpHeaders.DELIVERY_TAG)注解,
 * 可以从消息头中提取出该投递标签,并将其赋值给long deliveryTag参数。
 */

@Component
@Slf4j
public class BiMessageConsumer {

    @Resource
    private ChartService chartService;

    @Resource
    private ChartRawCsvService chartRawCsvService;

    @Resource
    private ChartGenResultService chartGenResultService;

    @Resource
    private AiManagerYu aiManagerYu;

    @Resource
    private AiManagerXunFei aiManagerXunFei;


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
    //    @RabbitListener(queues = {BiMqConstant.BI_QUEUE_NAME}, ackMode = "MANUAL")
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
        if (!b) {
            handleChartUpdateError(chartId, " 更新图表执行中状态失败");
            channel.basicNack(deliveryTag,false,false);
            return;
        }
        Chart chart = chartService.getById(chartId);
        Integer aiChannel = chart.getAiChannel();
        String userInput = buildUserInput(chart);
        if(userInput == null){
            handleChartUpdateError(chartId, "未查询到csv数据");
            channel.basicNack(deliveryTag,false,false);
        }

        String result = null;
        if (aiChannel.equals(ChartConstant.YU_CONG_MING)) {
            result = aiManagerYu.doChatMq(MODELID, userInput.toString());
        } else if (aiChannel.equals(ChartConstant.XUN_FEI)) {
            result = aiManagerXunFei.doChat(userInput.toString());
        }
        if (result == null) {
            handleChartUpdateError(chartId, "AI 响应错误");
            channel.basicNack(deliveryTag,false,true);
            return;
        }
        //剪切【【【【【
        String[] split = result.split(ChartConstant.AI_SPLIT_STR);
        if (split.length < ChartConstant.CHART_SPLIT_LENGTH) {
            handleChartUpdateError(chartId, "AI 生成结果有错误");
            channel.basicNack(deliveryTag,false,true);
            return;
        }
        String genChart = split[1].trim();
        String genResult = split[2].trim();

        // 调用AI得到结果之后,再更新一次
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setChartState(ChartConstant.SUCCEED);

        ChartGenResult chartGenResult = new ChartGenResult();
        chartGenResult.setChartId(chartId);
        chartGenResult.setGenChart(genChart);
        chartGenResult.setGenResult(genResult);

        try {
            BiMessageConsumer biMessageConsumer = (BiMessageConsumer) AopContext.currentProxy();
            biMessageConsumer.updateSuccessChart(updateChartResult,chartGenResult);
        }catch (RuntimeException e){
            handleChartUpdateError(chartId, "保存图表生成结果时出错");
            channel.basicNack(deliveryTag,false,false);
            return;
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
        System.err.println("============消费者消费消息==================");
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
        Integer aiChannel = chart.getAiChannel();
        Long userId = chart.getUserId();
        //检查用户任务计数器 ，如果同时在开好几个生成的，就不让你现在生成了
        int userTaskCount = (int) getRunningTaskCount(userId);
        try {
            if (userTaskCount > BiMqConstant.MAX_CONCURRENT_CHARTS) {
                channel.basicNack(deliveryTag, false, false);
            }
            boolean b = chartService.updateById(new Chart(Long.parseLong(message), ChartConstant.RUNNING));
            Long chartId = chart.getId();
            if (!b) {
                log.error("更新图表状态为执行状态失败");
                throwExceptionAndNackMessage(channel, deliveryTag);
                return;
            }

            String userInput = buildUserInput(chart);
            if(userInput == null){
                log.error("找不到对应的csv文件");
                //如果是这样的话，永远都生成不了，但是会出现这种数据不一致的情况吗，存的时候我都开了事务
                throwExceptionAndNackMessage(channel, deliveryTag);
                return;
            }

            String result = null;
            if (aiChannel.equals(ChartConstant.YU_CONG_MING)) {
                result = aiManagerYu.doChat(MODELID, userInput);
            } else if (aiChannel.equals(ChartConstant.XUN_FEI)) {
                result = aiManagerXunFei.doChat(userInput);
            }
            if (result == null) {
                throwExceptionAndNackMessage(channel, deliveryTag);
                return;
            }
            //剪切【【【【【
            String[] split = result.split(ChartConstant.AI_SPLIT_STR);
            if (split.length < ChartConstant.CHART_SPLIT_LENGTH) {
                throwExceptionAndNackMessage(channel, deliveryTag);
                return;
            }
            String genChart = split[1].trim();
            String genResult = split[2].trim();

            // 调用AI得到结果之后,再更新一次
            Chart updateChartResult = new Chart();
            updateChartResult.setId(chartId);
            updateChartResult.setChartState(ChartConstant.SUCCEED);

            ChartGenResult chartGenResult = new ChartGenResult();
            chartGenResult.setChartId(chartId);
            chartGenResult.setGenChart(genChart);
            chartGenResult.setGenResult(genResult);

            BiMessageConsumer biMessageConsumer = (BiMessageConsumer) AopContext.currentProxy();
            biMessageConsumer.updateSuccessChart(updateChartResult,chartGenResult);

            //确认消息、、
            channel.basicAck(deliveryTag, false);
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
     * 生成了成功的结果，存进db 【开启事务】
     * @param chart
     * @param chartGenResult
     */
    @Transactional(rollbackFor = RuntimeException.class)
    public void updateSuccessChart(Chart chart,ChartGenResult chartGenResult){
        boolean updateResult = chartService.updateById(chart);
        if (!updateResult) {
            handleChartUpdateError(chart.getId(), "更新图表成功状态失败");
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        boolean b = chartGenResultService.saveOrUpdate(chartGenResult);
        if(!b){
            handleChartUpdateError(chart.getId(), "更新图表成功状态失败");
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
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
        log.info("---------死信消费异常消息--------");
        if (StringUtils.isBlank(message)) {
            throwExceptionAndNackMessage(channel, deliveryTag);
        }
        log.info("receiveErrorMessage message = {}", message);
        Chart chart = chartService.getById(message);
        if (chart == null) {
            throwExceptionAndNackMessage(channel, deliveryTag);
        }
        //失败原因我怎么传？？？ 传不了，只能统一失败了
        chartService.updateById(new Chart(Long.parseLong(message), ChartConstant.FAILED, "执行失败 X_X"));
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
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "出了点问题~~");
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


    //region other方法

    /**
     * 解决图标更新时产生的error
     *
     * @param chartId
     * @param execMessage
     */
    private void handleChartUpdateError(long chartId, String execMessage) {
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setChartState(ChartConstant.FAILED);
        String time = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss | ").format(LocalDateTime.now());
        updateChartResult.setExecMessage(time + execMessage);
        boolean updateResult = chartService.updateById(updateChartResult);

        if (!updateResult) {
            String s = "更新图表失败的状态失败" + chartId + "," + execMessage;
            log.error(s);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, s);
        }
    }


    private String buildUserInput(Chart chart) {
        ChartRawCsv chartRawCsv = chartRawCsvService.getById(chart.getId());
        if(chartRawCsv == null){
            return null;
        }
        String csvData = chartRawCsv.getCsvData();

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

        userInput.append(csvData).append("\n");
        return userInput.toString();
    }
    //endregion

}
