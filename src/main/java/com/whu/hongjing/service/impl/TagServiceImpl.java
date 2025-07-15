package com.whu.hongjing.service.impl;

import com.whu.hongjing.pojo.entity.Customer;
import com.whu.hongjing.pojo.entity.FundInfo;
import com.whu.hongjing.service.CustomerService;
import com.whu.hongjing.service.FundInfoService;
import com.whu.hongjing.service.TagService;
import com.whu.hongjing.service.TagRefreshWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 【最终版】客户画像刷新服务的调度中心。
 * 它的职责是准备数据和管理线程池，将具体的刷新任务分发给 TagRefreshWorker 执行。
 */
@Service
public class TagServiceImpl implements TagService {

    @Autowired private CustomerService customerService;
    @Autowired private FundInfoService fundInfoService;
    @Autowired private TagRefreshWorker tagRefreshWorker; // 注入我们的“工人”

    /**
     * 【批量方法】刷新所有客户的画像数据（并行处理）。
     * 这是所有批量、定时任务的入口。
     */
    @Override
    public void refreshAllTagsAtomically() {
        // 1. 一次性获取所有客户，避免N+1查询
        List<Customer> allCustomers = customerService.list();
        if (allCustomers == null || allCustomers.isEmpty()) {
            System.out.println("【批量刷新】没有找到任何客户，任务结束。");
            return;
        }
        System.out.println("【批量刷新启动】为 " + allCustomers.size() + " 位客户并行更新画像...");

        // 2. 一次性获取所有基金信息，避免在循环中重复查询
        Map<String, FundInfo> fundInfoMap = fundInfoService.list().stream()
                .collect(Collectors.toMap(FundInfo::getFundCode, Function.identity()));

        // 3. 创建线程池执行任务
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        try {
            for (final Customer customer : allCustomers) {
                executor.submit(() -> {
                    try {
                        // 4. 为每个客户调用“工人”的事务方法进行刷新
                        tagRefreshWorker.refreshSingleCustomer(customer, fundInfoMap);
                    } catch (Exception e) {
                        System.err.println("【批量刷新错误】客户 " + customer.getId() + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            }
        } finally {
            executor.shutdown();
        }

        // 5. 等待所有任务执行完毕
        try {
            if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
                System.err.println("【批量刷新警告】线程池在1小时内未能完全终止。");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            System.err.println("【批量刷新错误】等待线程池终止时被中断。");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("【批量刷新完成】所有客户画像数据更新任务已完成！");
    }

    /**
     * 【核心方法】为单个客户刷新所有画像数据。
     * 这是所有实时、单体更新的入口。它现在是一个包装器。
     */
    @Override
    public void refreshTagsForCustomer(Long customerId) {
        // 1. 准备数据
        Customer customer = customerService.getById(customerId);
        if (customer == null) return;
        Map<String, FundInfo> fundInfoMap = fundInfoService.list().stream()
                .collect(Collectors.toMap(FundInfo::getFundCode, Function.identity()));

        // 2. 调用“工人”的方法执行刷新
        tagRefreshWorker.refreshSingleCustomer(customer, fundInfoMap);
    }
}