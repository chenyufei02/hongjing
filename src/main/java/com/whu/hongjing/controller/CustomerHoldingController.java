package com.whu.hongjing.controller;

import com.whu.hongjing.pojo.entity.CustomerHolding;
import com.whu.hongjing.service.CustomerHoldingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer-holding")
@Tag(name = "客户持仓管理", description = "提供客户持仓的查询接口")
public class CustomerHoldingController {

    @Autowired
    private CustomerHoldingService customerHoldingService;

    // 在pagecontroller里优化调用了 customerholdingservice里同时根据姓名和基金代码模糊查询的方法 getHoldingPage
    @Operation(summary = "根据客户ID查询其所有持仓信息")
    @GetMapping("/customer/{customerId}")
    public List<CustomerHolding> getHoldingsByCustomerId(@PathVariable Long customerId) {
        return customerHoldingService.listByCustomerId(customerId);
    }

    // 在mockdata中提供了一个统一一键刷新所有持仓数据的方法（模拟每天的数据更新）
    @Operation(summary = "【手动触发】为单个客户重新计算历史持仓")
    @PostMapping("/recalculate/{customerId}")
    public boolean recalculateHoldings(@PathVariable Long customerId) {
        return customerHoldingService.recalculateAndSaveHoldings(customerId);
    }

}