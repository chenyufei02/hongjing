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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;import org.springframework.context.annotation.Lazy;

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

        System.out.println("【批量刷新完成】所有客户画像数据更新任务已完成！");
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



        // --- 计算阶段1：更新核心量化指标 (CustomerProfile) ： 持仓周期 总资产M 最近交易距离R 交易频率F---
        CustomerProfile profile = calculateAndSaveProfile(customer, holdings, transactions);

        // --- 计算阶段2：根据最新的指标和基础信息，生成所有分类标签 ：包含分类标签与指标标签 ---
        List<CustomerTagRelation> newTags = generateAllTags(customer, profile, latestAssessment, transactions);

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
     * 核心标签生成器：根据所有输入信息，产出完整的标签列表。
     */
    private List<CustomerTagRelation> generateAllTags(Customer customer, CustomerProfile profile, RiskAssessment assessment, List<FundTransaction> transactions) {
        List<CustomerTagRelation> tags = new ArrayList<>();
        Long customerId = customer.getId();

        // 1. 生成基础信息标签 (人口属性)
        if (customer.getGender() != null) tags.add(new CustomerTagRelation(customerId, customer.getGender(), TaggingConstants.CATEGORY_GENDER));
        if (customer.getOccupation() != null) tags.add(new CustomerTagRelation(customerId, customer.getOccupation(), TaggingConstants.CATEGORY_OCCUPATION));
        if (customer.getBirthDate() != null) tags.add(new CustomerTagRelation(customerId, getAgeGroupTag(customer.getBirthDate()), TaggingConstants.CATEGORY_AGE));
        String riskLevel = (assessment != null && assessment.getRiskLevel() != null) ? assessment.getRiskLevel() : "未知";
        tags.add(new CustomerTagRelation(customerId, riskLevel, TaggingConstants.CATEGORY_RISK_DECLARED));

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
        if (isLongTermHolder) {
            // -- 长期持有型客户的 R 和 F --
            // R: 行为标签 (根据净申购判断)
            tags.add(generateLongTermRecencyTag(customerId, transactions));
            // F: 习惯标签 (根据是否有定投)
            String freqTag = profile.getHasRegularInvestment() ? TaggingConstants.LABEL_FREQUENCY_LONG_REGULAR : TaggingConstants.LABEL_FREQUENCY_LONG_IRREGULAR;
            tags.add(new CustomerTagRelation(customerId, freqTag, TaggingConstants.CATEGORY_FREQUENCY));
        } else {
            // -- 短期交易型客户的 R 和 F --
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
     * 辅助方法：计算指定时间范围内的净申购额（总申购 - 总赎回）。
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