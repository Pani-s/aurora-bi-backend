package com.pani.bi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pani.bi.model.entity.ChartGenResult;
import com.pani.bi.service.ChartGenResultService;
import com.pani.bi.mapper.ChartGenResultMapper;
import org.springframework.stereotype.Service;

/**
* @author Pani
* @description 针对表【chart_gen_result(生成的图标可视化数据和结论)】的数据库操作Service实现
* @createDate 2024-03-14 08:59:34
*/
@Service
public class ChartGenResultServiceImpl extends ServiceImpl<ChartGenResultMapper, ChartGenResult>
    implements ChartGenResultService{

}




