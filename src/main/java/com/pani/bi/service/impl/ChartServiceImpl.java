package com.pani.bi.service.impl;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pani.bi.bizmq.BiMessageProducer;
import com.pani.bi.common.ErrorCode;
import com.pani.bi.constant.ChartConstant;
import com.pani.bi.constant.CommonConstant;
import com.pani.bi.constant.FileConstant;
import com.pani.bi.exception.BusinessException;
import com.pani.bi.exception.ThrowUtils;
import com.pani.bi.manager.AiManagerXunFei;
import com.pani.bi.manager.AiManagerYu;
import com.pani.bi.mapper.ChartMapper;
import com.pani.bi.model.bo.SaveChartResponse;
import com.pani.bi.model.dto.chart.ChartEditRequest;
import com.pani.bi.model.dto.chart.ChartQueryRequest;
import com.pani.bi.model.dto.chart.GenChartByAiRequest;
import com.pani.bi.model.entity.Chart;
import com.pani.bi.model.entity.ChartGenResult;
import com.pani.bi.model.entity.ChartRawCsv;
import com.pani.bi.model.entity.User;
import com.pani.bi.model.vo.BiResponse;
import com.pani.bi.model.vo.ChartVO;
import com.pani.bi.model.vo.ChartVOCsv;
import com.pani.bi.service.ChartGenResultService;
import com.pani.bi.service.ChartRawCsvService;
import com.pani.bi.service.ChartService;
import com.pani.bi.service.UserService;
import com.pani.bi.utils.ExcelUtils;
import com.pani.bi.utils.SqlUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

import static com.pani.bi.constant.CommonConstant.MODELID;

/**
 * @author Pani
 * @description 针对表【chart(图表信息表)】的数据库操作Service实现
 * @createDate 2023-11-17 15:13:42
 */
