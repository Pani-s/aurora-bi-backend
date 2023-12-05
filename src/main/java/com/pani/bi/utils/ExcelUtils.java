package com.pani.bi.utils;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.pani.bi.common.ErrorCode;
import com.pani.bi.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Pani
 * @date Created in 2023/11/18 11:32
 * @description
 */
@Slf4j
public class ExcelUtils {
    public static void main(String[] args) {
        excelToCsv(null);
    }

    public static String excelToCsv(MultipartFile multipartFile) {
        List<Map<Integer, String>> list = null;
        try {
            list = EasyExcel.read(multipartFile.getInputStream())
                    .excelType(ExcelTypeEnum.XLSX)
                    .sheet()
                    .headRowNumber(0)
                    .doReadSync();
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"文件读取错误");
        }
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
        return stringBuilder.toString();
    }
}
