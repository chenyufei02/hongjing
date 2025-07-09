package com.whu.hongjing.controller;
import com.whu.hongjing.service.ScheduledTasksService;
import com.whu.hongjing.pojo.vo.ApiResponseVO;
import com.whu.hongjing.service.MockDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock-data")
@Tag(name = "测试数据生成工具", description = "用于生成和刷新各类模拟数据的接口")
public class MockDataController {

    @Autowired
    private MockDataService mockDataService;
    @Autowired
    private ScheduledTasksService scheduledTasksService;


    @PostMapping("/create-customers")
    @Operation(summary = "【工具I：创世】生成一批全新的模拟客户及其初始风险评估")
    public ApiResponseVO createCustomers(@RequestParam(defaultValue = "50") int count) {
        try {
            String resultMessage = mockDataService.createMockCustomers(count);
            return new ApiResponseVO(true, resultMessage);
        } catch (Exception e) {
            e.printStackTrace();
            return new ApiResponseVO(false, "模拟客户生成失败: " + e.getMessage());
        }
    }

    @PostMapping("/simulate-trading-days")
    @Operation(summary = "【工具II：演绎】为所有现有客户模拟接下来一段时间的交易")
    public ApiResponseVO simulateTradingDays(@RequestParam(defaultValue = "30") int days) {
        try {
            String resultMessage = mockDataService.simulateTradingDays(days);
            return new ApiResponseVO(true, resultMessage);
        } catch (Exception e) {
            e.printStackTrace();
            return new ApiResponseVO(false, "交易模拟失败: " + e.getMessage());
        }
    }

    /**
     * 手动触发一次每日的基金净值更新，以及所有客户持仓市值的重新计算。调用的是定时任务的服务而非测试数据的服务。
     */
    @PostMapping("/trigger-daily-update")
    @Operation(summary = "【工具III：刷新净值】手动触发一次每日净值和市值的更新任务，并接着更新客户持仓数据。")
    public ApiResponseVO triggerDailyUpdate() {
        try {
            // 直接调用定时任务的核心方法
            scheduledTasksService.updateNetValueAndMarketValueDaily();
            return new ApiResponseVO(true, "每日净值和市值更新任务已手动触发并成功执行！同时更新了客户持仓数据！");
        } catch (Exception e) {
            e.printStackTrace();
            return new ApiResponseVO(false, "手动触发每日任务失败: " + e.getMessage());
        }
    }
}