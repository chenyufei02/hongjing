package com.whu.hongjing.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.whu.hongjing.pojo.entity.CustomerHolding;
import java.util.List;

public interface CustomerHoldingService extends IService<CustomerHolding> {

    // 我们定义一个接口，用于根据客户ID查询其所有持仓
    List<CustomerHolding> listByCustomerId(Long customerId);

    // 新增：为单个客户重新计算并更新所有持仓的方法
    boolean recalculateAndSaveHoldings(Long customerId);
}