package com.whu.hongjing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.whu.hongjing.constants.TaggingConstants; // 导入我们的常量中心
import com.whu.hongjing.pojo.entity.*;
import com.whu.hongjing.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Lazy;
import com.whu.hongjing.service.FundInfoService;

/**
 * 客户画像刷新服务的核心实现类。
 * 负责统一计算和更新客户的所有画像数据，包括量化指标和分类标签。
 * V2版本：全面重构，遵循“常量驱动”和“风格分类优先”的业务逻辑。
 */
@Service
public class TagRefreshServiceImpl implements TagRefreshService {

    // 注入所有需要用到的服务
    @Autowired
    private CustomerService customerService;
    @Autowired
    private RiskAssessmentService riskAssessmentService;
    @Autowired
    private CustomerHoldingService customerHoldingService;
    @Autowired
    private FundTransactionService fundTransactionService;
    @Autowired
    private CustomerProfileService customerProfileService;
    @Autowired
    private CustomerTagRelationService customerTagRelationService;
    @Autowired
    private FundInfoService fundInfoService;
    @Autowired
    @Lazy
    private TagRefreshService self;


    /**
     * 【批量并行方法】刷新所有客户的画像数据。
     */
    @Override
    public void refreshAllTagsAtomically() {
        List<Customer> allCustomers = customerService.list();
        if (allCustomers == null || allCustomers.isEmpty()) {
            System.out.println("【批量刷新】没有找到任何客户，任务结束。");
            return;
        }
        System.out.println("【批量刷新启动】准备为 " + allCustomers.size() + " 位客户并行更新画像数据...");

        int corePoolSize = Runtime.getRuntime().availableProcessors();
        ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().setNameFormat("profile-refresh-thread-%d").build();
        ExecutorService executor = new ThreadPoolExecutor(
                corePoolSize,
                corePoolSize * 5,
                1200L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(20480),
                namedThreadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy());
        try {
            for (Customer customer : allCustomers) {
                executor.submit(() -> {
                    try {
                        self.refreshTagsForCustomer(customer.getId()); // 这里提交的任务是针对单个客户的完整计算和标签生成
                    } catch (Exception e) {
                        System.err.println("【批量刷新错误】处理客户 " + customer.getId() + " 时发生异常: " + e.getMessage());
                        e.printStackTrace(); // 打印详细堆栈
                    }
                });
            }
        } finally {
            executor.shutdown();
        }

        // 不能终止和提前终止的异常处理
        try {
            if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
                System.err.println("【批量刷新警告】线程池在1小时内未能完全终止。");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            System.err.println("【批量刷新错误】等待线程池终止时被中断。");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("【批量刷新完成】所有客户指标数据生成及画像标签更新任务已完成！");
    }


    /**
     * 【核心原子方法】为单个客户刷新所有画像数据（指标+标签）。
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED) // 【核心修改】指定事务隔离级别
    public void refreshTagsForCustomer(Long customerId) {
        // --- 准备阶段：一次性获取所有需要的数据 ---
        Customer customer = customerService.getById(customerId);
        if (customer == null) return;

        // 持仓数据 用于计算持仓长持型/交易型、持仓集中度、总资产、实盘风险等
        List<CustomerHolding> holdings = customerHoldingService.listByCustomerId(customerId);
        // 交易数据 用于计算R F
        List<FundTransaction> transactions = fundTransactionService.list(new QueryWrapper<FundTransaction>().eq("customer_id", customerId));
        // 风险申报数据
        RiskAssessment latestAssessment = riskAssessmentService.getOne(
                new QueryWrapper<RiskAssessment>().eq("customer_id", customer.getId()).orderByDesc("assessment_date").last("LIMIT 1"));

        // 准备基金风险数据，用于计算实盘风险
        Map<String, FundInfo> fundInfoMap = fundInfoService.list().stream()
                .collect(Collectors.toMap(FundInfo::getFundCode, Function.identity()));




        // --- 计算阶段1：更新核心量化指标 (CustomerProfile) ： 持仓周期 总资产M 最近交易距离R 交易频率F---
        CustomerProfile profile = calculateAndSaveProfile(customer, holdings, transactions);

        // --- 计算阶段2：根据最新的指标和基础信息，生成所有分类标签 ：包含分类标签与指标标签 ---
        List<CustomerTagRelation> newTags = generateAllTags(customer, profile, latestAssessment, transactions, holdings, fundInfoMap);

        // --- 持久化阶段：将新标签覆盖式写入数据库 ---
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
                // 找到该基金的最早一笔申购记录
                // 【代码优化】使用 Comparator.comparing 优化
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

            // R: 最近交易距今天数
            if (lastTxTimeOpt.isPresent()) {
                profile.setRecencyDays((int) ChronoUnit.DAYS.between(lastTxTimeOpt.get(), LocalDateTime.now()));
            } else { profile.setRecencyDays(null); }

            // F[针对交易]: 90天内交易次数
            profile.setFrequency90d((int) transactions.stream()
                .filter(tx -> tx.getTransactionTime().isAfter(LocalDateTime.now().minusDays(90))).count());

            // F[针对长持]: 是否有定投行为 (近一年内连续3个月有申购)
            profile.setHasRegularInvestment(checkForRegularInvestment(transactions));
        } else {
            profile.setRecencyDays(null);
            profile.setFrequency90d(0);
            profile.setHasRegularInvestment(false);
        }

        // 4. 计算盈亏信息 (如果需要)
        // 此处可以加入之前讨论的 total_profit 和 profit_rate 的计算逻辑

        customerProfileService.saveOrUpdate(profile);
        return profile;
    }

    /**
     * 核心标签生成器：根据所有输入信息，产出完整的标签列表。包括：性别 职业 年龄 申报风险 实盘风险 风险诊断 M R F
     */
    private List<CustomerTagRelation> generateAllTags(
            Customer customer, CustomerProfile profile,
             RiskAssessment assessment, List<FundTransaction> transactions,
            List<CustomerHolding> holdings, Map<String, FundInfo> fundInfoMap)
    {
        List<CustomerTagRelation> tags = new ArrayList<>();
        Long customerId = customer.getId();

        // 1. 生成基础信息标签 (人口属性)
        if (customer.getGender() != null) tags.add(new CustomerTagRelation(customerId, customer.getGender(), TaggingConstants.CATEGORY_GENDER));
        if (customer.getOccupation() != null) tags.add(new CustomerTagRelation(customerId, customer.getOccupation(), TaggingConstants.CATEGORY_OCCUPATION));
        if (customer.getBirthDate() != null) tags.add(new CustomerTagRelation(customerId, getAgeGroupTag(customer.getBirthDate()), TaggingConstants.CATEGORY_AGE));

        // 1.5 获取申报风险等级，获取实盘风险和风险诊断标签（暂时保存在内存）
        String declaredRiskLevel = (assessment != null && assessment.getRiskLevel() != null) ? assessment.getRiskLevel() : "未知";
        tags.add(new CustomerTagRelation(customerId, declaredRiskLevel, TaggingConstants.CATEGORY_RISK_DECLARED));

            // 计算并生成实盘风险和风险诊断标签
        tags.addAll(calculateAndGenerateRiskTags(customerId, holdings, fundInfoMap, declaredRiskLevel));


        // 2. 生成资产规模 (M) 标签
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

        // 3. 生成持仓风格标签，并决定后续R,F的计算逻辑
        boolean isLongTermHolder = profile.getAvgHoldingDays() != null && profile.getAvgHoldingDays() > TaggingConstants.HOLDING_STYLE_THRESHOLD_DAYS;
        if (isLongTermHolder) {
            tags.add(new CustomerTagRelation(customerId, TaggingConstants.LABEL_STYLE_LONG_TERM, TaggingConstants.CATEGORY_STYLE));
        } else {
            tags.add(new CustomerTagRelation(customerId, TaggingConstants.LABEL_STYLE_SHORT_TERM, TaggingConstants.CATEGORY_STYLE));
        }

        // 4. 根据风格，生成不同的 R 和 F 标签
            // -- 如果是长持型客户 的 R 和 F --
        if (isLongTermHolder) {
            // -- 长期持有型客户的 R 和 F --
            // R: 行为标签 (根据净申购判断)
            tags.add(generateLongTermRecencyTag(customerId, transactions));
            // F: 习惯标签 (根据是否有定投)
            String freqTag = profile.getHasRegularInvestment() ? TaggingConstants.LABEL_FREQUENCY_LONG_REGULAR : TaggingConstants.LABEL_FREQUENCY_LONG_IRREGULAR;
            tags.add(new CustomerTagRelation(customerId, freqTag, TaggingConstants.CATEGORY_FREQUENCY));
        } else {
            // -- 如果是交易型客户的 R 和 F --
            // R: 行为标签 (根据最近交易天数判断)
            if (profile.getRecencyDays() != null) {
                int rDays = profile.getRecencyDays();
                if (rDays <= TaggingConstants.RECENCY_ACTIVE_DAYS) {
                    tags.add(new CustomerTagRelation(customerId, TaggingConstants.LABEL_RECENCY_SHORT_ACTIVE, TaggingConstants.CATEGORY_RECENCY));
                } else if (rDays <= TaggingConstants.RECENCY_SLEEP_DAYS) {
                    tags.add(new CustomerTagRelation(customerId, TaggingConstants.LABEL_RECENCY_SHORT_SLEEP, TaggingConstants.CATEGORY_RECENCY));
                } else {
                    tags.add(new CustomerTagRelation(customerId, TaggingConstants.LABEL_RECENCY_SHORT_LOST, TaggingConstants.CATEGORY_RECENCY));
                }
            }
            // F: 习惯标签 (根据90天内交易次数判断)
            if (profile.getFrequency90d() != null) {
                int fCount = profile.getFrequency90d();
                if (fCount > TaggingConstants.FREQUENCY_HIGH_COUNT) {
                    tags.add(new CustomerTagRelation(customerId, TaggingConstants.LABEL_FREQUENCY_SHORT_HIGH, TaggingConstants.CATEGORY_FREQUENCY));
                } else if (fCount > TaggingConstants.FREQUENCY_LOW_COUNT) {
                    tags.add(new CustomerTagRelation(customerId, TaggingConstants.LABEL_FREQUENCY_SHORT_MEDIUM, TaggingConstants.CATEGORY_FREQUENCY));
                } else {
                    tags.add(new CustomerTagRelation(customerId, TaggingConstants.LABEL_FREQUENCY_SHORT_LOW, TaggingConstants.CATEGORY_FREQUENCY));
                }
            }
        }

        return tags;
    }


    /**
     * 计算实盘风险和风险诊断标签
     */
    private List<CustomerTagRelation> calculateAndGenerateRiskTags(Long customerId, List<CustomerHolding> holdings, Map<String, FundInfo> fundInfoMap, String declaredRiskLevel) {
        List<CustomerTagRelation> riskTags = new ArrayList<>();

        if (holdings == null || holdings.isEmpty() || fundInfoMap.isEmpty()) {
            riskTags.add(new CustomerTagRelation(customerId, TaggingConstants.LABEL_ACTUAL_RISK_UNKNOWN, TaggingConstants.CATEGORY_RISK_ACTUAL));
            riskTags.add(new CustomerTagRelation(customerId, TaggingConstants.LABEL_DIAGNOSIS_UNKNOWN, TaggingConstants.CATEGORY_RISK_DIAGNOSIS));
            return riskTags;
        }

        BigDecimal totalMarketValue = holdings.stream()
                .map(CustomerHolding::getMarketValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalMarketValue.compareTo(BigDecimal.ZERO) <= 0) {
            riskTags.add(new CustomerTagRelation(customerId, TaggingConstants.LABEL_ACTUAL_RISK_UNKNOWN, TaggingConstants.CATEGORY_RISK_ACTUAL));
            riskTags.add(new CustomerTagRelation(customerId, TaggingConstants.LABEL_DIAGNOSIS_UNKNOWN, TaggingConstants.CATEGORY_RISK_DIAGNOSIS));
            return riskTags;
        }

        // 1. 计算加权平均风险分
        BigDecimal weightedRiskSum = BigDecimal.ZERO;
        for (CustomerHolding holding : holdings) {
            FundInfo fund = fundInfoMap.get(holding.getFundCode());
            if (fund != null && fund.getRiskScore() != null && holding.getMarketValue() != null) {
                BigDecimal fundRisk = new BigDecimal(fund.getRiskScore());
                weightedRiskSum = weightedRiskSum.add(holding.getMarketValue().multiply(fundRisk));
            }
        }
        double actualRiskScore = weightedRiskSum.divide(totalMarketValue, 2, RoundingMode.HALF_UP).doubleValue();

        // 2. 生成实盘风险标签
        String actualRiskLabel = TaggingConstants.LABEL_ACTUAL_RISK_UNKNOWN;
        if (actualRiskScore >= TaggingConstants.ACTUAL_RISK_THRESHOLD_AGGRESSIVE) {
            actualRiskLabel = TaggingConstants.LABEL_ACTUAL_RISK_AGGRESSIVE;
        } else if (actualRiskScore >= TaggingConstants.ACTUAL_RISK_THRESHOLD_GROWTH) {
            actualRiskLabel = TaggingConstants.LABEL_ACTUAL_RISK_GROWTH;
        } else if (actualRiskScore >= TaggingConstants.ACTUAL_RISK_THRESHOLD_BALANCED) {
            actualRiskLabel = TaggingConstants.LABEL_ACTUAL_RISK_BALANCED;
        } else if (actualRiskScore >= TaggingConstants.ACTUAL_RISK_THRESHOLD_STEADY) {
            actualRiskLabel = TaggingConstants.LABEL_ACTUAL_RISK_STEADY;
        } else if (actualRiskScore >0 && actualRiskScore < TaggingConstants.ACTUAL_RISK_THRESHOLD_STEADY){
            actualRiskLabel = TaggingConstants.LABEL_ACTUAL_RISK_CONSERVATIVE;
        }
        riskTags.add(new CustomerTagRelation(customerId, actualRiskLabel, TaggingConstants.CATEGORY_RISK_ACTUAL));


        // 3. 生成风险诊断标签
        String diagnosisLabel = TaggingConstants.LABEL_DIAGNOSIS_UNKNOWN;
        if (!"未知".equals(declaredRiskLevel) && !TaggingConstants.LABEL_ACTUAL_RISK_UNKNOWN.equals(actualRiskLabel)) {
            // 为了比较，我们给风险等级赋予数值
            Map<String, Integer> riskLevelMap = Map.of(
                    "保守型", 1, TaggingConstants.LABEL_ACTUAL_RISK_CONSERVATIVE, 1,
                    "稳健型", 2, TaggingConstants.LABEL_ACTUAL_RISK_STEADY, 2,
                    "平衡型", 3, TaggingConstants.LABEL_ACTUAL_RISK_BALANCED, 3,
                    "成长型", 4, TaggingConstants.LABEL_ACTUAL_RISK_GROWTH, 4,
                    "激进型", 5, TaggingConstants.LABEL_ACTUAL_RISK_AGGRESSIVE, 5
            );

            int declaredLevelInt = riskLevelMap.getOrDefault(declaredRiskLevel, 0);
            int actualLevelInt = riskLevelMap.getOrDefault(actualRiskLabel, 0);

            if (declaredLevelInt > 0 && actualLevelInt > 0) {
                if (actualLevelInt > declaredLevelInt) {
                    diagnosisLabel = TaggingConstants.LABEL_DIAGNOSIS_OVERWEIGHT;
                } else if (actualLevelInt < declaredLevelInt) {
                    diagnosisLabel = TaggingConstants.LABEL_DIAGNOSIS_UNDERWEIGHT;
                } else {
                    diagnosisLabel = TaggingConstants.LABEL_DIAGNOSIS_MATCH;
                }
            }
        }
        riskTags.add(new CustomerTagRelation(customerId, diagnosisLabel, TaggingConstants.CATEGORY_RISK_DIAGNOSIS));

        return riskTags;
    }

    /**
     * 为“长期持有型”客户生成“近期活跃度(R)”标签。
     * 逻辑：通过即时计算近期的净申购额来判断。
     */
    private CustomerTagRelation generateLongTermRecencyTag(Long customerId, List<FundTransaction> transactions) {
        LocalDateTime now = LocalDateTime.now();
        // 计算近3个月的净申购
        BigDecimal netPurchaseLast3Months = calculateNetPurchase(transactions, now.minusMonths(TaggingConstants.RECENCY_STAGNANT_MONTHS), now);
        if (netPurchaseLast3Months.compareTo(BigDecimal.ZERO) > 0) {
            return new CustomerTagRelation(customerId, TaggingConstants.LABEL_RECENCY_LONG_INVEST, TaggingConstants.CATEGORY_RECENCY);
        }

        // 计算近6个月的净申购
        BigDecimal netPurchaseLast6Months = calculateNetPurchase(transactions, now.minusMonths(TaggingConstants.RECENCY_OUTFLOW_MONTHS), now);
        if (netPurchaseLast6Months.compareTo(BigDecimal.ZERO) >= 0) {
            // 如果最近3个月没有净申购，但最近6个月的净申购大于等于0，则认为是“投入停滞”
            return new CustomerTagRelation(customerId, TaggingConstants.LABEL_RECENCY_LONG_STAGNANT, TaggingConstants.CATEGORY_RECENCY);
        } else {
            // 如果最近6个月的净申购是负数，则认为是“资产流出”
            return new CustomerTagRelation(customerId, TaggingConstants.LABEL_RECENCY_LONG_OUTFLOW, TaggingConstants.CATEGORY_RECENCY);
        }
    }

    /**
     * 辅助方法：计算指定时间范围内的净申购额（总申购 - 总赎回） 。
     */
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

    /**
     * 辅助方法：检查近一年是否有连续3个月的定投行为。
     */
    private boolean checkForRegularInvestment(List<FundTransaction> transactions) {
        Map<YearMonth, Boolean> monthlyPurchaseMap = transactions.stream()
            .filter(tx -> "申购".equals(tx.getTransactionType()) && tx.getTransactionTime().isAfter(LocalDateTime.now().minusYears(1)))
            .collect(Collectors.toMap(
                tx -> YearMonth.from(tx.getTransactionTime()),
                v -> true,
                (existing, replacement) -> existing // 如果一个月有多笔，保留第一个即可
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

    /**
     * 辅助方法：根据出生日期计算年龄分代标签。
     */
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