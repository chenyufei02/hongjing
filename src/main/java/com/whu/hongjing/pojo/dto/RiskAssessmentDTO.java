package com.whu.hongjing.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
@Schema(description = "新增客户风险评估数据传输对象")
public class RiskAssessmentDTO {

    @NotNull(message = "客户ID不能为空")
    @Schema(description = "客户ID", example = "1")
    private Long customerId;

    @Schema(description = "风险评估得分", example = "65")
    private Integer riskScore;

    @Schema(description = "风险等级", example = "稳健型")
    private String riskLevel;

    @NotNull(message = "评估日期不能为空")
    @Schema(description = "评估完成日期", example = "2024-07-25")
    private LocalDate assessmentDate;
}