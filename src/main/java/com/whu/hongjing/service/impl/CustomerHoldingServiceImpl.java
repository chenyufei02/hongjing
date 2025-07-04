package com.whu.hongjing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whu.hongjing.mapper.CustomerHoldingMapper;
import com.whu.hongjing.pojo.entity.CustomerHolding;
import com.whu.hongjing.pojo.entity.FundTransaction;
import com.whu.hongjing.service.CustomerHoldingService;
import com.whu.hongjing.service.FundTransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CustomerHoldingServiceImpl extends ServiceImpl<CustomerHoldingMapper, CustomerHolding> implements CustomerHoldingService {

    @Autowired
    private FundTransactionService fundTransactionService;

    /**
     * 根据ID查询持仓情况
     * @param customerId
     * @return java.util.List<com.whu.hongjing.pojo.entity.CustomerHolding>
     * @author yufei
     * @since 2025/7/4
     */
    @Override
    public List<CustomerHolding> listByCustomerId(Long customerId) {
        QueryWrapper<CustomerHolding> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("customer_id", customerId);
        return this.list(queryWrapper);
    }

    /**
     * 手动更新用户持仓情况的方法，用于修正持仓表，因为有可能有在自动更新持仓功能之前就已经存进来的数据
     * @param customerId
     * @return boolean
     * @author yufei
     * @since 2025/7/3
     */
    @Override
    @Transactional // 开启事务，保证数据一致性
    public boolean recalculateAndSaveHoldings(Long customerId) {
        // 1. 获取该客户的所有交易记录
        QueryWrapper<FundTransaction> txQuery = new QueryWrapper<>();
        txQuery.eq("customer_id", customerId).orderByAsc("transaction_time");
        List<FundTransaction> transactions = fundTransactionService.list(txQuery);

        if (transactions.isEmpty()) {
            // 如果客户没有任何交易记录，也应确保他没有任何持仓记录
            QueryWrapper<CustomerHolding> holdingDeleteQuery = new QueryWrapper<>();
            holdingDeleteQuery.eq("customer_id", customerId);
            this.remove(holdingDeleteQuery);
            return true; // 没有交易记录，无需计算
        }

        // 2. 按fund_code对交易进行分组，map的key为同一支基金的code，value为同一只基金的交易记录列表
        Map<String, List<FundTransaction>> transactionsByFund = transactions.stream()
                .collect(Collectors.groupingBy(FundTransaction::getFundCode));

        // 3. 删除该客户旧的持仓记录，准备重新计算
        QueryWrapper<CustomerHolding> holdingDeleteQuery = new QueryWrapper<>();
        holdingDeleteQuery.eq("customer_id", customerId);
        this.remove(holdingDeleteQuery);

        // 4. 遍历每只基金的交易，计算最终持仓
        for (Map.Entry<String, List<FundTransaction>> entry : transactionsByFund.entrySet()) {
            String fundCode = entry.getKey(); // 取出一支基金的code
            List<FundTransaction> fundTransactions = entry.getValue(); // 取出一支基金的交易记录

            // 对每支取出来的基金重新定义总份额和总成本
            BigDecimal totalShares = BigDecimal.ZERO;  // 总份额
            BigDecimal totalCost = BigDecimal.ZERO;    // 总成本
            BigDecimal currentAverageCost = BigDecimal.ZERO; // 用于计算当前平均买入成本 当赎回的成本

            for (FundTransaction tx : fundTransactions) {
                if ("申购".equals(tx.getTransactionType())) {
                    totalShares = totalShares.add(tx.getTransactionShares());  // 份额加份额
                    totalCost = totalCost.add(tx.getTransactionAmount());      // 成本加成本
                } else if ("赎回".equals(tx.getTransactionType())) {
                    // 赎回时，需要知道现在的平均成本来在总成本里赎回份额。因为已经假设了原有的交易有的没被计入到持仓情况表里来，因此
                    // 赎回时的平均成本也不能直接用持仓表里的averageCost字段，因其漏掉了交易 认为不准，重新根据所有交易的总成本与
                    // 总份额计算当前的最新平均成本
                    if(totalShares.compareTo(BigDecimal.ZERO) > 0) {
                        // 总成本除总份额得到当前买入的平均成本
                        currentAverageCost = totalCost.divide(totalShares, 10, RoundingMode.HALF_UP); // 用更高精度计算当时成本
                    }
                    // 总赎回金额 = 份额×平均成本
                    BigDecimal redeemedCost = tx.getTransactionShares().multiply(currentAverageCost);

                    totalShares = totalShares.subtract(tx.getTransactionShares());
                    totalCost = totalCost.subtract(redeemedCost); // 赎回时，已经花费的总成本按比例减少
                }
            }

            // 如果最终份额大于0，则创建或更新持仓记录
            if (totalShares.compareTo(BigDecimal.ZERO) > 0) {
                CustomerHolding newHolding = new CustomerHolding();
                newHolding.setCustomerId(customerId);
                newHolding.setFundCode(fundCode);
                newHolding.setTotalShares(totalShares);

                // 计算交易处理完之后的平均买入成本（因为上面的currentAverageCost只是在当时计算赎回值的时候临时计算的当前平均成本
                // ,而最后面的交易如果是申购交易的话这个临时平均成本最后就没有在最后通过计算赎回成本时更新，因此不能直接使用。）
                BigDecimal averageCost = totalCost.divide(totalShares, 4, RoundingMode.HALF_UP);
                newHolding.setAverageCost(averageCost);
                newHolding.setLastUpdateDate(LocalDateTime.now());

                // marketValue可以后续通过定时任务或查询时实时计算，这里暂不处理

                this.save(newHolding);
            }
        }
        return true;
    }

    @Override
    @Transactional
    public void updateHoldingAfterNewTransaction(FundTransaction transaction) {
        // 1. 查找该客户对该基金是否已有持仓记录
        QueryWrapper<CustomerHolding> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("customer_id", transaction.getCustomerId())
                    .eq("fund_code", transaction.getFundCode());
        CustomerHolding holding = this.getOne(queryWrapper);

        if (holding == null) { // 如果没有持仓记录，说明是首次购买
            holding = new CustomerHolding();
            holding.setCustomerId(transaction.getCustomerId());
            holding.setFundCode(transaction.getFundCode());
            holding.setTotalShares(BigDecimal.ZERO);
            holding.setAverageCost(BigDecimal.ZERO);
        }

        // 2. 根据交易类型，更新份额和成本
        if ("申购".equals(transaction.getTransactionType())) {
            // 新总份额 = 原份额 + 新交易份额
            BigDecimal newTotalShares = holding.getTotalShares().add(transaction.getTransactionShares());
            // 新总成本 = (原成本*原份额 + 新交易金额)
            BigDecimal newTotalCost = (holding.getAverageCost().multiply(holding.getTotalShares()))
                                        .add(transaction.getTransactionAmount());

            holding.setTotalShares(newTotalShares);
            // 新平均成本 = 新总成本/新总份额
            holding.setAverageCost(newTotalCost.divide(newTotalShares, 4, RoundingMode.HALF_UP));

        } else if ("赎回".equals(transaction.getTransactionType())) {
            // 赎回不影响平均成本，所以不做处理。只对总份额做处理
            BigDecimal newTotalShares = holding.getTotalShares().subtract(transaction.getTransactionShares());
            holding.setTotalShares(newTotalShares);
        }

        holding.setLastUpdateDate(LocalDateTime.now());

        // 3. 保存或更新持仓记录到数据库
        this.saveOrUpdate(holding);
    }
}