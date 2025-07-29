package com.whu.hongjing.service;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.whu.hongjing.pojo.entity.CustomerHolding;
import com.whu.hongjing.pojo.entity.FundInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 后台定时任务服务
 * V3 最终性能版：采用手动管理的线程池实现“计算并行+写入并行+自动重试”模型。
 */
@Service
public class ScheduledTasksService {

    @Autowired
    private FundInfoService fundInfoService;
    @Autowired
    private CustomerHoldingService customerHoldingService;

    // 注入事务写入服务
    @Autowired
    private DailyUpdateWriterService dailyUpdateWriterService;
    @Autowired
    private TagRefreshService tagRefreshService;

    /**
     * 【任务一】每日下午收盘后，更新基金净值与客户持仓市值。
     * 总调度方法，自身不带事务。负责协调两个并发的更新阶段。
     */
    @Scheduled(cron = "0 00 16 * * ?")
    public void updateNetValueAndMarketValueDaily() {
        System.out.println("【定时任务】开始执行每日净值与市值更新...");

        // --- 第一阶段：并发更新所有基金的净值 ---
        this.updateFundPricesConcurrently();

        // --- 第二阶段：并发更新所有持仓的市值 ---
        this.updateMarketValuesConcurrently();

        System.out.println("【定时任务】每日净值与市值更新任务圆满完成！且已成功更新用户持仓表！");
    }

    /**
     * 阶段一：并发更新基金净值
     */
    private void updateFundPricesConcurrently() {
        List<FundInfo> allFunds = fundInfoService.list();
        if (allFunds.isEmpty()) {
            System.out.println("【定时任务-阶段1】没有基金信息，净值更新跳过。");
            return;
        }

        // 1. 并发计算出所有基金的新净值（这部分计算很快，直接用并行流即可）
        List<FundInfo> updatedFunds = allFunds.parallelStream().map(fund -> {
            Random random = ThreadLocalRandom.current();
            BigDecimal currentNetValue = fund.getNetValue();
            // 如果为空则初始化随机值
            if (currentNetValue == null || currentNetValue.compareTo(BigDecimal.ZERO) == 0) {
                currentNetValue = BigDecimal.valueOf(0.8 + random.nextDouble() * 0.7);
            }
            // 不为空则在一定范围内按比例波动
            double percentageChange = (random.nextDouble() * 0.05) - 0.025;  // 波动幅度为±0.025
            BigDecimal newNetValue = currentNetValue.multiply(BigDecimal.valueOf(1 + percentageChange))
                    .setScale(4, RoundingMode.HALF_UP);

            FundInfo fundForUpdate = new FundInfo();
            fundForUpdate.setFundCode(fund.getFundCode());
            fundForUpdate.setNetValue(newNetValue);
            return fundForUpdate;
        }).collect(Collectors.toList());

        // 2. 将计算结果在一个独立的、可重试的事务中，批量写入数据库
        try {
            dailyUpdateWriterService.saveUpdatedPricesInTransaction(updatedFunds);
            System.out.println("【定时任务-阶段1】成功更新了 " + updatedFunds.size() + " 只基金的最新净值。");
        } catch (Exception e) {
            System.err.println("【定时任务-阶段1】更新基金净值时发生严重错误！");
            e.printStackTrace();
        }
    }