@Service
public class ChartServiceImpl extends ServiceImpl<ChartMapper, Chart>
        implements ChartService {

    /**
     * 定义合法的后缀列表
     */
    final List<String> validFileSuffixList = Arrays.asList("xls", "xlsx");

    //    @Resource
    //    private RedisLimiterManager redisLimiterManager;

    @Resource
    private UserService userService;

    @Resource
    private ChartRawCsvService chartRawCsvService;

    @Resource
    private ChartGenResultService chartGenResultService;

    @Resource
    private BiMessageProducer biMessageProducer;

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @Resource
    private AiManagerYu aiManagerYu;

    @Resource
    private AiManagerXunFei aiManagerXunFei;

    @Override
    public QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest) {
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        if (chartQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chartQueryRequest.getId();
        String goal = chartQueryRequest.getGoal();
        String name = chartQueryRequest.getName();
        String chartType = chartQueryRequest.getChartType();
        Long userId = chartQueryRequest.getUserId();
        String sortField = chartQueryRequest.getSortField();
        String sortOrder = chartQueryRequest.getSortOrder();

        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.like(StringUtils.isNotBlank(goal), "goal", goal);
        queryWrapper.like(StringUtils.isNotBlank(name), "name", name);
        queryWrapper.eq(StringUtils.isNotBlank(chartType), "chartType", chartType);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq("isDelete", false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    @Transactional(rollbackFor = RuntimeException.class)
    @Override
    public boolean editChartWithCsvData(ChartEditRequest chartEditRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        long id = chartEditRequest.getId();
        // 判断是否存在
        Chart oldChart = this.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR, "图表不存在");
        // 仅本人或管理员可编辑
        if (!oldChart.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限");
        }
        String csvData = chartEditRequest.getCsvData();
        ThrowUtils.throwIf(!chartRawCsvService.isValidCsv(csvData), ErrorCode.PARAMS_ERROR, "非法的csv");

        Chart chart = new Chart();
        ChartRawCsv chartRawCsv = new ChartRawCsv();
        chartRawCsv.setChartId(chartEditRequest.getId());
        chartRawCsv.setCsvData(csvData);
        chartRawCsv.setIsDelete(0);
        BeanUtils.copyProperties(chartEditRequest, chart);
        //因为涉及到两个表的修改，所以开启事务
        boolean result = this.updateById(chart);
        boolean b = chartRawCsvService.updateById(chartRawCsv);
        if (!result || !b) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "修改失败");
        }
        return result;
    }

    @Override
    public boolean isValidExcel(MultipartFile multipartFile) {
        /*
        校检文件：大小，后缀
        利用FileUtil工具类中的getSuffix方法获取文件后缀名(例如:aaa.png,suffix应该保存为png)
         */
        long fileSize = multipartFile.getSize();
        ThrowUtils.throwIf(fileSize > FileConstant.ONE_MB, ErrorCode.PARAMS_ERROR, "文件过大！");
        String suffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀不合法！");
        return true;
    }

    //region 生成图表相关
    @Override
    public String buildUserInput(GenChartByAiRequest genChartByAiRequest, String csvData) {
        //组织语句....
        StringBuffer userInput = new StringBuffer();
        userInput.append("分析需求：");
        // 拼接分析目标
        String userGoal = genChartByAiRequest.getGoal();
        // 如果图表类型不为空
        if (StringUtils.isNotBlank(genChartByAiRequest.getChartType())) {
            // 就将分析目标拼接上“请使用”+图表类型
            userGoal += "，请使用" + genChartByAiRequest.getChartType();
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");
        userInput.append(csvData).append("\n");
        return userInput.toString();
    }

    @Transactional(rollbackFor = RuntimeException.class)
    @Override
    public SaveChartResponse saveChartByAiAsync(MultipartFile multipartFile, GenChartByAiRequest genChartByAiRequest, User loginUser) {
        String chartType = genChartByAiRequest.getChartType();
        String name = genChartByAiRequest.getName();
        Integer aiChannel = genChartByAiRequest.getAiChannel();
        String goal = genChartByAiRequest.getGoal();
        // 校验
        //ai channel
        ThrowUtils.throwIf(!aiChannel.equals(ChartConstant.YU_CONG_MING) && !aiChannel.equals(ChartConstant.XUN_FEI)
                , ErrorCode.PARAMS_ERROR, "此AI 线路不存在");
        // 如果分析目标为空，就抛出请求参数错误异常，并给出提示
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        // 如果名称不为空，并且名称长度大于100，就抛出异常，并给出提示
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");
        isValidExcel(multipartFile);

        //拼接
        String csvData = ExcelUtils.excelToCsv(multipartFile);

        // 先把图表保存到数据库中！！！
        Chart chart = new Chart();
        chart.setGoal(goal);
        chart.setName(name);
        chart.setChartType(chartType);
        chart.setUserId(loginUser.getId());
        chart.setAiChannel(aiChannel);
        //0等待，其实可以不写，因为default  chart.setChartState(0);
        boolean saveResult = this.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表信息保存失败，请稍后重试");
        ChartRawCsv chartRawCsv = new ChartRawCsv();
        chartRawCsv.setChartId(chart.getId());
        chartRawCsv.setCsvData(csvData);
        boolean saved = chartRawCsvService.save(chartRawCsv);
        ThrowUtils.throwIf(!saved, ErrorCode.SYSTEM_ERROR, "表格文件保存失败，请稍后重试");

        SaveChartResponse saveChartResponse = new SaveChartResponse();
        saveChartResponse.setChartId(chart.getId());
        saveChartResponse.setAiChannel(chart.getAiChannel());
        saveChartResponse.setCsvData(csvData);
        return saveChartResponse;
    }

    @Override
    public BiResponse genChartByAiAsyncMq(MultipartFile multipartFile, GenChartByAiRequest genChartByAiRequest, User loginUser) {
        //@Transactional 先得到代理。。
        ChartService chartServiceProxy = (ChartService) AopContext.currentProxy();
        SaveChartResponse saveChartResponse = chartServiceProxy.saveChartByAiAsync(multipartFile, genChartByAiRequest, loginUser);

        Long chartId = saveChartResponse.getChartId();
        //mq
        biMessageProducer.sendMessage(String.valueOf(chartId));

        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chartId);
        return biResponse;
    }

    @Override
    public BiResponse genChartByAiAsync(MultipartFile multipartFile, GenChartByAiRequest genChartByAiRequest, User loginUser) {
        //@Transactional 先得到代理。。
        ChartService chartServiceProxy = (ChartService) AopContext.currentProxy();
        SaveChartResponse saveChartResponse = chartServiceProxy.saveChartByAiAsync(multipartFile, genChartByAiRequest, loginUser);

        Integer aiChannel = saveChartResponse.getAiChannel();
        Long chartId = saveChartResponse.getChartId();
        String userInput = buildUserInput(genChartByAiRequest, saveChartResponse.getCsvData());

        //异步
        //线程池满了会抛异常 -- 告诉用户生成失败就好啦
        CompletableFuture.runAsync(() -> {
            Chart updateChart = new Chart();
            updateChart.setId(chartId);
            // 把任务状态改为执行中 1
            updateChart.setChartState(ChartConstant.RUNNING);
            boolean b = this.updateById(updateChart);
            // 如果提交失败(一般情况下,更新失败可能意味着你的数据库出问题了)
            if (!b) {
                handleChartUpdateError(chartId, "更新图表执行中状态失败");
                return;
            }
            String result = null;
            if (aiChannel.equals(ChartConstant.YU_CONG_MING)) {
                result = aiManagerYu.doChat(MODELID, userInput);
            } else if (aiChannel.equals(ChartConstant.XUN_FEI)) {
                //                result = aiManagerXunFei.doChat(userInput.toString());
                result = aiManagerXunFei.doChat(userInput);
            }
            if (result == null) {
                handleChartUpdateError(chartId, "AI 生成失败");
            }
            //剪切【【【【【
            String[] split = result.split(ChartConstant.AI_SPLIT_STR);
            if (split.length < ChartConstant.CHART_SPLIT_LENGTH) {
                handleChartUpdateError(chartId, "AI 生成错误");
            }
            String genChart = split[1].trim();
            String genResult = split[2].trim();

            ChartGenResult chartGenResult = new ChartGenResult();
            chartGenResult.setChartId(chartId);
            chartGenResult.setGenChart(genChart);
            chartGenResult.setGenResult(genResult);
            boolean saved = chartGenResultService.save(chartGenResult);
            if (!saved) {
                handleChartUpdateError(chartId, "更新图表分析结果时失败");
            }

            // 调用AI得到结果之后,再更新一次
            Chart updateChartResult = new Chart();
            updateChartResult.setId(chartId);
            updateChartResult.setChartState(ChartConstant.SUCCEED);
            boolean updateResult = this.updateById(updateChartResult);
            if (!updateResult) {
                handleChartUpdateError(chartId, "更新图表成功状态时失败");
            }
        }, threadPoolExecutor);
        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chartId);
        return biResponse;
    }

    @Transactional(rollbackFor = RuntimeException.class)
    @Override
    public BiResponse genChartByAi(MultipartFile multipartFile, GenChartByAiRequest genChartByAiRequest, User loginUser) {
        String chartType = genChartByAiRequest.getChartType();
        Integer aiChannel = genChartByAiRequest.getAiChannel();
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        // 校验
        //ai channel
        ThrowUtils.throwIf(!aiChannel.equals(ChartConstant.YU_CONG_MING) && !aiChannel.equals(ChartConstant.XUN_FEI)
                , ErrorCode.PARAMS_ERROR, "此AI 线路不存在");
        // 如果分析目标为空，就抛出请求参数错误异常，并给出提示
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        // 如果名称不为空，并且名称长度大于100，就抛出异常，并给出提示
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");

        isValidExcel(multipartFile);

        // 压缩后的数据（把multipartFile传进来）
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        String userInput = buildUserInput(genChartByAiRequest, csvData);

        //        String result = aiManager.doChat(MODELID, userInput.toString());
        String result = null;
        if (aiChannel.equals(ChartConstant.YU_CONG_MING)) {
            result = aiManagerYu.doChat(MODELID, userInput.toString());
        } else if (aiChannel.equals(ChartConstant.XUN_FEI)) {
            result = aiManagerXunFei.doChat(userInput.toString());
        }
        //剪切【【【【【
        String[] split = result.split(ChartConstant.AI_SPLIT_STR);
        if (split.length < ChartConstant.CHART_SPLIT_LENGTH) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 生成错误");
        }

        String genChart = split[1].trim();
        String genResult = split[2].trim();
        Chart chart = new Chart();
        chart.setGoal(goal);
        chart.setName(name);
        //        chart.setChartData(csvData);
        chart.setChartType(chartType);
        //        chart.setGenChart(genChart);
        //        chart.setGenResult(genResult);
        chart.setUserId(loginUser.getId());
        chart.setChartState(ChartConstant.SUCCEED);
        //保存一下
        boolean saveResult = this.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表信息保存失败");

        ChartRawCsv chartRawCsv = new ChartRawCsv();
        chartRawCsv.setChartId(chart.getId());
        chartRawCsv.setCsvData(csvData);
        boolean saved = chartRawCsvService.save(chartRawCsv);
        ThrowUtils.throwIf(!saved, ErrorCode.SYSTEM_ERROR, "表格文件保存失败，请稍后重试");

        ChartGenResult chartGenResult = new ChartGenResult();
        chartGenResult.setChartId(chart.getId());
        chartGenResult.setGenChart(genChart);
        chartGenResult.setGenResult(genResult);
        boolean save = chartGenResultService.save(chartGenResult);
        ThrowUtils.throwIf(!save, ErrorCode.SYSTEM_ERROR, "图表生成结果保存失败，请稍后重试");


        BiResponse biResponse = new BiResponse();
        biResponse.setGenResult(genResult);
        biResponse.setGenChart(genChart);
        biResponse.setChartId(chart.getId());
        return biResponse;
    }


    @Override
    public boolean reloadChartByAi(long chartId, HttpServletRequest request) {
        ThrowUtils.throwIf(chartId < 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        final String key = ChartConstant.GEN_CHART_LIMIT_KEY + loginUser.getId();
        // 限流判断
        //        redisLimiterManager.doRateLimit(key);
        /*
        发送消息
         */
        //        biMessageProducer.sendMessage(String.valueOf(chartId));
        //异步
        //线程池满了会抛异常 -- 已解决，目前先忽视掉了
        CompletableFuture.runAsync(() -> {
            Chart updateChart = new Chart();
            updateChart.setId(chartId);
            // 把任务状态改为执行中 1
            updateChart.setChartState(ChartConstant.RUNNING);
            boolean b = this.updateById(updateChart);
            // 如果提交失败(一般情况下,更新失败可能意味着你的数据库出问题了)
            if (!b) {
                handleChartUpdateError(chartId, "更新图表执行中状态失败");
                return;
            }
            String result = null;

            Chart chartById = this.getById(chartId);
            Integer aiChannel = chartById.getAiChannel();

            GenChartByAiRequest genChartByAiRequest = new GenChartByAiRequest();
            BeanUtils.copyProperties(chartById, genChartByAiRequest);

            ChartRawCsv rawCsv = chartRawCsvService.getById(chartId);
            if (rawCsv == null) {
                handleChartUpdateError(chartId, "未找到对应csv数据！");
                return;
            }

            String userInput = buildUserInput(genChartByAiRequest, rawCsv.getCsvData());


            if (aiChannel.equals(ChartConstant.YU_CONG_MING)) {
                result = aiManagerYu.doChat(MODELID, userInput);
            } else if (aiChannel.equals(ChartConstant.XUN_FEI)) {
                //result = aiManagerXunFei.doChat(userInput.toString());
                result = aiManagerXunFei.doChat(userInput);
            }
            if (result == null) {
                handleChartUpdateError(chartId, "AI 生成失败");
            }
            //剪切【【【【【
            String[] split = result.split(ChartConstant.AI_SPLIT_STR);
            if (split.length < ChartConstant.CHART_SPLIT_LENGTH) {
                handleChartUpdateError(chartId, "AI 生成错误");
            }
            String genChart = split[1].trim();
            String genResult = split[2].trim();

            ChartGenResult chartGenResult = new ChartGenResult();
            chartGenResult.setChartId(chartId);
            chartGenResult.setGenChart(genChart);
            chartGenResult.setGenResult(genResult);
            boolean saved = chartGenResultService.updateById(chartGenResult);
            if (!saved) {
                handleChartUpdateError(chartId, "更新图表分析结果时失败");
            }

            // 调用AI得到结果之后,再更新一次
            Chart updateChartResult = new Chart();
            updateChartResult.setId(chartId);
            updateChartResult.setChartState(ChartConstant.SUCCEED);
            boolean updateResult = this.updateById(updateChartResult);
            if (!updateResult) {
                handleChartUpdateError(chartId, "更新图表成功状态时失败");
            }
        }, threadPoolExecutor);
        return true;
    }


    @Override
    public void handleChartUpdateError(long chartId, String execMessage) {
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setChartState(ChartConstant.FAILED);
        updateChartResult.setExecMessage(execMessage);
        boolean updateResult = this.updateById(updateChartResult);
        System.err.println("【【【【更新图表失败状态失败");
        if (!updateResult) {
            log.error("更新图表失败状态失败" + chartId + "," + execMessage);
        }

    }
    //endregion

    @Override
    public Page<ChartVO> getChartVOPage(Page<Chart> chartPage) {
        Page<ChartVO> chartVOPage = new Page<>(chartPage.getCurrent(), chartPage.getSize(), chartPage.getTotal());
        List<Chart> chartList = chartPage.getRecords();
        List<ChartVO> chartVOList = null;
        if (chartPage.getTotal() != 0) {
            List<Long> chartIdList = chartList.stream().map(Chart::getId).collect(Collectors.toList());
            Map<Long, List<ChartGenResult>> chartIdListMap = chartGenResultService.listByIds(chartIdList).stream().
                    collect(Collectors.groupingBy(ChartGenResult::getChartId));

            chartVOList = chartList.stream().map((chart) -> {
                ChartVO chartVO = new ChartVO();
                BeanUtils.copyProperties(chart, chartVO);
                Long chartId = chart.getId();
                if (chartIdListMap.containsKey(chartId)) {
                    ChartGenResult chartGenResult = chartIdListMap.get(chartId).get(0);
                    chartVO.setGenChart(chartGenResult.getGenChart());
                    chartVO.setGenResult(chartGenResult.getGenResult());
                }
                return chartVO;
            }).collect(Collectors.toList());
        } else {
            chartVOList = new ArrayList<>();
        }


        chartVOPage.setRecords(chartVOList);
        return chartVOPage;
    }

    @Override
    public ChartVO getChartVO(Chart chart) {
        ChartVO chartVO = new ChartVO();
        BeanUtils.copyProperties(chart, chartVO);
        Long chartId = chart.getId();
        ChartGenResult chartGenResult = chartGenResultService.getById(chartId);
        chartVO.setGenChart(chartGenResult.getGenChart());
        chartVO.setGenResult(chartGenResult.getGenResult());
        return chartVO;
    }

    @Override
    public ChartVOCsv getChartVOCsv(Chart chart) {
        ChartVOCsv chartVOCsv = new ChartVOCsv();
        BeanUtils.copyProperties(chart, chartVOCsv);
        Long chartId = chart.getId();
        ChartRawCsv csv = chartRawCsvService.getById(chartId);
        chartVOCsv.setChartCsvData(csv.getCsvData());
        return chartVOCsv;
    }

}




