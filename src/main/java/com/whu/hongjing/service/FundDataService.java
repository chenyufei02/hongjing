package com.whu.hongjing.service;

import java.math.BigDecimal;

/**
 * 模拟从外部数据源获取基金数据的服务
 */
public interface FundDataService {

    /**
     * 根据基金代码，获取其最新的净值
     * @param fundCode 基金代码
     * @return 最新的单位净值
     */
    BigDecimal getLatestNetValue(String fundCode);
}