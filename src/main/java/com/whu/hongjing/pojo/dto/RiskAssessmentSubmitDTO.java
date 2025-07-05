package com.whu.hongjing.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
@Schema(description = "风险评估提交请求对象")
public class RiskAssessmentSubmitDTO {

    @NotNull(message = "客户ID不能为空")
    @Schema(description = "客户ID", example = "1")
    private Long customerId;

    @NotNull(message = "风险评估得分不能为空")
    @Min(value = 0, message = "分数不能低于0")
    @Max(value = 100, message = "分数不能高于100")
    @Schema(description = "根据问卷计算出的总分", example = "55")
    private Integer score;

    @NotNull(message = "评估日期不能为空")
    @Schema(description = "评估完成日期", example = "2025-07-05")
    private LocalDate assessmentDate;
}