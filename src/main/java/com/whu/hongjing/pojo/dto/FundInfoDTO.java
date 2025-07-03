package com.whu.hongjing.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
@Schema(description = "基金信息数据传输对象")
public class FundInfoDTO {

    @NotBlank(message = "基金代码不能为空")
    @Size(max = 20, message = "基金代码长度不能超过20")
    @Schema(description = "基金代码", example = "000001")
    private String fundCode;

    @NotBlank(message = "基金名称不能为空")
    @Size(max = 100, message = "基金名称长度不能超过100")
    @Schema(description = "基金名称", example = "华夏成长混合")
    private String fundName;

    @Size(max = 50, message = "基金类型长度不能超过50")
    @Schema(description = "基金类型", example = "混合型")
    private String fundType;
}