package com.whu.hongjing.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("fund_transaction")
public class FundTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long customerId;

    private String fundCode;

    private String transactionType;

    private BigDecimal transactionAmount; // 交易金额

    private BigDecimal transactionShares;  // 交易份额

    private BigDecimal sharePrice;       // 成交净值

    private LocalDateTime transactionTime;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}