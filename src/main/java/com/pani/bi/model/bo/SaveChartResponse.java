package com.pani.bi.model.bo;

import lombok.Data;

/**
 * @author Pani
 * @date Created in 2024/3/14 10:20
 * @description BO（Business Object）：业务对象，把业务逻辑封装为一个对象，这个对象可以包括一个或多个其它的对象。
 */
@Data
public class SaveChartResponse {
    private Long chartId;

    private Integer aiChannel;

    private String csvData;
}
