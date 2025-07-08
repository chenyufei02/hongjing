package com.whu.hongjing.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalDate;
/**
 * 风险评估表
 * @param
 * @return
 * @author yufei
 * @since 2025/7/3
 */
@Data
@TableName("risk_assessment")
public class RiskAssessment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long customerId;

    // 风险得分
    private Integer riskScore;

    // 申报风险等级
    private String riskLevel;

    // 评估日期
    private LocalDate assessmentDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}