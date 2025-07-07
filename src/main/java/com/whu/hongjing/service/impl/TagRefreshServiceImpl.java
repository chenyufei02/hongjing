package com.whu.hongjing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.whu.hongjing.pojo.entity.*;
import com.whu.hongjing.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 客户画像刷新服务的核心实现类。
 * 负责统一计算和更新客户的所有画像数据，包括量化指标和分类标签。
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
    // 注入服务自身的代理对象，用于解决事务自调用失效的问题
    @Autowired
    private TagRefreshService self;

    /**
     * 【批量并行方法】刷新所有客户的画像数据。
     * 这是所有批量、定时任务的入口，采用了手动创建的高性能线程池。
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
        System.out.println(corePoolSize);

        // 1. 【新增】自定义线程工厂，为每个线程设置有意义的名称
        ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().setNameFormat("profile-refresh-thread-%d").build();

        ExecutorService executor = new ThreadPoolExecutor(
                corePoolSize,                 // 核心线程数
                corePoolSize * 5,             // 最大线程数
                60L,                          // 空闲线程存活时间
                TimeUnit.SECONDS,             // 时间单位
                new LinkedBlockingQueue<>(2048), // 任务队列，设置一个合理的容量
                namedThreadFactory,           // 【新增】使用我们自定义的线程工厂
                new ThreadPoolExecutor.CallerRunsPolicy() // 【新增】设置拒绝策略
        );

        try {
            for (Customer customer : allCustomers) {
                executor.submit(() -> {
                    try {
                        self.refreshTagsForCustomer(customer.getId());
                    } catch (Exception e) {
                        System.err.println("【批量刷新错误】处理客户 " + customer.getId() + " 时发生异常: " + e.getMessage());
                    }
                });
            }
        } finally {
            executor.shutdown();
        }

        try {
            if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
                System.err.println("【批量刷新警告】线程池在1小时内未能完全终止。");
            }
        } catch (InterruptedException e) {
            System.err.println("【批量刷新错误】等待线程池终止时被中断。");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("【批量刷新完成】所有客户画像数据更新任务已结束。");
    }

    /**
     * 【核心原子方法】为单个客户刷新所有画像数据（指标+标签）。
     * 这是所有实时、单体更新的入口（例如，交易发生后自动调用此方法）。
     * 此方法带有 @Transactional 注解，确保对单个客户的所有数据库操作要么全部成功，要么全部失败，保证了数据的一致性。
     *
     * @param customerId 要刷新画像的客户ID
     */
    @Override
    @Transactional
    public void refreshTagsForCustomer(Long customerId) {
        // 获取客戶的全部信息——用来计算人口属性标签
        Customer customer = customerService.getById(customerId);
        if (customer == null) return;
        // 获取该客户的全部持仓数据——用来计算实盘风险
        List<CustomerHolding> holdings = customerHoldingService.listByCustomerId(customerId);
        // 获取该客户的全部申报风险——用来计算持仓风险
        RiskAssessment latestAssessment = riskAssessmentService.getOne(
                new QueryWrapper<RiskAssessment>().eq("customer_id", customer.getId()).orderByDesc("assessment_date").last("LIMIT 1")
        );
        // 获取该客户的全部交易数据——用来计算交易行为标签RFM
        List<FundTransaction> transactions = fundTransactionService.list(
                new QueryWrapper<FundTransaction>().eq("customer_id", customerId)
        );

        // 1、计算分类标签
        List<CustomerTagRelation> newTags = calculateCategoricalTags(customer, latestAssessment);
        // 1、保存分类标签到数据库
        customerTagRelationService.remove(new QueryWrapper<CustomerTagRelation>().eq("customer_id", customerId));
        if (!newTags.isEmpty()) {
            customerTagRelationService.saveBatch(newTags);
        }

        // 2、计算量化的指标标签（RFM交易行为标签） 并将计算后的量化标签保存到数据库。
        calculateAndSaveProfile(customer, holdings, transactions);

    }

    /**
     * 私有辅助方法：计算一个客户的所有“分类”性质的标签。是固定属性类的标签 不会经常变更，因此都保存在此表
     */
    private List<CustomerTagRelation> calculateCategoricalTags(Customer customer, RiskAssessment assessment) {
        List<CustomerTagRelation> tags = new ArrayList<>();

        // 性别
        if (customer.getGender() != null) tags.add(new CustomerTagRelation(customer.getId(), customer.getGender(), "性别"));
        // 职业
        if (customer.getOccupation() != null) tags.add(new CustomerTagRelation(customer.getId(), customer.getOccupation(), "职业"));
        // 年龄分代
        if (customer.getBirthDate() != null) {
            tags.add(new CustomerTagRelation(customer.getId(), getAgeGroupTag(customer.getBirthDate()), "年龄分代"));
        }
        // 风险申报
        String statedRiskLevelName = (assessment != null && assessment.getRiskLevel() != null)
                ? assessment.getRiskLevel() : "未知";
        tags.add(new CustomerTagRelation(customer.getId(), statedRiskLevelName, "申报风险等级"));
        return tags;
    }

    /**
     * 私有辅助方法：计算并保存一个客户的所有核心“量化”数据。
     */
    private void calculateAndSaveProfile(Customer customer, List<CustomerHolding> holdings, List<FundTransaction> transactions) {
        // 获取或创建当前用户的量化指标profile数据
        CustomerProfile profile = customerProfileService.getById(customer.getId());
        if (profile == null) {
            profile = new CustomerProfile(customer.getId());
        }

        // 计算平均持仓天数 并设置到量化指标表里的 平均持仓天数 用来根据180判断交易型/长持型
        if (!holdings.isEmpty()) {
            long totalDaysSum = 0;
            int validHoldingsCount = 0;
            for (CustomerHolding holding : holdings) {
                FundTransaction firstPurchase = fundTransactionService.getOne(new QueryWrapper<FundTransaction>()
                        .eq("customer_id", customer.getId()).eq("fund_code", holding.getFundCode())
                        .eq("transaction_type", "申购").orderByAsc("transaction_time").last("LIMIT 1"));
                if (firstPurchase != null) {
                    totalDaysSum += Period.between(firstPurchase.getTransactionTime().toLocalDate(), LocalDate.now()).getDays();
                    validHoldingsCount++;
                }
            }
            if (validHoldingsCount > 0) {
                profile.setAvgHoldingDays((int) (totalDaysSum / validHoldingsCount));
            }
        }

        // 计算并设置总资产 M 值
        profile.setTotalMarketValue(
            holdings.stream().map(CustomerHolding::getMarketValue)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add)
        );

        // 计算并保存最近交易时间间隔 R值 和 90天交易频率F值 和定投行为判断
        if (!transactions.isEmpty()) {
            // 从交易表中取出最近交易时间
            Optional<LocalDateTime> lastTxTimeOpt = transactions.stream()
                .map(FundTransaction::getTransactionTime).max(LocalDateTime::compareTo);

            // 如果上次交易时间存在 就把最近交易日期间隔 R 设置为上次交易时间和当前时间的差值 并保存
            if (lastTxTimeOpt.isPresent()) {
                profile.setRecencyDays((int) ChronoUnit.DAYS.between(lastTxTimeOpt.get(), LocalDateTime.now()));
            }

            // 计算90天之前以后（也就是最近90天）的交易数据的次数 F 并保存
            profile.setFrequency90d((int) transactions.stream()
                .filter(tx -> tx.getTransactionTime().isAfter(LocalDateTime.now().minusDays(90))).count());

            // 计算过去一年内 是否有定投行为并保存
                // 将该用户在最近一年内的所有交易以月份为单位取一条数据 用来记录客户在哪些月份有过申购
            Map<YearMonth, Boolean> monthlyPurchase = transactions.stream()
                .filter(tx -> "申购".equals(tx.getTransactionType()) && tx.getTransactionTime().isAfter(LocalDateTime.now().minusYears(1)))
                .collect(Collectors.toMap(tx -> YearMonth.from(tx.getTransactionTime()), v -> true, (a, b) -> a));

            boolean hasRegularInvestment = false;
            YearMonth currentMonth = YearMonth.now();
                // 从当前月份-0开始判断012，一直到当前月份-10判断101112， 看是否有连续三个月定投
            for (int i = 0; i < 10; i++) {
                YearMonth start = currentMonth.minusMonths(i);
                if (monthlyPurchase.getOrDefault(start, false) &&
                        monthlyPurchase.getOrDefault(start.minusMonths(1), false) &&
                        monthlyPurchase.getOrDefault(start.minusMonths(2), false))
                {
                    // 只要上面连续三个月的申购判断值都成立为true 就立马设置为过去一年内曾经有过定投行为 并退出后续循环
                    hasRegularInvestment = true;
                    break;
                }
            }
            profile.setHasRegularInvestment(hasRegularInvestment);
        }
        customerProfileService.saveOrUpdate(profile);
    }









    /**
 * 私有辅助方法：根据出生日期（年份）计算年龄分代标签，逻辑永不过时。
 * * @param birthDate 客户的出生日期
 * @return 年代标签字符串，如 "80后"
 */
    private String getAgeGroupTag(LocalDate birthDate) {
        int birthYear = birthDate.getYear();
        if (birthYear >= 2010) return "10后";
        if (birthYear >= 2000) return "00后";
        if (birthYear >= 1990) return "90后";
        if (birthYear >= 1980) return "80后";
        if (birthYear >= 1970) return "70后";
        if (birthYear >= 1960) return "60后";
        return "60前"; // 为更早出生的客户提供一个标签
    }
}