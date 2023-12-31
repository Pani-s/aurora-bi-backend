package com.pani.bi.model.dto.chart;

import lombok.Data;

import java.io.Serializable;

/**
 * 文件上传请求
 *
 * @author pani
 */
@Data
public class GenChartByAiRequest implements Serializable {

    /**
     * 分析目标
     */
    private String goal;

    /**
     * 图表名称
     */
    private String name;

    /**
     * 图表类型
     */
    private String chartType;

    /**
     * 0讯飞星火1鱼聪明
     */
    private Integer aiChannel;

    private static final long serialVersionUID = 1L;
}