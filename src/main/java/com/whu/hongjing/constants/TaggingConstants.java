package com.whu.hongjing.constants;

import java.math.BigDecimal;


/**
 * 客户画像标签体系的常量定义中心。
 * 所有业务规则阈值和标签文本都在此统一管理，便于未来维护和调整。
 * 这个类被设计为final且构造函数私有，以防止被继承或实例化。
 */
public final class TaggingConstants {

    // --- 1. 业务规则阈值 (Thresholds) ---

    // 资产规模 (M) 阈值
    public static final BigDecimal ASSET_THRESHOLD_HIGH = new BigDecimal("500000"); // 50万
    public static final BigDecimal ASSET_THRESHOLD_LOW = new BigDecimal("100000");  // 10万

    // 持仓风格阈值 (天数)
    public static final int HOLDING_STYLE_THRESHOLD_DAYS = 180;

    // "短期交易型" 客户的 R 和 F 阈值
    public static final int RECENCY_ACTIVE_DAYS = 30;   // 近期活跃
    public static final int RECENCY_SLEEP_DAYS = 90;    // 近期沉睡

    public static final int FREQUENCY_HIGH_COUNT = 10;  // 高频交易
    public static final int FREQUENCY_LOW_COUNT = 2;    // 低频交易

    // "长期持有型" 客户的 R 阈值 (月数)
    public static final int RECENCY_STAGNANT_MONTHS = 3; // 投入停滞
    public static final int RECENCY_OUTFLOW_MONTHS = 6;  // 资产流出

    // 实盘风险分数的阈值 (与 fund_info.risk_score 对应)
    public static final double ACTUAL_RISK_THRESHOLD_AGGRESSIVE = 4.5; // 激进型
    public static final double ACTUAL_RISK_THRESHOLD_GROWTH = 3.5;     // 成长型
    public static final double ACTUAL_RISK_THRESHOLD_BALANCED = 2.5;   // 平衡型
    public static final double ACTUAL_RISK_THRESHOLD_STEADY = 1.5;     // 稳健型

    // --- 2. 标签分类 (Categories) ---
    public static final String CATEGORY_ASSET = "资产规模";
    public static final String CATEGORY_STYLE = "持仓风格";
    public static final String CATEGORY_RECENCY = "近期";
    public static final String CATEGORY_FREQUENCY = "历史";
    public static final String CATEGORY_GENDER = "性别";
    public static final String CATEGORY_OCCUPATION = "职业";
    public static final String CATEGORY_AGE = "年龄分代";
    public static final String CATEGORY_RISK_DECLARED = "申报风险";
    public static final String CATEGORY_RISK_ACTUAL = "实盘风险";
    public static final String CATEGORY_RISK_DIAGNOSIS = "风险诊断结果";


    // --- 3. 标签文本 (Labels) ---

    // 资产规模标签
    public static final String LABEL_ASSET_HIGH = "资产: >50万";
    public static final String LABEL_ASSET_MEDIUM = "资产: 10-50万";
    public static final String LABEL_ASSET_LOW = "资产: <10万";

    // 持仓风格标签
    public static final String LABEL_STYLE_LONG_TERM = "风格: 长持型";
    public static final String LABEL_STYLE_SHORT_TERM = "风格: 交易型";

    // 短期交易型 - 近期活跃度(R)标签
    public static final String LABEL_RECENCY_SHORT_ACTIVE = "近期: 近期活跃";
    public static final String LABEL_RECENCY_SHORT_SLEEP = "近期: 近期沉睡";
    public static final String LABEL_RECENCY_SHORT_LOST = "近期: 近期流失";

    // 短期交易型 - 历史习惯(F)标签
    public static final String LABEL_FREQUENCY_SHORT_HIGH = "历史: 高频交易";
    public static final String LABEL_FREQUENCY_SHORT_MEDIUM = "历史: 中频交易";
    public static final String LABEL_FREQUENCY_SHORT_LOW = "历史: 低频交易";

    // 长期持有型 - 近期活跃度(R)标签
    public static final String LABEL_RECENCY_LONG_INVEST = "近期: 持续投入";
    public static final String LABEL_RECENCY_LONG_STAGNANT = "近期: 投入停滞";
    public static final String LABEL_RECENCY_LONG_OUTFLOW = "近期: 资产流出";

    // 长期持有型 - 历史习惯(F)标签
    public static final String LABEL_FREQUENCY_LONG_REGULAR = "历史: 有定投行为";
    public static final String LABEL_FREQUENCY_LONG_IRREGULAR = "历史: 无定投行为";

    // 实盘风险等级标签
    public static final String LABEL_ACTUAL_RISK_AGGRESSIVE = "实盘-激进型";
    public static final String LABEL_ACTUAL_RISK_GROWTH = "实盘-成长型";
    public static final String LABEL_ACTUAL_RISK_BALANCED = "实盘-平衡型";
    public static final String LABEL_ACTUAL_RISK_STEADY = "实盘-稳健型";
    public static final String LABEL_ACTUAL_RISK_CONSERVATIVE = "实盘-保守型";
    public static final String LABEL_ACTUAL_RISK_UNKNOWN = "实盘-无法评估";

    // 【新增】风险诊断标签
    public static final String LABEL_DIAGNOSIS_MATCH = "诊断-知行合一";
    public static final String LABEL_DIAGNOSIS_OVERWEIGHT = "诊断-行为激进";
    public static final String LABEL_DIAGNOSIS_UNDERWEIGHT = "诊断-行为保守";
    public static final String LABEL_DIAGNOSIS_UNKNOWN = "诊断-风险未知";


    /**
     * 私有构造函数，防止该常量类被外部实例化。
     */
    private TaggingConstants() {
    }
}