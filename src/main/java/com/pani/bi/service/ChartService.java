package com.pani.bi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pani.bi.model.entity.Chart;

import javax.servlet.http.HttpServletRequest;

/**
* @author Pani
* @description 针对表【chart(图表信息表)】的数据库操作Service
* @createDate 2023-11-17 15:13:42
*/
public interface ChartService extends IService<Chart> {
    /**
     * 手动重试 AI 生成图表
     *
     * @param chartId 图表id
     * @param request
     * @return boolean
     */
    boolean reloadChartByAi(long chartId, HttpServletRequest request);

}
