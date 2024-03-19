package com.pani.bi.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 生成的图标可视化数据和结论
 * @author Pani
 * @TableName chart_gen_result
 */
@TableName(value ="chart_gen_result")
@Data
public class ChartGenResult implements Serializable {
    /**
     * 
     */
    @TableId
    private Long chartId;

    /**
     * 生成的图表可视化数据
     */
    private String genChart;

    /**
     * 生成的分析结果
     */
    private String genResult;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}