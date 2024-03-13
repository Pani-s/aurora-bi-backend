package com.pani.bi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pani.bi.bizmq.BiMessageProducer;
import com.pani.bi.common.ErrorCode;
import com.pani.bi.constant.ChartConstant;
import com.pani.bi.exception.ThrowUtils;
import com.pani.bi.manager.RedisLimiterManager;
import com.pani.bi.mapper.ChartMapper;
import com.pani.bi.model.entity.Chart;
import com.pani.bi.model.entity.User;
import com.pani.bi.service.ChartService;
import com.pani.bi.service.UserService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
* @author Pani
* @description 针对表【chart(图表信息表)】的数据库操作Service实现
* @createDate 2023-11-17 15:13:42
*/
@Service
public class ChartServiceImpl extends ServiceImpl<ChartMapper, Chart>
    implements ChartService{

    @Resource
    RedisLimiterManager redisLimiterManager;

    @Resource
    UserService userService;

    @Resource
    private BiMessageProducer biMessageProducer;


    @Override
    public boolean reloadChartByAi(long chartId, HttpServletRequest request) {
        ThrowUtils.throwIf(chartId < 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        final String key = ChartConstant.GEN_CHART_LIMIT_KEY + loginUser.getId();
        // 限流判断
        redisLimiterManager.doRateLimit(key);
        //发送消息
        biMessageProducer.sendMessage(String.valueOf(chartId));
        return true;

    }
}




