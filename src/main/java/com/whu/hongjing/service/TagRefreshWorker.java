package com.whu.hongjing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.whu.hongjing.constants.TaggingConstants;
import com.whu.hongjing.enums.RiskLevelEnum;
import com.whu.hongjing.pojo.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;


/**
 * 这是一个专门负责执行单个客户标签计算到刷新事务的模块，将其独立出来让职责更清晰。
 */
@Component
public class TagRefreshWorker {

    @Autowired private CustomerHoldingService customerHoldingService;
    @Autowired private FundTransactionService fundTransactionService;
    @Autowired private CustomerProfileService customerProfileService;
    @Autowired private CustomerTagRelationService customerTagRelationService;
    @Autowired private RiskAssessmentService riskAssessmentService;

    /**
     * 执行数据库操作的核心方法。 刷新单个客户的所有标签数据。
     * 必须是 public 且带有 @Transactional 注解，以便被外部调用并开启事务。
     * 接收已经查询好的 Customer 和 FundInfo 数据，专注于计算和写入。
     * @param customer 待刷新画像的客户对象
     * @param fundInfoMap 全量的基金信息，用于提高性能
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void refreshSingleCustomer(Customer customer, Map<String, FundInfo> fundInfoMap) {
        Long customerId = customer.getId();

        // --- 准备阶段：获取当前客户的其他关联数据 ---
        List<CustomerHolding> holdings = customerHoldingService.listByCustomerId(customerId);
        List<FundTransaction> transactions = fundTransactionService.list(new QueryWrapper<FundTransaction>().eq("customer_id", customerId));
        RiskAssessment latestAssessment = riskAssessmentService.getOne(
                new QueryWrapper<RiskAssessment>().eq("customer_id", customerId).orderByDesc("assessment_date").last("LIMIT 1"));

        // --- 计算阶段1：计算核心量化指标 (CustomerProfile) ---
        CustomerProfile profile = calculateAndSaveProfile(customer, holdings, transactions);

        // --- 计算阶段2：生成所有标签 ---
        List<CustomerTagRelation> newTags = generateAllTags(customer, profile, latestAssessment, transactions, holdings, fundInfoMap);

        // --- 持久化阶段：覆盖式写入数据库 (先删后增) ---
        customerTagRelationService.remove(new QueryWrapper<CustomerTagRelation>().eq("customer_id", customerId));
        if (!newTags.isEmpty()) {
            customerTagRelationService.saveBatch(newTags);
        }
    }



    /**
     * 负责计算并保存一个客户的所有核心“量化”数据到 customer_profile 表。
     * @return 更新后的 CustomerProfile 对象
     */
    private CustomerProfile calculateAndSaveProfile(Customer customer, List<CustomerHolding> holdings, List<FundTransaction> transactions) {
        // 存储了当前客户的量化指标的profile
        CustomerProfile profile = customerProfileService.getById(customer.getId());

        if (profile == null) {
            profile = new CustomerProfile(customer.getId());
        }

        // 1. 计算平均持仓天数
        if (!holdings.isEmpty()) {
            long totalDaysSum = 0;  // 所有基金的总持仓天数
            int validHoldingsCount = 0;  // 持仓的基金数量

            // 遍历每只持仓的基金
            for (CustomerHolding holding : holdings) {
                // 对于当前这只基金(holding)，从【所有】历史交易记录(transactions)中， 找到它【第一次】被申购的那条记录。
                // Optional 是一个容器，它可能包含一个FundTransaction，也可能为空。
                Optional<FundTransaction> firstPurchaseOpt = transactions.stream()
                        .filter(tx -> tx.getFundCode().equals(holding.getFundCode()) && "申购".equals(tx.getTransactionType()))
                        .min(Comparator.comparing(FundTransaction::getTransactionTime));

                // 计算从第一次购买那天，到今天总共过去了多少天
                if (firstPurchaseOpt.isPresent()) {
                    totalDaysSum += ChronoUnit.DAYS.between(firstPurchaseOpt.get().getTransactionTime().toLocalDate(), LocalDate.now());
                    validHoldingsCount++;   // 记录持仓过的基金数+1
                }
            }
            // 汇总计算平均持仓天数
            profile.setAvgHoldingDays(validHoldingsCount > 0 ? (int) (totalDaysSum / validHoldingsCount) : 0);

        // 如果完全没有持仓 直接记0
        } else {
            profile.setAvgHoldingDays(0);
        }

        // 2. 计算总市值 (M)
        profile.setTotalMarketValue(
            holdings.stream()
            .map(CustomerHolding::getMarketValue)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add)   // 按照add的方式从0开始累加
        );

