package com.whu.hongjing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whu.hongjing.exception.InsufficientFundsException;
import com.whu.hongjing.mapper.FundTransactionMapper;
import com.whu.hongjing.pojo.dto.FundPurchaseDTO;
import com.whu.hongjing.pojo.dto.FundRedeemDTO;
import com.whu.hongjing.pojo.entity.CustomerHolding;
import com.whu.hongjing.pojo.entity.FundTransaction;
import com.whu.hongjing.service.CustomerHoldingService;
import com.whu.hongjing.service.FundDataService;
import com.whu.hongjing.service.FundTransactionService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 基金交易服务实现类
 * 负责处理申购和赎回的核心业务逻辑
 */
@Service
public class FundTransactionServiceImpl extends ServiceImpl<FundTransactionMapper, FundTransaction> implements FundTransactionService {

    /**
     * 注入模拟的基金数据服务，用于获取基金净值
     */
    @Autowired
    private FundDataService fundDataService;

    /**
     * 使用@Lazy注解懒加载客户持仓服务，以解决循环依赖问题
     */
    @Autowired
    @Lazy
    private CustomerHoldingService customerHoldingService;

    /**
     * 处理基金申购业务
     * @param dto 包含申购信息的DTO对象
     * @return 创建并保存好的交易记录实体
     */
    @Override
    @Transactional
    public FundTransaction createPurchaseTransaction(FundPurchaseDTO dto) {
        // 1. 从外部服务获取基金的最新净值
        BigDecimal sharePrice = fundDataService.getLatestNetValue(dto.getFundCode());

        // 2. 创建交易实体，并从DTO复制基础属性
        FundTransaction transaction = new FundTransaction();
        BeanUtils.copyProperties(dto, transaction);

        // 3. 填充申购业务特有的字段
        transaction.setTransactionType("申购");
        transaction.setSharePrice(sharePrice);

        transaction.setStatus("成功"); // 设置默认状态

        // 4. 根据申购金额和净值，计算出客户能购买到的份额
        BigDecimal shares = dto.getTransactionAmount().divide(sharePrice, 2, RoundingMode.DOWN);
        transaction.setTransactionShares(shares);

        // 5. 保存交易并触发持仓更新
        return saveTransactionAndUpdateHolding(transaction);
    }

    /**
     * 处理基金赎回业务，包含核心的持仓校验逻辑
     * @param dto 包含赎回信息的DTO对象
     * @return 创建并保存好的交易记录实体
     */
    @Override
    @Transactional
    public FundTransaction createRedeemTransaction(FundRedeemDTO dto) {

        // a. 查询客户对该基金的当前持仓
        QueryWrapper<CustomerHolding> holdingQuery = new QueryWrapper<>();
        holdingQuery.eq("customer_id", dto.getCustomerId())
                    .eq("fund_code", dto.getFundCode());
        CustomerHolding currentHolding = customerHoldingService.getOne(holdingQuery);

        // b. 进行校验
        if (currentHolding == null || dto.getTransactionShares().compareTo(currentHolding.getTotalShares()) > 0) {
            // 如果持仓记录不存在，或者要赎回的份额 > 当前持有的总份额
            String availableShares = (currentHolding != null) ? currentHolding.getTotalShares().toPlainString() : "0";
            throw new InsufficientFundsException(
                "赎回失败：份额不足。当前持有 " + availableShares + " 份，尝试赎回 " + dto.getTransactionShares().toPlainString() + " 份。"
            );
        }

        // 2. 从外部服务获取基金的最新净值
        BigDecimal sharePrice = fundDataService.getLatestNetValue(dto.getFundCode());

        // 3. 创建交易实体
        FundTransaction transaction = new FundTransaction();
        BeanUtils.copyProperties(dto, transaction);
        transaction.setTransactionType("赎回");
        transaction.setSharePrice(sharePrice);
        transaction.setStatus("成功");

        // 4. 计算赎回金额
        BigDecimal amount = dto.getTransactionShares().multiply(sharePrice).setScale(2, RoundingMode.HALF_UP);
        transaction.setTransactionAmount(amount);

        // 5. 保存交易并触发持仓更新
        return saveTransactionAndUpdateHolding(transaction);
    }

    /**
     * 私有辅助方法，用于统一处理保存交易和更新持仓的逻辑
     * @param transaction 已经构建好的交易实体
     * @return 保存后的交易实体（包含数据库生成的ID）
     */
    private FundTransaction saveTransactionAndUpdateHolding(FundTransaction transaction) {
        // 步骤1：将交易记录保存到数据库
        this.save(transaction);
        // 步骤2：调用客户持仓服务，根据这笔新交易实时更新持仓信息
        customerHoldingService.updateHoldingAfterNewTransaction(transaction);
        // 步骤3：返回包含ID的完整交易实体
        return transaction;
    }
}