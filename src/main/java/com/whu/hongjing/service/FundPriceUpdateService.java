package com.whu.hongjing.service;

import com.whu.hongjing.pojo.entity.FundInfo; // 导入
import java.util.List; // 导入

public interface FundPriceUpdateService {
    /**
     * 执行更新所有基金最新净值的操作
     * @return 成功更新了净值的基金信息列表
     */
    int updateAllFundPrices();
}