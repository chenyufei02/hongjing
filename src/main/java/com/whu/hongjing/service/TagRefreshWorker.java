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
 * 这是一个专门负责执行单个客户标签刷新事务的组件。
 * 将其独立出来，可以解决在异步线程中事务不生效的问题，并让职责更清晰。
 */
@Component
public class TagRefreshWorker {

    @Autowired private CustomerHoldingService customerHoldingService;
    @Autowired private FundTransactionService fundTransactionService;
    @Autowired private CustomerProfileService customerProfileService;
    @Autowired private CustomerTagRelationService customerTagRelationService;
    @Autowired private RiskAssessmentService riskAssessmentService;

    /**
     * 执行数据库操作的核心方法。
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

        // --- 计算阶段1：更新核心量化指标 (CustomerProfile) ---
        CustomerProfile profile = calculateAndSaveProfile(customer, holdings, transactions);

        // --- 计算阶段2：生成所有分类标签 ---
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
        CustomerProfile profile = customerProfileService.getById(customer.getId());
        if (profile == null) {
            profile = new CustomerProfile(customer.getId());
        }

        // 1. 计算平均持仓天数
        if (!holdings.isEmpty()) {
            long totalDaysSum = 0;
            int validHoldingsCount = 0;
            for (CustomerHolding holding : holdings) {
                Optional<FundTransaction> firstPurchaseOpt = transactions.stream()
                        .filter(tx -> tx.getFundCode().equals(holding.getFundCode()) && "申购".equals(tx.getTransactionType()))
                        .min(Comparator.comparing(FundTransaction::getTransactionTime));

                if (firstPurchaseOpt.isPresent()) {
                    totalDaysSum += ChronoUnit.DAYS.between(firstPurchaseOpt.get().getTransactionTime().toLocalDate(), LocalDate.now());
                    validHoldingsCount++;
                }
            }
            profile.setAvgHoldingDays(validHoldingsCount > 0 ? (int) (totalDaysSum / validHoldingsCount) : 0);
        } else {
            profile.setAvgHoldingDays(0);
        }

        // 2. 计算总市值 (M)
        profile.setTotalMarketValue(
            holdings.stream().map(CustomerHolding::getMarketValue)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add)
        );

        // 3. 计算 R, F 和定投行为
        if (!transactions.isEmpty()) {
            Optional<LocalDateTime> lastTxTimeOpt = transactions.stream()
                .map(FundTransaction::getTransactionTime).max(LocalDateTime::compareTo);

            profile.setRecencyDays(lastTxTimeOpt.map(localDateTime -> (int) ChronoUnit.DAYS.between(localDateTime, LocalDateTime.now())).orElse(null));

            profile.setFrequency90d((int) transactions.stream()
                .filter(tx -> tx.getTransactionTime().isAfter(LocalDateTime.now().minusDays(90))).count());

            profile.setHasRegularInvestment(checkForRegularInvestment(transactions));
        } else {
            profile.setRecencyDays(null);
            profile.setFrequency90d(0);
            profile.setHasRegularInvestment(false);
        }

        customerProfileService.saveOrUpdate(profile);
        return profile;
    }

    /**
     * 核心标签生成器：确保所有标签分类都严格使用常量
     */
    private List<CustomerTagRelation> generateAllTags(Customer customer, CustomerProfile profile, RiskAssessment assessment, List<FundTransaction> transactions, List<CustomerHolding> holdings, Map<String, FundInfo> fundInfoMap) {
        List<CustomerTagRelation> tags = new ArrayList<>();
        Long customerId = customer.getId();

        if (customer.getGender() != null) tags.add(new CustomerTagRelation(customerId, customer.getGender(), TaggingConstants.CATEGORY_GENDER));
        if (customer.getOccupation() != null) tags.add(new CustomerTagRelation(customerId, customer.getOccupation(), TaggingConstants.CATEGORY_OCCUPATION));
        if (customer.getBirthDate() != null) tags.add(new CustomerTagRelation(customerId, getAgeGroupTag(customer.getBirthDate()), TaggingConstants.CATEGORY_AGE));

        String declaredRiskLevel = (assessment != null && assessment.getRiskLevel() != null) ? assessment.getRiskLevel() : "未知";
        tags.add(new CustomerTagRelation(customerId, declaredRiskLevel, TaggingConstants.CATEGORY_RISK_DECLARED));
        tags.addAll(calculateAndGenerateRiskTags(customerId, holdings, fundInfoMap, declaredRiskLevel));

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

        boolean isLongTermHolder = profile.getAvgHoldingDays() != null && profile.getAvgHoldingDays() > TaggingConstants.HOLDING_STYLE_THRESHOLD_DAYS;
        if (isLongTermHolder) {
            tags.add(new CustomerTagRelation(customerId, TaggingConstants.LABEL_STYLE_LONG_TERM, TaggingConstants.CATEGORY_STYLE));
        } else {
            tags.add(new CustomerTagRelation(customerId, TaggingConstants.LABEL_STYLE_SHORT_TERM, TaggingConstants.CATEGORY_STYLE));
        }

        if (isLongTermHolder) {
            tags.add(generateLongTermRecencyTag(customerId, transactions));
            String freqTag = profile.getHasRegularInvestment() ? TaggingConstants.LABEL_FREQUENCY_LONG_REGULAR : TaggingConstants.LABEL_FREQUENCY_LONG_IRREGULAR;
            tags.add(new CustomerTagRelation(customerId, freqTag, TaggingConstants.CATEGORY_FREQUENCY));
        } else {
             if (profile.getRecencyDays() != null) {
                int rDays = profile.getRecencyDays();
                String recencyTag;
                if (rDays <= TaggingConstants.RECENCY_ACTIVE_DAYS) recencyTag = TaggingConstants.LABEL_RECENCY_SHORT_ACTIVE;
                else if (rDays <= TaggingConstants.RECENCY_SLEEP_DAYS) recencyTag = TaggingConstants.LABEL_RECENCY_SHORT_SLEEP;
                else recencyTag = TaggingConstants.LABEL_RECENCY_SHORT_LOST;
                tags.add(new CustomerTagRelation(customerId, recencyTag, TaggingConstants.CATEGORY_RECENCY));
            }
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

    private List<CustomerTagRelation> calculateAndGenerateRiskTags(Long customerId, List<CustomerHolding> holdings, Map<String, FundInfo> fundInfoMap, String declaredRiskLevel) {
        List<CustomerTagRelation> riskTags = new ArrayList<>();
        if (holdings == null || holdings.isEmpty()) {
            riskTags.add(new CustomerTagRelation(customerId, TaggingConstants.LABEL_ACTUAL_RISK_UNKNOWN, TaggingConstants.CATEGORY_RISK_ACTUAL));
            riskTags.add(new CustomerTagRelation(customerId, TaggingConstants.LABEL_DIAGNOSIS_UNKNOWN, TaggingConstants.CATEGORY_RISK_DIAGNOSIS));
            return riskTags;
        }
        BigDecimal totalMarketValue = holdings.stream().map(CustomerHolding::getMarketValue).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalMarketValue.compareTo(BigDecimal.ZERO) <= 0) {
            riskTags.add(new CustomerTagRelation(customerId, TaggingConstants.LABEL_ACTUAL_RISK_UNKNOWN, TaggingConstants.CATEGORY_RISK_ACTUAL));
            riskTags.add(new CustomerTagRelation(customerId, TaggingConstants.LABEL_DIAGNOSIS_UNKNOWN, TaggingConstants.CATEGORY_RISK_DIAGNOSIS));
            return riskTags;
        }
        BigDecimal weightedRiskSum = BigDecimal.ZERO;
        for (CustomerHolding holding : holdings) {
            FundInfo fund = fundInfoMap.get(holding.getFundCode());
            if (fund != null && fund.getRiskScore() != null && holding.getMarketValue() != null) {
                weightedRiskSum = weightedRiskSum.add(holding.getMarketValue().multiply(new BigDecimal(fund.getRiskScore())));
            }
        }
        double actualRiskScore = weightedRiskSum.divide(totalMarketValue, 2, RoundingMode.HALF_UP).doubleValue();
        String actualRiskLabel;
        if (actualRiskScore >= TaggingConstants.ACTUAL_RISK_THRESHOLD_AGGRESSIVE) actualRiskLabel = TaggingConstants.LABEL_ACTUAL_RISK_AGGRESSIVE;
        else if (actualRiskScore >= TaggingConstants.ACTUAL_RISK_THRESHOLD_GROWTH) actualRiskLabel = TaggingConstants.LABEL_ACTUAL_RISK_GROWTH;
        else if (actualRiskScore >= TaggingConstants.ACTUAL_RISK_THRESHOLD_BALANCED) actualRiskLabel = TaggingConstants.LABEL_ACTUAL_RISK_BALANCED;
        else if (actualRiskScore >= TaggingConstants.ACTUAL_RISK_THRESHOLD_STEADY) actualRiskLabel = TaggingConstants.LABEL_ACTUAL_RISK_STEADY;
        else actualRiskLabel = TaggingConstants.LABEL_ACTUAL_RISK_CONSERVATIVE;
        riskTags.add(new CustomerTagRelation(customerId, actualRiskLabel, TaggingConstants.CATEGORY_RISK_ACTUAL));

        String diagnosisLabel = TaggingConstants.LABEL_DIAGNOSIS_UNKNOWN;
        if (!"未知".equals(declaredRiskLevel)) {
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
            int declaredLevelInt = riskLevelMap.getOrDefault(declaredRiskLevel, 0);
            int actualLevelInt = riskLevelMap.getOrDefault(actualRiskLabel, 0);
            if (declaredLevelInt > 0 && actualLevelInt > 0) {
                if (actualLevelInt > declaredLevelInt) diagnosisLabel = TaggingConstants.LABEL_DIAGNOSIS_OVERWEIGHT;
                else if (actualLevelInt < declaredLevelInt) diagnosisLabel = TaggingConstants.LABEL_DIAGNOSIS_UNDERWEIGHT;
                else diagnosisLabel = TaggingConstants.LABEL_DIAGNOSIS_MATCH;
            }
        }
        riskTags.add(new CustomerTagRelation(customerId, diagnosisLabel, TaggingConstants.CATEGORY_RISK_DIAGNOSIS));
        return riskTags;
    }

    private CustomerTagRelation generateLongTermRecencyTag(Long customerId, List<FundTransaction> transactions) {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal netPurchaseLast3Months = calculateNetPurchase(transactions, now.minusMonths(TaggingConstants.RECENCY_STAGNANT_MONTHS), now);
        if (netPurchaseLast3Months.compareTo(BigDecimal.ZERO) > 0) {
            return new CustomerTagRelation(customerId, TaggingConstants.LABEL_RECENCY_LONG_INVEST, TaggingConstants.CATEGORY_RECENCY);
        }
        BigDecimal netPurchaseLast6Months = calculateNetPurchase(transactions, now.minusMonths(TaggingConstants.RECENCY_OUTFLOW_MONTHS), now);
        if (netPurchaseLast6Months.compareTo(BigDecimal.ZERO) >= 0) {
            return new CustomerTagRelation(customerId, TaggingConstants.LABEL_RECENCY_LONG_STAGNANT, TaggingConstants.CATEGORY_RECENCY);
        } else {
            return new CustomerTagRelation(customerId, TaggingConstants.LABEL_RECENCY_LONG_OUTFLOW, TaggingConstants.CATEGORY_RECENCY);
        }
    }

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

    // 计算并判断定投行为的方法
    private boolean checkForRegularInvestment(List<FundTransaction> transactions) {
        Map<YearMonth, Boolean> monthlyPurchaseMap = transactions.stream()
            .filter(tx -> "申购".equals(tx.getTransactionType()) && tx.getTransactionTime().isAfter(LocalDateTime.now().minusYears(1)))
            .collect(Collectors.toMap(
                tx -> YearMonth.from(tx.getTransactionTime()),
                v -> true,
                (existing, replacement) -> existing
            ));
        YearMonth currentMonth = YearMonth.now();
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