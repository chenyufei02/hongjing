package com.whu.hongjing.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "基金交易流水视图对象")
public class FundTransactionVO {

    // --- 核心交易信息 ---
    @Schema(description = "交易记录ID")
    private Long id;

    @Schema(description = "交易类型（申购/赎回）")
    private String transactionType;

    @Schema(description = "交易金额")
    private BigDecimal transactionAmount;

    @Schema(description = "交易份额")
    private BigDecimal transactionShares;

    @Schema(description = "成交净值")
    private BigDecimal sharePrice;

    @Schema(description = "交易时间")
    private LocalDateTime transactionTime;

    @Schema(description = "交易状态")
    private String status;

    // --- 关联的客户信息 ---
    @Schema(description = "客户ID")
    private Long customerId;

    @Schema(description = "客户姓名")
    private String customerName;

    // --- 关联的基金信息 ---
    @Schema(description = "基金代码")
    private String fundCode;

    @Schema(description = "基金名称")
    private String fundName;
}