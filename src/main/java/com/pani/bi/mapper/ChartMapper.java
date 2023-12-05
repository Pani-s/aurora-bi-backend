package com.pani.bi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pani.bi.model.entity.Chart;

import java.util.List;
import java.util.Map;

/**
* @author Pani
* @description 针对表【chart(图表信息表)】的数据库操作Mapper
* @createDate 2023-11-17 15:13:42
* @Entity com.pani.bi.model.entity.Chart
*/
public interface ChartMapper extends BaseMapper<Chart> {

    /**
     * 动态的创建数据库
     * @param creatTableSQL
     */
    void createTable(final String creatTableSQL);

    /**
     * 向动态创建的数据库之中插入数据
     *
     * @param insertCVSData
     * @return
     */
    void insertValue(final String insertCVSData);

    /**
     * 查询表格数据
     * @param chartId
     * @return
     */
    List<Map<String,Object>> queryChartData(String chartId);

    /*
    mapkey是这个：
    ====
    //使用带有@Mapkey("id")的Map<Integer,User>接收
    //{1111:{id:1111,name:"foo"},2222:{id:2222,name:"bar"}}
    @Mapkey("id")
    public Map<Integer,User> useMap();
     */
}




