package com.pani.bi.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * @author Pani
 * @date Created in 2024/3/19 19:30
 * @description with csv 但是没有生成结果
 */
@Data
public class ChartVOCsv {
    /**
     * id
     */
    private Long id;

    /**
     * 图表名称
     */
    private String goal;

    /**
     * 分析目标
     */
    private String name;

    /**
     * 图表数据
     */
    private String chartCsvData;

    /**
     * 图表类型
     */
    private String chartType;


    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 0讯飞星火1鱼聪明
     */
    private Integer aiChannel;

    /**
     * 创建时间
     */
    @JsonFormat(locale = "zh", timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * 更新时间
     */
    @JsonFormat(locale = "zh", timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;


    private static final long serialVersionUID = 1L;
}
