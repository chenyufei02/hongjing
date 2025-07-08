package com.whu.hongjing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.whu.hongjing.pojo.entity.CustomerHolding;
import com.whu.hongjing.pojo.entity.FundTransaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DeadlockLoserDataAccessException; // 【1. 新增】导入死锁异常类
import org.springframework.retry.annotation.Backoff; // 【1. 新增】导入Backoff
import org.springframework.retry.annotation.Retryable; // 【1. 新增】导入Retryable
import java.util.List;

/**
 * 这是一个专门用于并发写入模拟数据的辅助服务。
 * 它的核心作用是提供一个带@Transactional注解的方法，让每个并发线程都能在自己的独立事务中完成数据库操作。
 */
@Service
public class MockDataWriterService {

    @Autowired
    private FundTransactionService fundTransactionService;
    @Autowired
    private CustomerHoldingService customerHoldingService;

    /**
     * 【终极改造】并发写入的核心事务方法
     * 1. @Transactional: 保证每个客户的数据写入是原子性的。
     * 2. @Retryable: 赋予此方法自动重试的能力。
     * - value: 指定只有在发生“死锁”这类异常时，才进行重试。
     * - maxAttempts: 最多尝试3次（第一次失败后，再重试2次）。
     * - backoff: 指定重试的退避策略。
     * - delay: 第一次重试前，延迟50毫秒。
     * - multiplier: 后续每次重试的延迟时间，是前一次的2倍（50ms, 100ms）。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Retryable(
        value = { DeadlockLoserDataAccessException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 50, multiplier = 2)
    )
    public void saveCustomerDataInTransaction(Long customerId, List<FundTransaction> transactions, List<CustomerHolding> holdings) {
        // 因为是并发写入，我们不能再全局清空表，而是只处理当前客户的数据
        if (!transactions.isEmpty()) {
            fundTransactionService.saveBatch(transactions);
        }
        if (!holdings.isEmpty()) {
            // 先删除这个客户旧的持仓，再插入新的
            customerHoldingService.remove(new QueryWrapper<CustomerHolding>().eq("customer_id", customerId));
            customerHoldingService.saveBatch(holdings);
        }
    }
}