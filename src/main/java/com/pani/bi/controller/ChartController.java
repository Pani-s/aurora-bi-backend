package com.pani.bi.controller;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pani.bi.annotation.AuthCheck;
import com.pani.bi.bizmq.BiMessageProducer;
import com.pani.bi.common.BaseResponse;
import com.pani.bi.common.DeleteRequest;
import com.pani.bi.common.ErrorCode;
import com.pani.bi.common.ResultUtils;
import com.pani.bi.constant.ChartConstant;
import com.pani.bi.constant.CommonConstant;
import com.pani.bi.constant.FileConstant;
import com.pani.bi.constant.UserConstant;
import com.pani.bi.exception.BusinessException;
import com.pani.bi.exception.ThrowUtils;
import com.pani.bi.manager.AiManagerYu;
import com.pani.bi.manager.AiManagerXunFei;
import com.pani.bi.manager.RedisLimiterManager;
import com.pani.bi.model.dto.chart.*;
import com.pani.bi.model.entity.Chart;
import com.pani.bi.model.entity.User;
import com.pani.bi.model.vo.BiResponse;
import com.pani.bi.service.ChartService;
import com.pani.bi.service.UserService;
import com.pani.bi.utils.ExcelUtils;
import com.pani.bi.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

import static com.pani.bi.constant.CommonConstant.MODELID;

/**
 * 帖子接口
 *
 * @author pani
 */
@RestController
@RequestMapping("/chart")
@Slf4j
public class ChartController {

    @Resource
    private ChartService chartService;

    @Resource
    private UserService userService;

    @Resource
    private AiManagerYu aiManagerYu;

    @Resource
    private AiManagerXunFei aiManagerXunFei;

    @Resource
    private RedisLimiterManager redisLimiterManager;

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @Resource
    private BiMessageProducer biMessageProducer;



    /**
     * 定义合法的后缀列表
     */
    final List<String> validFileSuffixList = Arrays.asList("xls", "xlsx");

    // region 增删改查

