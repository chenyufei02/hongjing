package com.whu.hongjing.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 后台定时任务服务
 * 负责执行所有周期性的、需要批量处理的任务
 */
@Service
public class ScheduledTasksService {

    @Autowired
    private FundPriceUpdateService fundPriceUpdateService;
    @Autowired
    private CustomerHoldingService customerHoldingService;

    @Scheduled(cron = "0 09 17 * * ?")
    @Transactional
    public void updateNetValueAndMarketValueDaily() {
        System.out.println("【定时任务】开始执行每日净值与市值更新...");
        try {
            // 步骤1：更新所有基金的净值，并接收返回的、真正被更新了的基金列表
            int updatedPriceCount = fundPriceUpdateService.updateAllFundPrices();
            System.out.println("【定时任务】步骤1完成：更新了 " + updatedPriceCount + " 只基金的最新净值。");

            // 步骤2：将这个“增量列表”传递给市值计算服务，只处理必要的数据
            customerHoldingService.recalculateAllMarketValues();
            System.out.println("【定时任务】每日净值与市值更新任务圆满完成！");

        } catch (Exception e) {
            System.err.println("【定时任务】执行过程中发生严重错误！");
            e.printStackTrace();
        }
    }
}