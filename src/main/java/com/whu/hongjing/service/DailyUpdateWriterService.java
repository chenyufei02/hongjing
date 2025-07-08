package com.whu.hongjing.service;

import com.whu.hongjing.pojo.entity.CustomerHolding;
import com.whu.hongjing.pojo.entity.FundInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 这是一个专门为“每日更新”任务提供并发写入能力的辅助服务。
 * 它的核心作用是提供带有事务和自动重试功能的独立方法，供并发线程调用。
 */
@Service
public class DailyUpdateWriterService {

    @Autowired
    private FundInfoService fundInfoService;

    @Autowired
    private CustomerHoldingService customerHoldingService;

    /**
     * 在独立的、可重试的事务中，批量更新基金净值。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Retryable(
        value = { DeadlockLoserDataAccessException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public void saveUpdatedPricesInTransaction(List<FundInfo> updatedFunds) {
        if (updatedFunds != null && !updatedFunds.isEmpty()) {
            fundInfoService.updateBatchById(updatedFunds);
        }
    }

    /**
     * 在独立的、可重试的事务中，批量更新持仓市值。
     * 注意：这里我们不再为每个客户单独开启事务，而是将计算好的所有持仓更新对象，
     * 在一个总的批量更新事务中完成，因为updateBatchById是按ID更新，冲突概率远小于“删后插”。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Retryable(
        value = { DeadlockLoserDataAccessException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public void saveUpdatedHoldingsInTransaction(List<CustomerHolding> updatedHoldings) {
        if (updatedHoldings != null && !updatedHoldings.isEmpty()) {
            // 分批次更新，避免单次SQL过长
            customerHoldingService.updateBatchById(updatedHoldings, 1000);
        }
    }
}