    /**
     * 创建
     *
     * @param chartAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addChart(@RequestBody ChartAddRequest chartAddRequest, HttpServletRequest request) {
        if (chartAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartAddRequest, chart);
        User loginUser = userService.getLoginUser(request);
        chart.setUserId(loginUser.getId());
        boolean result = chartService.save(chart);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newChartId = chart.getId();
        return ResultUtils.success(newChartId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteChart(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldChart.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = chartService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新（仅管理员）
     *
     * @param chartUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateChart(@RequestBody ChartUpdateRequest chartUpdateRequest) {
        if (chartUpdateRequest == null || chartUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartUpdateRequest, chart);
        long id = chartUpdateRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<Chart> getChartById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = chartService.getById(id);
        if (chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(chart);
    }

    /**
     * 分页获取列表（封装类）
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<Chart>> listChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
                                                     HttpServletRequest request) {
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);

    }

    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<Chart>> listMyChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
                                                       HttpServletRequest request) {
        if (chartQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        chartQueryRequest.setUserId(loginUser.getId());
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);

    }


    /**
     * 编辑（用户）
     *
     * @param chartEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editChart(@RequestBody ChartEditRequest chartEditRequest, HttpServletRequest request) {
        if (chartEditRequest == null || chartEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartEditRequest, chart);
        User loginUser = userService.getLoginUser(request);
        long id = chartEditRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldChart.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    // endregion

    /**
     * 获取查询包装类
     *
     * @param chartQueryRequest
     * @return
     */
    private QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest) {
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

    // region AI智能分析（大头）

    /**
     * 智能分析--同步版
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen")
    public BaseResponse<BiResponse> genChartByAi(@RequestPart("file") MultipartFile multipartFile,
                                                 GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        // 通过response对象拿到用户id(必须登录才能使用)
        User loginUser = userService.getLoginUser(request);
        redisLimiterManager.doRateLimit(ChartConstant.GEN_CHART_LIMIT_KEY + loginUser.getId());

        String chartType = genChartByAiRequest.getChartType();
        Integer aiChannel = genChartByAiRequest.getAiChannel();
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        // 校验
        //ai channel
        ThrowUtils.throwIf(!aiChannel.equals(ChartConstant.YU_CONG_MING)&&!aiChannel.equals(ChartConstant.XUN_FEI)
        ,ErrorCode.PARAMS_ERROR, "此AI 线路不存在");
        // 如果分析目标为空，就抛出请求参数错误异常，并给出提示
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        // 如果名称不为空，并且名称长度大于100，就抛出异常，并给出提示
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");

        /*
        校检文件：大小，后缀
        利用FileUtil工具类中的getSuffix方法获取文件后缀名(例如:aaa.png,suffix应该保存为png)
         */
        long fileSize = multipartFile.getSize();
        ThrowUtils.throwIf(fileSize > FileConstant.ONE_MB, ErrorCode.PARAMS_ERROR, "文件过大，超过 1M");
        String suffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀不合法");


        //组织语句....
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求：");

        // 拼接分析目标
        String userGoal = goal;
        // 如果图表类型不为空
        if (StringUtils.isNotBlank(chartType)) {
            // 就将分析目标拼接上“请使用”+图表类型
            userGoal += "，请使用" + chartType;
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");

        // 压缩后的数据（把multipartFile传进来）
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(csvData).append("\n");


//        String result = aiManager.doChat(MODELID, userInput.toString());
        String result = null;
        if(aiChannel.equals(ChartConstant.YU_CONG_MING)){
            result = aiManagerYu.doChat(MODELID, userInput.toString());
        }else if(aiChannel.equals(ChartConstant.XUN_FEI)){
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
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setGenChart(genChart);
        chart.setGenResult(genResult);
        chart.setUserId(loginUser.getId());
        chart.setChartState(ChartConstant.SUCCEED);
        //保存一下
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");

        BiResponse biResponse = new BiResponse();
        biResponse.setGenResult(genResult);
        biResponse.setGenChart(genChart);
        biResponse.setChartId(chart.getId());


        return ResultUtils.success(biResponse);

    }

    /**
     * 智能分析--异步版
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen_async")
    public BaseResponse<BiResponse> genChartByAiAsync(@RequestPart("file") MultipartFile multipartFile,
                                                      GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        // 通过response对象拿到用户id(必须登录才能使用)
        User loginUser = userService.getLoginUser(request);
        //key设为一个 static final变量 genChartByAi_
        redisLimiterManager.doRateLimit(ChartConstant.GEN_CHART_LIMIT_KEY + loginUser.getId());

        String chartType = genChartByAiRequest.getChartType();
        String name = genChartByAiRequest.getName();
        Integer aiChannel = genChartByAiRequest.getAiChannel();
        String goal = genChartByAiRequest.getGoal();
        // 校验
        //ai channel
        ThrowUtils.throwIf(!aiChannel.equals(ChartConstant.YU_CONG_MING) && !aiChannel.equals(ChartConstant.XUN_FEI)
                ,ErrorCode.PARAMS_ERROR, "此AI 线路不存在");
        // 如果分析目标为空，就抛出请求参数错误异常，并给出提示
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        // 如果名称不为空，并且名称长度大于100，就抛出异常，并给出提示
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");

        /*
        校检文件：大小，后缀
        利用FileUtil工具类中的getSuffix方法获取文件后缀名(例如:aaa.png,suffix应该保存为png)
         */
        long fileSize = multipartFile.getSize();
        ThrowUtils.throwIf(fileSize > FileConstant.ONE_MB, ErrorCode.PARAMS_ERROR, "文件过大，超过 1M");
        String suffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀不合法");

        //组织语句....
        StringBuffer userInput = new StringBuffer();
        userInput.append("分析需求：");

        // 拼接分析目标
        String userGoal = goal;
        // 如果图表类型不为空
        if (StringUtils.isNotBlank(chartType)) {
            // 就将分析目标拼接上“请使用”+图表类型
            userGoal += "，请使用" + chartType;
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");

        // 压缩后的数据（把multipartFile传进来）
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(csvData).append("\n");

        // 先把图表保存到数据库中！！！
        Chart chart = new Chart();
        chart.setGoal(goal);
        chart.setName(name);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setUserId(loginUser.getId());
        chart.setAiChannel(aiChannel);
        //0等待，其实可以不写，因为default  chart.setChartState(0);
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");

        //异步
        //线程池满了会抛异常 -- 已解决，目前先忽视掉了
        CompletableFuture.runAsync( ()->{
            Chart updateChart = new Chart();
            updateChart.setId(chart.getId());
            // 把任务状态改为执行中 1
            updateChart.setChartState(ChartConstant.RUNNING);
            boolean b = chartService.updateById(updateChart);
            // 如果提交失败(一般情况下,更新失败可能意味着你的数据库出问题了)
            if (!b) {
                handleChartUpdateError(chart.getId(), "更新图表执行中状态失败");
                return;
            }
            String result = null;
            if(aiChannel.equals(ChartConstant.YU_CONG_MING)){
                result = aiManagerYu.doChat(MODELID, userInput.toString());
            }else if(aiChannel.equals(ChartConstant.XUN_FEI)){
//                result = aiManagerXunFei.doChat(userInput.toString());
                result = aiManagerXunFei.doChat(userInput.toString());
            }
            //剪切【【【【【
            String[] split = result.split(ChartConstant.AI_SPLIT_STR);
            if (split.length < ChartConstant.CHART_SPLIT_LENGTH) {
                handleChartUpdateError(chart.getId(), "AI 生成错误");
            }
            String genChart = split[1].trim();
            String genResult = split[2].trim();

            // 调用AI得到结果之后,再更新一次
            Chart updateChartResult = new Chart();
            updateChartResult.setId(chart.getId());
            updateChartResult.setGenChart(genChart);
            updateChartResult.setGenResult(genResult);
            updateChartResult.setChartState(ChartConstant.SUCCEED);
            boolean updateResult = chartService.updateById(updateChartResult);
            if (!updateResult) {
                handleChartUpdateError(chart.getId(), "更新图表成功状态时失败");
            }
        },threadPoolExecutor);

        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());
        return ResultUtils.success(biResponse);
    }


    /**
     * 智能分析--异步版但是消息队列！！
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen_async/mq")
    public BaseResponse<BiResponse> genChartByAiAsyncMq(@RequestPart("file") MultipartFile multipartFile,
                                                      GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        // 通过response对象拿到用户id(必须登录才能使用)
        User loginUser = userService.getLoginUser(request);
        redisLimiterManager.doRateLimit(ChartConstant.GEN_CHART_LIMIT_KEY + loginUser.getId());

        String chartType = genChartByAiRequest.getChartType();
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        Integer aiChannel = genChartByAiRequest.getAiChannel();
        // 校验
        //ai channel
        ThrowUtils.throwIf(!aiChannel.equals(ChartConstant.YU_CONG_MING)&&!aiChannel.equals(ChartConstant.XUN_FEI)
                ,ErrorCode.PARAMS_ERROR, "此AI 线路不存在");
        // 如果分析目标为空，就抛出请求参数错误异常，并给出提示
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        // 如果名称不为空，并且名称长度大于100，就抛出异常，并给出提示
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");

        /*
        校检文件：大小，后缀
        利用FileUtil工具类中的getSuffix方法获取文件后缀名(例如:aaa.png,suffix应该保存为png)
         */
        long fileSize = multipartFile.getSize();
        ThrowUtils.throwIf(fileSize > FileConstant.ONE_MB, ErrorCode.PARAMS_ERROR, "文件过大，超过 1M");
        String suffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        ThrowUtils.throwIf(!validFileSuffixList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀不合法");


        //组织语句....
        StringBuffer userInput = new StringBuffer();
        userInput.append("分析需求：");

        // 拼接分析目标
        String userGoal = goal;
        // 如果图表类型不为空
        if (StringUtils.isNotBlank(chartType)) {
            // 就将分析目标拼接上“请使用”+图表类型
            userGoal += "，请使用" + chartType;
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");

        // 压缩后的数据（把multipartFile传进来）
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(csvData).append("\n");

        // 先把图表保存到数据库中！！！
        Chart chart = new Chart();
        chart.setGoal(goal);
        chart.setName(name);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setAiChannel(aiChannel);
        chart.setUserId(loginUser.getId());
        //0等待，其实可以不写，因为default
        //        chart.setChartState(0);
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");
        Long newChartId = chart.getId();

        //mq
        biMessageProducer.sendMessage(String.valueOf(newChartId));

        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(newChartId);

        return ResultUtils.success(biResponse);
    }


    /**
     * 上面的接口很多用到异常,直接定义一个工具类
     */
    private void handleChartUpdateError(long chartId, String execMessage) {
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setChartState(ChartConstant.FAILED);
        updateChartResult.setExecMessage(execMessage);
        boolean updateResult = chartService.updateById(updateChartResult);
        System.err.println("【【【【更新图表失败状态失败");
        if (!updateResult) {
            log.error("更新图表失败状态失败" + chartId + "," + execMessage);
        }
    }
    //endregion

    @GetMapping("/reload/gen")
    public BaseResponse<Boolean> reloadChartByAi(long chartId, HttpServletRequest request) {
        return ResultUtils.success(chartService.reloadChartByAi(chartId, request));
    }
}
