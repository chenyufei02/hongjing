package com.whu.hongjing.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "通用API响应对象")
public class ApiResponseVO {

    @Schema(description = "操作是否成功", example = "true")
    private boolean success;

    @Schema(description = "返回的消息文本", example = "操作成功！")
    private String message;
}