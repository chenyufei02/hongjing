package com.whu.hongjing.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.whu.hongjing.pojo.entity.CustomerHolding;
import com.whu.hongjing.pojo.entity.FundTransaction;
import java.util.List;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.hongjing.pojo.vo.CustomerHoldingVO;


public interface CustomerHoldingService extends IService<CustomerHolding> {

    // 我们定义一个接口，用于根据客户ID查询其所有持仓
    List<CustomerHolding> listByCustomerId(Long customerId);

    // 新增：为单个客户重新计算并更新所有持仓的方法
    boolean recalculateAndSaveHoldings(Long customerId);

    // 新增：处理一笔新交易并更新持仓
    void updateHoldingAfterNewTransaction(FundTransaction transaction);

    /**
     *  根据条件分页查询持仓信息
     * @param page 分页对象
     * @param customerName 客户姓名 (模糊查询)
     * @param fundCode 基金代码 (模糊查询)
     * @return 包含持仓视图对象(VO)的分页结果
     */
    Page<CustomerHoldingVO> getHoldingPage(
            Page<CustomerHoldingVO> page, String customerName,
            String fundCode, String sortField, String sortOrder);


}