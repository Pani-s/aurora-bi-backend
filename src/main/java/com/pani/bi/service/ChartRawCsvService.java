package com.pani.bi.service;

import com.pani.bi.model.entity.ChartRawCsv;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author Pani
* @description 针对表【chart_raw_csv(用户表单数据csv格式的原数据)】的数据库操作Service
* @createDate 2024-03-14 08:59:34
*/
public interface ChartRawCsvService extends IService<ChartRawCsv> {
    /**
     * 是不是合法的csv文件
     * @param csv
     * @return
     */
    boolean isValidCsv(String csv);
}
