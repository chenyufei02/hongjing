package com.whu.hongjing.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.whu.hongjing.pojo.entity.CustomerHolding;
import com.whu.hongjing.pojo.entity.FundTransaction;
//import java.math.BigDecimal;
import java.util.List;
//import com.whu.hongjing.pojo.entity.FundInfo;

public interface CustomerHoldingService extends IService<CustomerHolding> {

    // 我们定义一个接口，用于根据客户ID查询其所有持仓
    List<CustomerHolding> listByCustomerId(Long customerId);

    // 新增：为单个客户重新计算并更新所有持仓的方法
    boolean recalculateAndSaveHoldings(Long customerId);

    // 新增：处理一笔新交易并更新持仓
    void updateHoldingAfterNewTransaction(FundTransaction transaction);

    /**
     * 【批量任务】(全量版) 重新计算所有客户持仓的最新市值
     */
    void recalculateAllMarketValues();
}