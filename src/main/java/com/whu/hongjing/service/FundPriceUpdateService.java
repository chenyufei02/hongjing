package com.whu.hongjing.service;

public interface FundPriceUpdateService {
    /**
     * 执行更新所有基金最新净值的操作
     * @return 成功更新了净值的基金信息列表
     */
    int updateAllFundPrices();
}