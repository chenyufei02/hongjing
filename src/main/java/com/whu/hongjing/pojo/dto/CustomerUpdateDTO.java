package com.whu.hongjing.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import javax.validation.constraints.Pattern;


/**
 * 客户更新 DTO
 */
@Data
@Schema(description = "客户更新请求对象")
public class CustomerUpdateDTO {

    @NotNull(message = "客户ID不能为空")
    @Schema(description = "客户ID", example = "1")
    private Long id;

    @NotBlank(message = "姓名不能为空")
    @Schema(description = "姓名", example = "张三")
    private String name;

    @NotBlank(message = "性别不能为空")
    @Schema(description = "性别", example = "男")
    private String gender;

    @NotBlank(message = "证件类型不能为空")
    @Schema(description = "证件类型", example = "身份证")
    private String idType;

    @NotBlank(message = "证件号码不能为空")
    @Pattern(regexp = "^(\\d{18}|\\d{17}(\\d|X|x))$", message = "身份证号码格式不正确")
    @Schema(description = "证件号码", example = "123456789012345678")
    private String idNumber;

    @NotNull(message = "出生日期不能为空")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "出生日期", example = "1990-01-01")
    private LocalDate birthDate;

    @NotBlank(message = "国籍不能为空")
    @Schema(description = "国籍", example = "中国")
    private String nationality;

    @NotBlank(message = "职业不能为空")
    @Schema(description = "职业", example = "软件工程师")
    private String occupation;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号码格式不正确")
    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @NotBlank(message = "联系地址不能为空")
    @Schema(description = "联系地址", example = "北京市朝阳区xxx路xxx号")
    private String address;
}