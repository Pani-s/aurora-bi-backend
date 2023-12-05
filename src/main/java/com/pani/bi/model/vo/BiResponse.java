package com.pani.bi.model.vo;

import lombok.Data;

/**
 * @author Pani
 * @date Created in 2023/11/19 14:15
 * @description
 */
@Data
public class BiResponse {
    /**
     * 生成结果
     */
    private String genResult;
    /**
     * 生成的图表的js代码
     */
    private String genChart;
    /**
     * 图表id
     */
    private Long chartId;
}
