package com.whu.hongjing.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "客户标签视图对象")
public class CustomerTagVO {

    @Schema(description = "标签名称", example = "90后")
    private String tagName;
}