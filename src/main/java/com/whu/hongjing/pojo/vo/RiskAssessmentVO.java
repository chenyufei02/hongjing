package com.whu.hongjing.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Schema(description = "风险评估记录视图对象")
public class RiskAssessmentVO {

    // --- 核心评估信息 ---
    @Schema(description = "评估记录ID")
    private Long id;

    @Schema(description = "风险得分")
    private Integer riskScore;

    @Schema(description = "申报风险")
    private String riskLevel;

    @Schema(description = "实盘风险")
    private String actualRiskLevel;

    @Schema(description = "诊断结果")
    private String riskDiagnosis;

    @Schema(description = "评估日期")
    private LocalDate assessmentDate;

    @Schema(description = "记录创建时间")
    private LocalDateTime createTime;

    // --- 关联的客户信息 ---
    @Schema(description = "客户ID")
    private Long customerId;

    @Schema(description = "客户姓名")
    private String customerName;
}