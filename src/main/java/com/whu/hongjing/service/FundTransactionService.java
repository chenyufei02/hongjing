package com.whu.hongjing.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.whu.hongjing.pojo.dto.FundPurchaseDTO;
import com.whu.hongjing.pojo.dto.FundRedeemDTO;
import com.whu.hongjing.pojo.entity.FundTransaction;
import com.whu.hongjing.pojo.vo.FundTransactionVO;

public interface FundTransactionService extends IService<FundTransaction> {
    // 创建一个新方法，它不仅保存交易，还负责触发持仓更新
    FundTransaction createPurchaseTransaction(FundPurchaseDTO dto);

    FundTransaction createRedeemTransaction(FundRedeemDTO dto);

    /**
     * 根据条件分页查询交易流水
     * @param page 分页对象
     * @param customerName 客户姓名 (模糊查询)
     * @param fundCode 基金代码 (模糊查询)
     * @param transactionType 交易类型 (精确查询)
     * @return 包含交易流水视图对象(VO)的分页结果
     */
    Page<FundTransactionVO> getTransactionPage(
            Page<FundTransactionVO> page, String customerName,
            String fundCode, String transactionType, String sortField,
            String sortOrder);


}