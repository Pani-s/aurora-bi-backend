package com.pani.bi.job.cycle;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pani.bi.bizmq.BiMessageProducer;
import com.pani.bi.constant.ChartConstant;
import com.pani.bi.manager.AiManagerYu;
import com.pani.bi.mapper.ChartMapper;
import com.pani.bi.model.entity.Chart;
import com.pani.bi.model.entity.ChartGenResult;
import com.pani.bi.service.ChartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author pani
 * @Description 用定时任务把失败状态的图表放到队列中:
 * 向每隔 10分钟 执行一个在数据库中【捞取失败的数据 重新进行ai分析】的操作
 */
@Component
@Slf4j
public class ReGenChartData {
    @Resource
    private ChartMapper chartMapper;
    @Resource
    private AiManagerYu aiManagerYu;
    @Resource
    private ChartService chartService;
    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @Resource
    private BiMessageProducer biMessageProducer;

    @Scheduled(cron = "0 0/10 * * * ?") // Every 10 minutes
    public void doUpdateFailedChart() {
        log.info("【把失败状态的图表放到队列中】");
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("chartState", ChartConstant.FAILED);
        queryWrapper.select("id");
        List<Chart> failedCharts = chartMapper.selectList(queryWrapper);
        failedCharts.forEach(this::updateFailedChartAsyncMq);
    }

    /**
     * 同步更新失败的图表
     *
     * @param chart
     */
    private void updateFailedChart(final Chart chart) {
        Long chartId = chart.getId();
//        List<Map<String, Object>> chartOriginalData = chartMapper.queryChartData(chartId);
//        String cvsData = ChartDataUtil.changeDataToCSV(chartOriginalData);
//        ChartGenResult result = ChartDataUtil.getGenResult(aiManager, chart.getGoal(), cvsData, chart.getChartType());
//        try {
//            chartService.updateById(new Chart(chartId, result.getGenChart(), result.getGenResult(), ChartConstant.CHART_STATUS_SUCCEED, ""));
//        } catch (Exception e) {
//            chartService.updateById(new Chart(chartId, ChartConstant.CHART_STATUS_FAILED, e.getMessage()));
//            log.error("更新图表数据失败，chartId:{}, error:{}", chartId, e.getMessage());
//        }
    }


    /**
     * 异步更新失败的图表 - 线程池
     *
     * @param chart
     */
    private void updateFailedChartAsync(final Chart chart) {
        CompletableFuture.runAsync(() -> {
            updateFailedChart(chart);
        }, threadPoolExecutor);
    }

    /**
     * 异步更新失败的图表（消息队列
     *
     * @param chart
     */
    private void updateFailedChartAsyncMq(Chart chart) {
        log.info("【放入失败状态的图表：id为{}】",chart.getId());
        biMessageProducer.sendMessage(String.valueOf(chart.getId()));
    }
}