package com.pani.bi.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pani.bi.model.bo.SaveChartResponse;
import com.pani.bi.model.dto.chart.ChartEditRequest;
import com.pani.bi.model.dto.chart.ChartQueryRequest;
import com.pani.bi.model.dto.chart.GenChartByAiRequest;
import com.pani.bi.model.entity.Chart;
import com.pani.bi.model.entity.User;
import com.pani.bi.model.vo.BiResponse;
import com.pani.bi.model.vo.ChartVO;
import com.pani.bi.model.vo.ChartVOCsv;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

/**
* @author Pani
* @description 针对表【chart(图表信息表)】的数据库操作Service
* @createDate 2023-11-17 15:13:42
*/
public interface ChartService extends IService<Chart> {
    /**
     * 获取查询包装类
     *
     * @param chartQueryRequest
     * @return
     */
    QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest);

    /**
     * 编辑图表信息 with csv
     * @param chartEditRequest
     * @param request
     * @return
     */
    boolean editChartWithCsvData(ChartEditRequest chartEditRequest, HttpServletRequest request);

    /**
     * 校验文件合法性
     * @param multipartFile
     * @return
     */
    boolean isValidExcel(MultipartFile multipartFile);

    /**
     * 生成用户给AI的输入
     */
    String buildUserInput(GenChartByAiRequest genChartByAiRequest,String csvData);

    /**
     * 异步 --先存chart信息 因为线程池和mq这一步通用我就抽出来了
     * @param multipartFile
     * @param genChartByAiRequest
     * @param loginUser
     * @return 存入的chart
     */
    SaveChartResponse saveChartByAiAsync(MultipartFile multipartFile,
                                         GenChartByAiRequest genChartByAiRequest, User loginUser);

    /**
     * 智能分析-- 异步 消息队列
     * @param multipartFile
     * @param genChartByAiRequest
     * @param loginUser
     * @return
     */
    BiResponse genChartByAiAsyncMq(MultipartFile multipartFile,
                                   GenChartByAiRequest genChartByAiRequest, User loginUser);

    /**
     * 智能分析-- 异步 线程池
     * @param multipartFile
     * @param genChartByAiRequest
     * @param loginUser
     * @return
     */
    BiResponse genChartByAiAsync(MultipartFile multipartFile,
                                 GenChartByAiRequest genChartByAiRequest, User loginUser);

    /**
     * 智能分析 --同步
     * @param multipartFile
     * @param genChartByAiRequest
     * @param loginUser
     * @return
     */
    BiResponse genChartByAi(MultipartFile multipartFile,
                                 GenChartByAiRequest genChartByAiRequest, User loginUser);

    /**
     * 手动重试 AI 生成图表
     *
     * @param chartId 图表id
     * @param request
     * @return boolean
     */
    boolean reloadChartByAi(long chartId, HttpServletRequest request);

    /**
     * 方法中很多用到异常,直接定义一个工具类
     * @param chartId
     * @param execMessage
     */
    void handleChartUpdateError(long chartId, String execMessage);

    /**
     * 装载生成结果
     * @param chartPage
     * @return
     */
    Page<ChartVO> getChartVOPage(Page<Chart> chartPage);

    /**
     * chart vo
     * @param chart
     * @return
     */
    ChartVO getChartVO(Chart chart);

    ChartVOCsv getChartVOCsv(Chart chart);
}
