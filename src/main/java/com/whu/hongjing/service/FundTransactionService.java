package com.whu.hongjing.service;
import java.math.BigDecimal;
import com.baomidou.mybatisplus.extension.service.IService;
import com.whu.hongjing.pojo.dto.FundPurchaseDTO;
import com.whu.hongjing.pojo.dto.FundRedeemDTO;
import com.whu.hongjing.pojo.entity.FundTransaction;

public interface FundTransactionService extends IService<FundTransaction> {
    // 创建一个新方法，它不仅保存交易，还负责触发持仓更新
    FundTransaction createPurchaseTransaction(FundPurchaseDTO dto);

    FundTransaction createRedeemTransaction(FundRedeemDTO dto);


    // 以上是对外发生交易的接口方法  下面是对内部进行交易数据生成的接口方法  真实场景中不需要
    FundTransaction purchase(Long customerId, String fundCode, BigDecimal amount);

    FundTransaction redeem(Long customerId, String fundCode, BigDecimal shares);
}