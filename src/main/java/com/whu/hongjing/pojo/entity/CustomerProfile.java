package com.whu.hongjing.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("customer_profile")
public class CustomerProfile {

    @TableId // 主键就是 customer_id
    private Long customerId;

    // 总资产规模（总市值）
    private BigDecimal totalMarketValue;

    // 平均持仓周期
    private Integer avgHoldingDays;

    // 最近交易日期距今的天数（R）  R《30判断近期活跃
    private Integer recencyDays;

    // 90天内交易频率（F） 这里直接用次数表示不用频率 因为更加直观
    private Integer frequency90d;

    // 定投行为（F）
    private Boolean hasRegularInvestment;

    private LocalDateTime updateTime;

    // 创建一个方便使用的构造函数
    public CustomerProfile(Long customerId) {
        this.customerId = customerId;
    }
}
