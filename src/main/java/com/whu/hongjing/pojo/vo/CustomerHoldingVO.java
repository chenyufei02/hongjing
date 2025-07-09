package com.whu.hongjing.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "客户持仓视图对象")
public class CustomerHoldingVO {

    // --- 核心持仓信息 ---
    @Schema(description = "持仓记录ID")
    private Long id;

    @Schema(description = "总持有份额")
    private BigDecimal totalShares;

    @Schema(description = "持仓平均成本")
    private BigDecimal averageCost;

    @Schema(description = "当前市值")
    private BigDecimal marketValue;

    @Schema(description = "持仓最后更新日期")
    private LocalDateTime lastUpdateDate;

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