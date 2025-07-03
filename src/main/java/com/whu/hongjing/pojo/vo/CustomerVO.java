package com.whu.hongjing.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Schema(description = "客户信息响应对象")
public class CustomerVO {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "姓名")
    private String name;

    @Schema(description = "性别")
    private String gender;

    @Schema(description = "证件类型")
    private String idType;

    @Schema(description = "证件号码")
    private String idNumber;

    @Schema(description = "出生日期")
    private LocalDate birthDate;

    @Schema(description = "国籍")
    private String nationality;

    @Schema(description = "职业")
    private String occupation;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "联系地址")
    private String address;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}