package com.pani.bi.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pani.bi.annotation.AuthCheck;
import com.pani.bi.annotation.GenChartRateLimit;
import com.pani.bi.bizmq.BiMessageProducer;
import com.pani.bi.common.BaseResponse;
import com.pani.bi.common.DeleteRequest;
import com.pani.bi.common.ErrorCode;
import com.pani.bi.common.ResultUtils;
import com.pani.bi.constant.ChartConstant;
import com.pani.bi.constant.UserConstant;
import com.pani.bi.exception.BusinessException;
import com.pani.bi.exception.ThrowUtils;
import com.pani.bi.manager.AiManagerXunFei;
import com.pani.bi.manager.AiManagerYu;
import com.pani.bi.manager.RedisLimiterManager;
import com.pani.bi.model.dto.chart.*;
import com.pani.bi.model.entity.Chart;
import com.pani.bi.model.entity.User;
import com.pani.bi.model.vo.BiResponse;
import com.pani.bi.model.vo.ChartVO;
import com.pani.bi.model.vo.ChartVOCsv;
import com.pani.bi.service.ChartService;
import com.pani.bi.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

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

//    @Resource
//    private RedisLimiterManager redisLimiterManager;

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @Resource
    private BiMessageProducer biMessageProducer;



    /**
     * 定义合法的后缀列表
     */
    final List<String> validFileSuffixList = Arrays.asList("xls", "xlsx");

    // region 增删改查

//    /**
//     * 创建（暂时没用）
//     *
//     * @param chartAddRequest
//     * @param request
//     * @return
//     *//*
//    @PostMapping("/add")
/*    public BaseResponse<Long> addChart(@RequestBody ChartAddRequest chartAddRequest, HttpServletRequest request) {
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
    }*/

    /**
     * 删除（仅管理员和自己）
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
     * 更新（仅管理员）【目前没用】
     *
     * @param chartUpdateRequest
     * @return
     */
//    @PostMapping("/update")
//    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
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
     * 根据 id 获取 只能本人- -
     *
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<ChartVOCsv> getChartById(@RequestParam("id") long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = chartService.getById(id);
        if (chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        //验证是否是本人
        if(!chart.getUserId().equals(loginUser.getId())){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"非本人图表信息不可查看！");
        }
        return ResultUtils.success(chartService.getChartVOCsv(chart));
    }

    /**
     * 分页获取列表（封装类） 管理员用
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<ChartVO>> listChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
                                                     HttpServletRequest request) {
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                chartService.getQueryWrapper(chartQueryRequest));
//        return ResultUtils.success(chartPage);
        return ResultUtils.success(chartService.getChartVOPage(chartPage));

    }

    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<ChartVO>> listMyChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
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
                chartService.getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartService.getChartVOPage(chartPage));

    }


    /**
     * 编辑（仅本人用户）【仅修改标题 目标】
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
        User loginUser = userService.getLoginUser(request);
        long id = chartEditRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR,"图表不存在");
        // 仅本人或管理员可编辑
        if (!oldChart.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"无权限");
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartEditRequest, chart);
        boolean result = chartService.updateById(chart);
        if(!result){
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"修改信息失败");
        }
        return ResultUtils.success(result);
    }

    /**
     * 编辑（仅本人用户）【还可以修改原表格csv格式的信息】
     * 按理说，需要AI重新生成的，不过因为限流什么的，我认为还是让用户单独额外点击按钮进行AI分析
     *
     * @param chartEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit/data")
    public BaseResponse<Boolean> editChartWithData(@RequestBody ChartEditRequest chartEditRequest, HttpServletRequest request) {
        if (chartEditRequest == null || chartEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean b = chartService.editChartWithCsvData(chartEditRequest, request);
        return ResultUtils.success(b);
    }

    // endregion


    // region AI智能分析（大头）

    /**
     * 智能分析--同步版
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @GenChartRateLimit
    @PostMapping("/gen")
    public BaseResponse<BiResponse> genChartByAi(@RequestPart("file") MultipartFile multipartFile,
                                                 GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        // 通过response对象拿到用户id(必须登录才能使用)
        User loginUser = userService.getLoginUser(request);
        //限流
//        redisLimiterManager.doRateLimit(ChartConstant.GEN_CHART_LIMIT_KEY + loginUser.getId());

        BiResponse biResponse = chartService.genChartByAi(multipartFile, genChartByAiRequest, loginUser);

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
    @GenChartRateLimit
    @PostMapping("/gen_async")
    public BaseResponse<BiResponse> genChartByAiAsync(@RequestPart("file") MultipartFile multipartFile,
                                                      GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        // 通过response对象拿到用户id(必须登录才能使用)
        User loginUser = userService.getLoginUser(request);
        //key设为一个 static final变量 genChartByAi_
//        redisLimiterManager.doRateLimit(ChartConstant.GEN_CHART_LIMIT_KEY + loginUser.getId());

        return ResultUtils.success(chartService.genChartByAiAsync(multipartFile,
                genChartByAiRequest,loginUser));
    }


    /**
     * 智能分析--异步版但是消息队列！！
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @GenChartRateLimit
    @PostMapping("/gen_async/mq")
    public BaseResponse<BiResponse> genChartByAiAsyncMq(@RequestPart("file") MultipartFile multipartFile,
                                                      GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        // 通过response对象拿到用户id(必须登录才能使用)
        User loginUser = userService.getLoginUser(request);
//        redisLimiterManager.doRateLimit(ChartConstant.GEN_CHART_LIMIT_KEY + loginUser.getId());

        return ResultUtils.success(chartService.genChartByAiAsyncMq(multipartFile,
                genChartByAiRequest,loginUser));
    }

    @GenChartRateLimit
    @PostMapping("/gen/reload")
    public BaseResponse<Boolean> reloadChartByAi(long chartId, HttpServletRequest request) {
        log.info("重新生成图表分析");
        return ResultUtils.success(chartService.reloadChartByAi(chartId, request));
    }
    //endregion


}
