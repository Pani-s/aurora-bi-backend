package com.pani.bi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pani.bi.mapper.ChartMapper;
import com.pani.bi.model.entity.Chart;
import com.pani.bi.service.ChartService;
import org.springframework.stereotype.Service;

/**
* @author Pani
* @description 针对表【chart(图表信息表)】的数据库操作Service实现
* @createDate 2023-11-17 15:13:42
*/
@Service
public class ChartServiceImpl extends ServiceImpl<ChartMapper, Chart>
    implements ChartService{

}




