package com.pani.bi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.opencsv.CSVReader;
import com.pani.bi.model.entity.ChartRawCsv;
import com.pani.bi.service.ChartRawCsvService;
import com.pani.bi.mapper.ChartRawCsvMapper;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.util.List;

/**
* @author Pani
* @description 针对表【chart_raw_csv(用户表单数据csv格式的原数据)】的数据库操作Service实现
* @createDate 2024-03-14 08:59:34
*/
@Service
public class ChartRawCsvServiceImpl extends ServiceImpl<ChartRawCsvMapper, ChartRawCsv>
    implements ChartRawCsvService{

    @Override
    public boolean isValidCsv(String csv) {
        //opencsv实现
        try (CSVReader reader = new CSVReader(new StringReader(csv))) {
            // 尝试读取CSV数据
            List<String[]> allRows = reader.readAll();
            // 如果读取成功，没有抛出异常，则认为是合法的CSV格式
            return true;
        } catch (Exception e) {
            // 如果读取过程中发生异常，则认为不是合法的CSV格式
            return false;
        }
    }

    /**
     * 正则表达式实现
     * @param input
     * @return
     */
    public static boolean isValidCsvByReg(String input) {
        // 正则表达式解释：
        // ^ 表示字符串的开始
        // ([^\r\n]+(?:\r\n|[\n\r]))+ 表示一行或多行数据，每行由非换行符字符组成，后面跟着一个换行符
        // (?:,\s*|$) 表示每行数据后面跟着一个逗号和任意数量的空格，或者直接是字符串的结束
        // $ 表示字符串的结束
        String csvPattern = "^[^\\r\\n]+(?:\\r\\n|\\n\\r|\\n|\\r)(?:,[^\\r\\n]*(?:\\r\\n|\\n\\r|\\n|\\r))*$";
        return input != null && input.matches(csvPattern);
    }
}