        // 3. 计算 R, F 和定投行为
        if (!transactions.isEmpty()) {
            // 找到最近交易的交易
            Optional<LocalDateTime> lastTxTimeOpt = transactions.stream()
                .map(FundTransaction::getTransactionTime).max(LocalDateTime::compareTo);

            // 计算最近交易的交易日期到今天的总天数 R
            profile.setRecencyDays(lastTxTimeOpt.map(localDateTime -> (int) ChronoUnit.DAYS.between(localDateTime, LocalDateTime.now())).orElse(null));

            // 计算90天内的交易频率 F
            profile.setFrequency90d((int) transactions.stream()
                // 取出当前时间点90天前 之后发生的交易
                .filter(tx -> tx.getTransactionTime().isAfter(LocalDateTime.now().minusDays(90))).count());
            // 计算长持型的定投行为
            profile.setHasRegularInvestment(checkForRegularInvestment(transactions));
        // 没有交易行为 设置为空
        } else {
            profile.setRecencyDays(null);
            profile.setFrequency90d(0);
            profile.setHasRegularInvestment(false);
        }

        customerProfileService.saveOrUpdate(profile);
        return profile;
    }


    /**
     * 生成所有标签（包括计算好的profile数据 和其他的基础标签数据）
     */
    private List<CustomerTagRelation> generateAllTags(Customer customer, CustomerProfile profile, RiskAssessment assessment, List<FundTransaction> transactions, List<CustomerHolding> holdings, Map<String, FundInfo> fundInfoMap) {

        List<CustomerTagRelation> tags = new ArrayList<>();  // 初始化保存客户所有标签对象 的列表（一个标签对应一行数据 对应一个CustomerTagRelation对象）

        Long customerId = customer.getId();

        // 性别 年龄 职业标签
        if (customer.getGender() != null) tags.add(new CustomerTagRelation(customerId, customer.getGender(), TaggingConstants.CATEGORY_GENDER));
        if (customer.getOccupation() != null) tags.add(new CustomerTagRelation(customerId, customer.getOccupation(), TaggingConstants.CATEGORY_OCCUPATION));
        if (customer.getBirthDate() != null) tags.add(new CustomerTagRelation(customerId, getAgeGroupTag(customer.getBirthDate()), TaggingConstants.CATEGORY_AGE));

        // 计算并添加申报风险
        String declaredRiskLevel = (assessment != null && assessment.getRiskLevel() != null) ? assessment.getRiskLevel() : "未知";
        tags.add(new CustomerTagRelation(customerId, declaredRiskLevel, TaggingConstants.CATEGORY_RISK_DECLARED));
        // 计算并添加实盘风险+风险诊断结果（返回的是一个保存了二者的CustomerTagRelation对象的列表，因此要用addall一次性添加两个标签）
        tags.addAll(calculateAndGenerateRiskTags(customerId, holdings, fundInfoMap, declaredRiskLevel));

        // 开始计算 总资产标签M
        if (profile.getTotalMarketValue() != null) {
            BigDecimal mValue = profile.getTotalMarketValue();
            if (mValue.compareTo(TaggingConstants.ASSET_THRESHOLD_HIGH) >= 0) {
                tags.add(new CustomerTagRelation(customerId, TaggingConstants.LABEL_ASSET_HIGH, TaggingConstants.CATEGORY_ASSET));
            } else if (mValue.compareTo(TaggingConstants.ASSET_THRESHOLD_LOW) >= 0) {
                tags.add(new CustomerTagRelation(customerId, TaggingConstants.LABEL_ASSET_MEDIUM, TaggingConstants.CATEGORY_ASSET));
            } else {
                tags.add(new CustomerTagRelation(customerId, TaggingConstants.LABEL_ASSET_LOW, TaggingConstants.CATEGORY_ASSET));
            }
        }

        // 开始计算 持仓风格标签 （平均持仓天数作为核心量化指标，已经在上面第一阶段的方法里先计算好了 此处只用直接调用判断即可）
        boolean isLongTermHolder = profile.getAvgHoldingDays() != null && profile.getAvgHoldingDays() > TaggingConstants.HOLDING_STYLE_THRESHOLD_DAYS;
        if (isLongTermHolder) {
            tags.add(new CustomerTagRelation(customerId, TaggingConstants.LABEL_STYLE_LONG_TERM, TaggingConstants.CATEGORY_STYLE));
        } else {
            tags.add(new CustomerTagRelation(customerId, TaggingConstants.LABEL_STYLE_SHORT_TERM, TaggingConstants.CATEGORY_STYLE));
        }

        // 开始计算 最近交易日期标签 R 和 交易频率标签F

        // 开始为长持型计算90天、180天净资产流入/流出 （R标签），以及是否有定投行为（F标签）
        if (isLongTermHolder) {
            // 计算R
            tags.add(generateLongTermRecencyTag(customerId, transactions));
            // 计算F
            String freqTag = profile.getHasRegularInvestment() ? TaggingConstants.LABEL_FREQUENCY_LONG_REGULAR : TaggingConstants.LABEL_FREQUENCY_LONG_IRREGULAR;
            tags.add(new CustomerTagRelation(customerId, freqTag, TaggingConstants.CATEGORY_FREQUENCY));
        // 开始为交易型计算 近期活跃（<30天） 沉睡 流失（>90天） （R标签），以及近期交易频率 高频 中频 低频（F标签）
        } else {
            // 计算R
             if (profile.getRecencyDays() != null) {
                int rDays = profile.getRecencyDays();
                String recencyTag;
                if (rDays <= TaggingConstants.RECENCY_ACTIVE_DAYS) recencyTag = TaggingConstants.LABEL_RECENCY_SHORT_ACTIVE;
                else if (rDays <= TaggingConstants.RECENCY_SLEEP_DAYS) recencyTag = TaggingConstants.LABEL_RECENCY_SHORT_SLEEP;
                else recencyTag = TaggingConstants.LABEL_RECENCY_SHORT_LOST;
                tags.add(new CustomerTagRelation(customerId, recencyTag, TaggingConstants.CATEGORY_RECENCY));
            }
             // 计算F
            if (profile.getFrequency90d() != null) {
                int fCount = profile.getFrequency90d();
                String freqTag;
                if (fCount > TaggingConstants.FREQUENCY_HIGH_COUNT) freqTag = TaggingConstants.LABEL_FREQUENCY_SHORT_HIGH;
                else if (fCount > TaggingConstants.FREQUENCY_LOW_COUNT) freqTag = TaggingConstants.LABEL_FREQUENCY_SHORT_MEDIUM;
                else freqTag = TaggingConstants.LABEL_FREQUENCY_SHORT_LOW;
                tags.add(new CustomerTagRelation(customerId, freqTag, TaggingConstants.CATEGORY_FREQUENCY));
            }
        }
        return tags;
    }






    // 计算并判断定投行为的方法的辅助方法（提供给计算客户核心指标的方法使用）
    private boolean checkForRegularInvestment(List<FundTransaction> transactions) {
        Map<YearMonth, Boolean> monthlyPurchaseMap = transactions.stream()
            // 筛选一年内的申购交易
            .filter(tx -> "申购".equals(tx.getTransactionType()) && tx.getTransactionTime().isAfter(LocalDateTime.now().minusYears(1)))
            .collect(Collectors.toMap(
                tx -> YearMonth.from(tx.getTransactionTime()),  // 取出key（年+月份的时间），不管一个月内有多少条交易记录都合并成一个月的key
                v -> true,    // 只要这个月买过了 有记录 value就设置为true
                (existing, replacement) -> existing)  // 如果一个月内有多条记录 只保留已经存在的记录 不用新的记录
            );
        YearMonth currentMonth = YearMonth.now();
        // 判断在12个月内（i=9时也分别是往前倒1个月，2个月）是否有过定投行为（连续3个月都有购入）
        for (int i = 0; i <= 9; i++) {
            YearMonth start = currentMonth.minusMonths(i);
            if (monthlyPurchaseMap.getOrDefault(start, false) &&
                monthlyPurchaseMap.getOrDefault(start.minusMonths(1), false) &&
                monthlyPurchaseMap.getOrDefault(start.minusMonths(2), false)) {
                return true;
            }
        }
        return false;
    }


    // 计算实盘风险+风险诊断结果的辅助方法（提供给生成所有标签的方法使用）
    private List<CustomerTagRelation> calculateAndGenerateRiskTags(Long customerId, List<CustomerHolding> holdings, Map<String, FundInfo> fundInfoMap, String declaredRiskLevel) {

        List<CustomerTagRelation> riskTags = new ArrayList<>(); // 初始化一个既能保存实盘风险  又能保存风险诊断标签结果的列表

        // 没有持仓赋默认unknown值
        if (holdings == null || holdings.isEmpty()) {
            riskTags.add(new CustomerTagRelation(customerId, TaggingConstants.LABEL_ACTUAL_RISK_UNKNOWN, TaggingConstants.CATEGORY_RISK_ACTUAL));
            riskTags.add(new CustomerTagRelation(customerId, TaggingConstants.LABEL_DIAGNOSIS_UNKNOWN, TaggingConstants.CATEGORY_RISK_DIAGNOSIS));
            return riskTags;
        }

        // 累加总持仓市值<0赋默认unknown值
        BigDecimal totalMarketValue = holdings.stream().map(CustomerHolding::getMarketValue).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalMarketValue.compareTo(BigDecimal.ZERO) <= 0) {
            riskTags.add(new CustomerTagRelation(customerId, TaggingConstants.LABEL_ACTUAL_RISK_UNKNOWN, TaggingConstants.CATEGORY_RISK_ACTUAL));
            riskTags.add(new CustomerTagRelation(customerId, TaggingConstants.LABEL_DIAGNOSIS_UNKNOWN, TaggingConstants.CATEGORY_RISK_DIAGNOSIS));
            return riskTags;
        }

        // 【开始计算和保存实盘风险】
        BigDecimal weightedRiskSum = BigDecimal.ZERO;  // 累计所有持仓的风险分数 = 风险等级*市值
        for (CustomerHolding holding : holdings) {
            FundInfo fund = fundInfoMap.get(holding.getFundCode());
            if (fund != null && fund.getRiskScore() != null && holding.getMarketValue() != null) {
                weightedRiskSum = weightedRiskSum.add(holding.getMarketValue().multiply(new BigDecimal(fund.getRiskScore())));
            }
        }
        // 平均实盘风险分数 = 总风险分数 / 总市值
        double actualRiskScore = weightedRiskSum.divide(totalMarketValue, 2, RoundingMode.HALF_UP).doubleValue();
        String actualRiskLabel;
        // 根据分数打上实盘风险标签 并保存到列表
        if (actualRiskScore >= TaggingConstants.ACTUAL_RISK_THRESHOLD_AGGRESSIVE) actualRiskLabel = TaggingConstants.LABEL_ACTUAL_RISK_AGGRESSIVE;
        else if (actualRiskScore >= TaggingConstants.ACTUAL_RISK_THRESHOLD_GROWTH) actualRiskLabel = TaggingConstants.LABEL_ACTUAL_RISK_GROWTH;
        else if (actualRiskScore >= TaggingConstants.ACTUAL_RISK_THRESHOLD_BALANCED) actualRiskLabel = TaggingConstants.LABEL_ACTUAL_RISK_BALANCED;
        else if (actualRiskScore >= TaggingConstants.ACTUAL_RISK_THRESHOLD_STEADY) actualRiskLabel = TaggingConstants.LABEL_ACTUAL_RISK_STEADY;
        else actualRiskLabel = TaggingConstants.LABEL_ACTUAL_RISK_CONSERVATIVE;
        riskTags.add(new CustomerTagRelation(customerId, actualRiskLabel, TaggingConstants.CATEGORY_RISK_ACTUAL));

        // 【开始计算和保存风险诊断】
        String diagnosisLabel = TaggingConstants.LABEL_DIAGNOSIS_UNKNOWN;

        if (!"未知".equals(declaredRiskLevel)) {
            // 一个MAP里同时保存 申报风险的各种标签的对应评分 和实盘风险的各种标签的对应评分（这里的评分仅用来判定是不是相等）
            Map<String, Integer> riskLevelMap = Map.of(
                RiskLevelEnum.CONSERVATIVE.getLevelName(), 1,
                RiskLevelEnum.STEADY.getLevelName(), 2,
                RiskLevelEnum.BALANCED.getLevelName(), 3,
                RiskLevelEnum.GROWTH.getLevelName(), 4,
                RiskLevelEnum.AGGRESSIVE.getLevelName(), 5,
                TaggingConstants.LABEL_ACTUAL_RISK_CONSERVATIVE, 1,
                TaggingConstants.LABEL_ACTUAL_RISK_STEADY, 2,
                TaggingConstants.LABEL_ACTUAL_RISK_BALANCED, 3,
                TaggingConstants.LABEL_ACTUAL_RISK_GROWTH, 4,
                TaggingConstants.LABEL_ACTUAL_RISK_AGGRESSIVE, 5
            );

            // 分别取出该客户对应的实盘风险标签对应的分数 和 申报风险标签对应的分数
            int declaredLevelInt = riskLevelMap.getOrDefault(declaredRiskLevel, 0);
            int actualLevelInt = riskLevelMap.getOrDefault(actualRiskLabel, 0);
            // 根据申报与实盘的大小关系 打上风险诊断标签并保存
            if (declaredLevelInt > 0 && actualLevelInt > 0) {
                if (actualLevelInt > declaredLevelInt) diagnosisLabel = TaggingConstants.LABEL_DIAGNOSIS_OVERWEIGHT;
                else if (actualLevelInt < declaredLevelInt) diagnosisLabel = TaggingConstants.LABEL_DIAGNOSIS_UNDERWEIGHT;
                else diagnosisLabel = TaggingConstants.LABEL_DIAGNOSIS_MATCH;
            }
        }
        riskTags.add(new CustomerTagRelation(customerId, diagnosisLabel, TaggingConstants.CATEGORY_RISK_DIAGNOSIS));
        return riskTags;
    }


    // 为长持型计算 近期净资产流入/流出标签的辅助方法（提供给生成所有标签的方法使用）
    private CustomerTagRelation generateLongTermRecencyTag(Long customerId, List<FundTransaction> transactions) {
        LocalDateTime now = LocalDateTime.now();

        // 计算近3个月的净资产流入情况
        BigDecimal netPurchaseLast3Months = calculateNetPurchase(transactions, now.minusMonths(TaggingConstants.RECENCY_STAGNANT_MONTHS), now);
        if (netPurchaseLast3Months.compareTo(BigDecimal.ZERO) > 0) {
            return new CustomerTagRelation(customerId, TaggingConstants.LABEL_RECENCY_LONG_INVEST, TaggingConstants.CATEGORY_RECENCY); // 净资产流入
        }

        // 计算近6个月的净资产流入情况
        BigDecimal netPurchaseLast6Months = calculateNetPurchase(transactions, now.minusMonths(TaggingConstants.RECENCY_OUTFLOW_MONTHS), now);
        if (netPurchaseLast6Months.compareTo(BigDecimal.ZERO) >= 0) {
            return new CustomerTagRelation(customerId, TaggingConstants.LABEL_RECENCY_LONG_STAGNANT, TaggingConstants.CATEGORY_RECENCY); // 投入停滞
        } else {
            return new CustomerTagRelation(customerId, TaggingConstants.LABEL_RECENCY_LONG_OUTFLOW, TaggingConstants.CATEGORY_RECENCY);  // 资产流出
        }
    }

    // 计算n个月内 净资产累计情况的辅助方法
    private BigDecimal calculateNetPurchase(List<FundTransaction> transactions, LocalDateTime start, LocalDateTime end) {
        BigDecimal totalPurchase = transactions.stream()
                .filter(t -> "申购".equals(t.getTransactionType()) && !t.getTransactionTime().isBefore(start) && t.getTransactionTime().isBefore(end))
                .map(FundTransaction::getTransactionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalRedeem = transactions.stream()
                .filter(t -> "赎回".equals(t.getTransactionType()) && !t.getTransactionTime().isBefore(start) && t.getTransactionTime().isBefore(end))
                .map(FundTransaction::getTransactionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return totalPurchase.subtract(totalRedeem);
    }




    private String getAgeGroupTag(LocalDate birthDate) {
        int birthYear = birthDate.getYear();
        if (birthYear >= 2010) return "10后";
        if (birthYear >= 2000) return "00后";
        if (birthYear >= 1990) return "90后";
        if (birthYear >= 1980) return "80后";
        if (birthYear >= 1970) return "70后";
        if (birthYear >= 1960) return "60后";
        return "60前";
    }
}