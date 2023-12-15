package com.pani.bi.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 图表信息表
 * @author Pani
 * @TableName chart
 */
@TableName(value ="chart")
@Data
public class Chart implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
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
    private String chartData;

    /**
     * 图表类型
     */
    private String chartType;

    /**
     * 生成的图表数据
     */
    private String genChart;

    /**
     * 生成的分析结论
     */
    private String genResult;

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

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    /**
     *执行信息
     */
    private String execMessage;

    /**
     *生成状态：0:等待  1:运行中  2:失败  3:成功'
     */
    private Integer chartState;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    public Chart() {}

    public Chart(long id, Integer chartState, String execMessage) {
        this.id = id;
        this.chartState = chartState;
        this.execMessage = execMessage;
    }
    public Chart(long id, Integer chartState) {
        this.id = id;
        this.chartState = chartState;
    }
}