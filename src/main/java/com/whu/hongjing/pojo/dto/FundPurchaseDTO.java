package com.whu.hongjing.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "基金申购请求对象")
public class FundPurchaseDTO {

    @NotNull(message = "客户ID不能为空")
    @Schema(description = "客户ID", example = "1")
    private Long customerId;

    @NotBlank(message = "基金代码不能为空")
    @Schema(description = "基金代码", example = "000001")
    private String fundCode;

    // 这里只保留了交易金额，完全移除了交易份额字段
    @NotNull(message = "申购金额不能为空")
    @Positive(message = "申购金额必须为正数")
    @Schema(description = "申购金额", example = "1000.00")
    private BigDecimal transactionAmount;

    @NotNull(message = "交易申请时间不能为空")
    @Schema(description = "交易申请时间", example = "2025-07-04T14:30:00")
    private LocalDateTime transactionTime;
}