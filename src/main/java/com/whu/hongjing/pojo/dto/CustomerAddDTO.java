package com.whu.hongjing.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

/**
 * 客户新增 DTO
 */
@Data
@Schema(description = "客户新增请求对象")
public class CustomerAddDTO {

    @Schema(description = "姓名", required = true, example = "张三")
    private String name;

    @Schema(description = "性别", required = true, example = "男")
    private String gender;

    @Schema(description = "证件类型", required = true, example = "身份证")
    private String idType;

    @Schema(description = "证件号码", required = true, example = "123456789012345678")
    private String idNumber;

    @Schema(description = "出生日期", required = true, example = "1990-01-01")
    private LocalDate birthDate;

    @Schema(description = "国籍", required = true, example = "中国")
    private String nationality;

    @Schema(description = "职业", required = true, example = "软件工程师")
    private String occupation;

    @Schema(description = "手机号", required = true, example = "13800138000")
    private String phone;

    @Schema(description = "联系地址", required = true, example = "北京市朝阳区xxx路xxx号")
    private String address;
}