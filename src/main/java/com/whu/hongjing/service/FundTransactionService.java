package com.whu.hongjing.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.whu.hongjing.pojo.dto.FundPurchaseDTO;
import com.whu.hongjing.pojo.dto.FundRedeemDTO;
//import com.whu.hongjing.pojo.dto.FundTransactionDTO;
import com.whu.hongjing.pojo.entity.FundTransaction;

public interface FundTransactionService extends IService<FundTransaction> {
//    // 创建一个新方法，它不仅保存交易，还负责触发持仓更新
//    FundTransaction createTransactionAndUpdateHolding(FundTransactionDTO dto);
    FundTransaction createPurchaseTransaction(FundPurchaseDTO dto);

    FundTransaction createRedeemTransaction(FundRedeemDTO dto);
}