package com.pani.bi.utils;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.pani.bi.common.ErrorCode;
import com.pani.bi.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.ResourceUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * EasyExcel 测试
 *
 * @author pani
 */
//@SpringBootTest
public class EasyExcelTest {
    public static void main(String[] args) throws FileNotFoundException {
        testExcelToCsv();
    }

    @Test
    public void doImport() throws FileNotFoundException {
        File file = ResourceUtils.getFile("classpath:test_excel.xlsx");
        List<Map<Integer, String>> list = EasyExcel.read(file)
                .excelType(ExcelTypeEnum.XLSX)
                .sheet()
                .headRowNumber(0)
                .doReadSync();
        System.out.println(list);
    }

    @Test
    static String testExcelToCsv() throws FileNotFoundException {
        File file = ResourceUtils.getFile("classpath:test_excel3.xlsx");
        List<Map<Integer, String>> list = null;
        list = EasyExcel.read(file)
                .excelType(ExcelTypeEnum.XLSX)
                .sheet()
                .headRowNumber(0)
                .doReadSync();
        //        System.out.println(list);
        if (CollUtil.isEmpty(list)) {
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder();
        LinkedHashMap<Integer, String> headMap = (LinkedHashMap) list.get(0);
        List<String> headerList = headMap.values().stream().filter(ObjectUtil::isNotEmpty).
                collect(Collectors.toList());
        stringBuilder.append(StringUtils.join(headerList, ",")).append('\n');
        for (int i = 1; i < list.size(); i++) {
            LinkedHashMap<Integer, String> map = (LinkedHashMap) list.get(i);
            List<String> stringList = map.values().stream().filter(ObjectUtil::isNotEmpty).
                    collect(Collectors.toList());
            stringBuilder.append(StringUtils.join(stringList, ",")).append('\n');
        }
        String string = stringBuilder.toString();
        System.out.println(string);
        return string;
    }

}