package com.whu.hongjing.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户持仓信息实体类
 * 这张表记录了每个客户对每一只基金的当前持仓快照。
 */
@Data
@TableName("customer_holding")
public class CustomerHolding {

    /**
     * 持仓记录的唯一主键ID，由数据库自动生成。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 客户ID，用于关联到customer表，表明这条持仓记录属于哪个客户。
     */
    private Long customerId;

    /**
     * 基金代码，用于关联到fund_info表，表明客户持有的是哪一只基金。
     */
    private String fundCode;

    /**
     * 总持有份额。客户当前持有该基金的总份数，是所有申购份额减去所有赎回份额的结果。
     */
    private BigDecimal totalShares;

    /**
     * 客户持有的基金的当前市值。根据最新的基金净值计算出的、当前持有份额的总价值 (市值 = 总份额 * 最新净值)。
     */
    private BigDecimal marketValue;

    /**
     * 持仓平均成本。计算客户【买入】这只基金的平均单位价格，是计算盈亏的关键指标。
     */
    private BigDecimal averageCost;

    /**
     * 持仓最后更新日期。记录这条持仓信息最近一次因为交易而发生变化的时间。是最新一次交易完成的时间
     */
    private LocalDateTime lastUpdateDate;

    /**
     * 记录创建时间，由MyBatis-Plus在插入时自动填充。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 记录更新时间，由MyBatis-Plus在插入或更新时自动填充。是在系统里更新的时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}