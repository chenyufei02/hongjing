package com.whu.hongjing.controller;

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
@Tag(name = "测试数据生成工具", description = "用于生成各类模拟数据的接口")
public class MockDataController {

    @Autowired
    private MockDataService mockDataService;

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
}