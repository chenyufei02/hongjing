package com.whu.hongjing.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("fund_info")
public class FundInfo {

    @TableId(type = IdType.INPUT) // 主键类型为手动输入
    private String fundCode;

    private String fundName;

    private String fundType;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}