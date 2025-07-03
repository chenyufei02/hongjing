package com.whu.hongjing.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalDate;

/**
 * 客户基础信息实体类
 */
@Data // Lombok 自动生成getter/setter/toString等方法
@TableName("customer") // 指定对应数据库表名
public class Customer {

    @TableId(type = IdType.AUTO)
    private Long id;                  // 主键ID，自增

    private String name;              // 姓名

    private String gender;            // 性别

    private String idType;            // 证件类型

    private String idNumber;          // 证件号码

    private LocalDate birthDate;      // 出生日期

    private String nationality;       // 国籍

    private String occupation;        // 职业

    private String phone;             // 手机号

    private String address;           // 联系地址

    @Schema(hidden = true)
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime; // 创建时间

    @Schema(hidden = true)
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime; // 更新时间
}
