package com.pani.bi.bizmq;

/**
 * @author Pani
 * @date Created in 2023/11/27 21:23
 * @description
 */
public interface BiMqConstant {
    //region BI 工作
    /**
     * BI 交换机
     */
    String BI_EXCHANGE_NAME = "bi_exchange";

    String BI_QUEUE_NAME = "bi_queue";

    String BI_ROUTING_KEY = "bi_routingKey";
    //endregion

    //region BI 死信
    /**
     * 死信
     */
    String BI_DLX_EXCHANGE_NAME = "bi_dlx_exchange";

    String BI_DLX_QUEUE_NAME = "bi_dlx_queue";

    String BI_DLX_ROUTING_KEY = "bi_dlx_routing_key";
    //endregion


    /**
     * 限制同时生成的图表数量
     */
    int MAX_CONCURRENT_CHARTS = 3;

}