    /**
     * 阶段二：手动创建线程池，并发计算并写入持仓市值
     */
    private void updateMarketValuesConcurrently() {
        List<CustomerHolding> allHoldings = customerHoldingService.list();
        if (allHoldings.isEmpty()) {
            System.out.println("【定时任务-阶段2】没有任何持仓记录，市值更新结束。");
            return;
        }
        Map<String, BigDecimal> latestPrices = fundInfoService.list().stream()
                .filter(f -> f.getNetValue() != null)
                .collect(Collectors.toMap(FundInfo::getFundCode, FundInfo::getNetValue));

        // 用于并发写入的线程池
        int corePoolSize = Runtime.getRuntime().availableProcessors();
        int maximumPoolSize = corePoolSize * 10;
        long keepAliveTime = 60L;
        TimeUnit unit = TimeUnit.SECONDS;
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(allHoldings.size());
        ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("market-value-writer-thread-%d").build();
        RejectedExecutionHandler handler = new ThreadPoolExecutor.CallerRunsPolicy();

        ExecutorService writerExecutor = new ThreadPoolExecutor(
            corePoolSize,
            maximumPoolSize,
            keepAliveTime,
            unit,
            workQueue,
            threadFactory,
            handler
        );

        // 创建一个列表来收集所有写入任务的Future，以便后续检查是否全部成功
        List<Future<Void>> writerFutures = new ArrayList<>();

        // 按客户ID对所有持仓进行分组，为每个客户创建一个写入任务
        Map<Long, List<CustomerHolding>> holdingsByCustomer = allHoldings.stream()
                .collect(Collectors.groupingBy(CustomerHolding::getCustomerId));

        System.out.println("【定时任务-阶段2】开始向写入线程池提交 " + holdingsByCustomer.size() + " 个客户的市值并发更新任务...");

        for (Map.Entry<Long, List<CustomerHolding>> entry : holdingsByCustomer.entrySet()) {
            List<CustomerHolding> customerHoldings = entry.getValue();

            // 为每个客户创建一个独立的、可被调用的写入任务
            Callable<Void> writeTask = () -> {
                // a. 在子线程内部，先计算出这个客户所有需要更新的持仓对象
                List<CustomerHolding> updatedHoldingsForCustomer = customerHoldings.stream().map(holding -> {
                    BigDecimal latestNetValue = latestPrices.get(holding.getFundCode());
                    if (latestNetValue != null && holding.getTotalShares() != null) {
                        BigDecimal newMarketValue = holding.getTotalShares().multiply(latestNetValue)
                                .setScale(2, RoundingMode.HALF_UP);
                        CustomerHolding holdingForUpdate = new CustomerHolding();
                        holdingForUpdate.setId(holding.getId());
                      holdingForUpdate.setMarketValue(newMarketValue);
                        return holdingForUpdate;
                    }
                    return null;
                }).filter(Objects::nonNull).collect(Collectors.toList());

                // b. 调用我们新的、带有@Retryable注解的写入服务来执行数据库操作
                if (!updatedHoldingsForCustomer.isEmpty()) {
                    dailyUpdateWriterService.saveUpdatedHoldingsInTransaction(updatedHoldingsForCustomer);
                }
                return null; // Callable需要一个返回值，成功时返回null
            };
            writerFutures.add(writerExecutor.submit(writeTask));
        }

        // 等待并检查所有并发写入任务的结果
        writerExecutor.shutdown();
        try {
            for (Future<Void> future : writerFutures) {
                future.get(); // 如果任何一个子任务因为重试耗尽而最终失败，这里会抛出异常
            }
            System.out.println("【定时任务-阶段2】所有客户的市值并发更新任务均已成功完成！");
        } catch (InterruptedException | ExecutionException e) {
            System.err.println("【定时任务-阶段2】并发更新市值时发生严重错误！一个或多个写入任务最终失败。");
            e.printStackTrace();
            Thread.currentThread().interrupt();
        } finally {
            if (!writerExecutor.isTerminated()) writerExecutor.shutdownNow();
        }
    }



    /**
     * 【任务二】每日凌晨，批量刷新所有客户的画像标签。
     * 这是一个计算和IO密集型任务，安排在系统负载最低的凌晨执行。
     */
    @Scheduled(cron = "0 0 2 * * ?") // 每天 02:00 执行
    public void refreshAllCustomerTagsDaily() {
        System.out.println("【定时任务】开始执行每日全量客户画像刷新...");
        try {
            // 直接调用已经写好的并发刷新服务
            tagRefreshService.refreshAllTagsAtomically();
            System.out.println("【定时任务】每日全量客户画像刷新任务圆满完成！");
        } catch (Exception e) {
            System.err.println("【定时任务】在执行每日全量客户画像刷新时发生严重错误！");
            e.printStackTrace();
        }
    }


 }