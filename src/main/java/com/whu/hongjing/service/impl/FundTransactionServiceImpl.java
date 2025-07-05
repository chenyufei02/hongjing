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
import com.whu.hongjing.pojo.entity.FundInfo;
import com.whu.hongjing.service.FundInfoService;
import com.whu.hongjing.service.FundDataService;
import com.whu.hongjing.service.FundTransactionService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 基金交易服务实现类
 * 负责处理申购和赎回的核心业务逻辑
 */
@Service
public class FundTransactionServiceImpl extends ServiceImpl<FundTransactionMapper, FundTransaction> implements FundTransactionService {

    @Autowired
    private FundInfoService fundInfoService;

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
        // 1. 从我们自己的数据库中，获取该基金最新的、可靠的净值
        // TODO 这里是昨日的收盘净值 后续可优化为消息队列在下午收盘更新完持仓以后再发生真实交易
        FundInfo fundInfo = fundInfoService.getById(dto.getFundCode());
        Assert.notNull(fundInfo, "找不到对应的基金信息：" + dto.getFundCode());
        Assert.notNull(fundInfo.getNetValue(), "该基金暂无有效的净值信息，无法交易。");
        BigDecimal sharePrice = fundInfo.getNetValue();

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

        // 2. 从我们自己的数据库中，获取该基金最新的、可靠的净值
        FundInfo fundInfo = fundInfoService.getById(dto.getFundCode());
        Assert.notNull(fundInfo, "找不到对应的基金信息：" + dto.getFundCode());
        Assert.notNull(fundInfo.getNetValue(), "该基金暂无有效的净值信息，无法交易。");
        BigDecimal sharePrice = fundInfo.getNetValue();

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



    // 这里两个是为了只根据几个简单的信息快速生成交易数据使用的 避免每次生成交易数据都需要接收完整的DTO
    @Override
    public FundTransaction purchase(Long customerId, String fundCode, BigDecimal amount) {
        // 复用我们之前写好的、更专业的逻辑
        FundPurchaseDTO dto = new FundPurchaseDTO();
        dto.setCustomerId(customerId);
        dto.setFundCode(fundCode);
        dto.setTransactionAmount(amount);
        dto.setTransactionTime(LocalDateTime.now());
        return this.createPurchaseTransaction(dto);
    }

    @Override
    public FundTransaction redeem(Long customerId, String fundCode, BigDecimal shares) {
        // 复用我们之前写好的、更专业的逻辑
        FundRedeemDTO dto = new FundRedeemDTO();
        dto.setCustomerId(customerId);
        dto.setFundCode(fundCode);
        dto.setTransactionShares(shares);
        dto.setTransactionTime(LocalDateTime.now());
        return this.createRedeemTransaction(dto);
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