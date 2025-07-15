package com.whu.hongjing.controller;

import com.whu.hongjing.pojo.dto.FundPurchaseDTO; // <-- 导入新的DTO
import com.whu.hongjing.pojo.dto.FundRedeemDTO;   // <-- 导入新的DTO
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
//import com.whu.hongjing.pojo.dto.FundTransactionDTO;
import com.whu.hongjing.pojo.entity.FundTransaction;
import com.whu.hongjing.service.FundTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
//import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fund-transaction")
@Tag(name = "基金交易记录管理", description = "提供基金交易的记录和查询接口")
public class FundTransactionController {

    @Autowired
    private FundTransactionService fundTransactionService;

// ================ 以下没有提供前端接口实现 ================
    @Operation(summary = "申购基金")
    @PostMapping("/purchase")
    public FundTransaction purchase(@RequestBody @Validated FundPurchaseDTO dto) {
        return fundTransactionService.createPurchaseTransaction(dto);
    }

    @Operation(summary = "赎回基金")
    @PostMapping("/redeem")
    public FundTransaction redeem(@RequestBody @Validated FundRedeemDTO dto) {
        return fundTransactionService.createRedeemTransaction(dto);
    }

// ============ 以下在pagecontroller里优化为了综合根据客户姓名、基金代码、交易类型查询 ============
    @Operation(summary = "根据客户ID查询其所有交易记录")
    @GetMapping("/customer/{customerId}")
    public List<FundTransaction> listByCustomerId(@PathVariable Long customerId) {
        QueryWrapper<FundTransaction> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("customer_id", customerId);
        queryWrapper.orderByDesc("transaction_time"); // 按交易时间降序
        return fundTransactionService.list(queryWrapper);
    }

    @Operation(summary = "根据交易ID查询单条交易详情")
    @GetMapping("/{id}")
    public FundTransaction getById(@PathVariable Long id) {
        return fundTransactionService.getById(id);
    }
}