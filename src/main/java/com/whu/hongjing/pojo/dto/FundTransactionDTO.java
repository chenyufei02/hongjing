package com.whu.hongjing.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "新增基金交易记录数据传输对象")
public class FundTransactionDTO {

    @NotNull(message = "客户ID不能为空")
    @Schema(description = "客户ID", example = "1")
    private Long customerId;

    @NotBlank(message = "基金代码不能为空")
    @Schema(description = "基金代码", example = "000001")
    private String fundCode;

    @NotBlank(message = "交易类型不能为空")
    @Schema(description = "交易类型, e.g., 申购, 赎回", example = "申购")
    private String transactionType;

    @NotNull(message = "交易金额不能为空")
    @Positive(message = "交易金额必须为正数")
    @Schema(description = "交易金额", example = "10000.00")
    private BigDecimal transactionAmount;

    @Schema(description = "交易份额", example = "10000.0000")
    private BigDecimal transactionShares;

    @Schema(description = "成交净值", example = "1.0000")
    private BigDecimal sharePrice;

    @NotNull(message = "交易时间不能为空")
    @Schema(description = "交易时间", example = "2024-07-25T15:00:00")
    private LocalDateTime transactionTime;

    @Schema(description = "交易状态", example = "成功")
    private String status = "成功"; // 默认为成功
}