package com.whu.hongjing.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "标签统计视图对象")
public class TagVO {

    @Schema(description = "标签名称")
    private String tagName;

    @Schema(description = "拥有该标签的客户数量")
    private Integer customerCount;

    @Schema(description = "标签所属类别")
    private String tagCategory;

}