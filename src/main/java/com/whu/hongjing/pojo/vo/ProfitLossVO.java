package com.whu.hongjing.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Schema(description = "客户盈亏视图对象")
public class ProfitLossVO {

    @Schema(description = "客户ID")
    private Long customerId;

    @Schema(description = "客户姓名")
    private String customerName;

    @Schema(description = "累计投资总额")
    private BigDecimal totalInvestment;

    @Schema(description = "当前总市值")
    private BigDecimal totalMarketValue;

    @Schema(description = "总盈亏金额")
    private BigDecimal totalProfitLoss;

    @Schema(description = "总盈亏率 (%)")
    private BigDecimal profitLossRate;
}