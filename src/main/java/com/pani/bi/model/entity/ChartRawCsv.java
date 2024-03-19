package com.pani.bi.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import lombok.Data;

/**
 * 用户表单数据csv格式的原数据
 * @author Pani
 * @TableName chart_raw_csv
 */
@TableName(value ="chart_raw_csv")
@Data
public class ChartRawCsv implements Serializable {
    /**
     * chart图表id
     */
    @TableId
    private Long chartId;

    /**
     * csv格式的数据
     */
    private String csvData;

    /**
     * 是否删除（考虑到之后可能可以让用户修改原数据）
     */
    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}