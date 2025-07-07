package com.whu.hongjing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    @Autowired
    private TagRefreshService self;

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
        // 1. 获取该客户进行画像计算所需的所有基础数据
        Customer customer = customerService.getById(customerId);
        if (customer == null) {
            // 如果客户不存在，直接返回，不做任何操作
            return;
        }

        List<CustomerHolding> holdings = customerHoldingService.listByCustomerId(customerId);
        List<FundTransaction> transactions = fundTransactionService.list(
                new QueryWrapper<FundTransaction>().eq("customer_id", customerId)
        );
        RiskAssessment latestAssessment = riskAssessmentService.getOne(
                new QueryWrapper<RiskAssessment>().eq("customer_id", customer.getId()).orderByDesc("assessment_date").last("LIMIT 1")
        );

        // 2. 调用私有方法，计算并保存该客户的核心“量化指标”到 customer_profile 表
        calculateAndSaveProfile(customer, holdings, transactions);

        // 3. 调用私有方法，计算该客户的所有“分类标签”
        List<CustomerTagRelation> newTags = calculateCategoricalTags(customer, latestAssessment);

        // 4. 更新数据库中的分类标签（采用“先删后增”策略，确保数据最新）
        customerTagRelationService.remove(new QueryWrapper<CustomerTagRelation>().eq("customer_id", customerId));
        if (!newTags.isEmpty()) {
            customerTagRelationService.saveBatch(newTags);
        }
    }

    /**
     * 【批量并行方法】刷新所有客户的画像数据。
     * 这是所有批量、定时任务的入口（例如，每日凌晨的全量刷新任务）。
     */
    @Override
    public void refreshAllTagsAtomically() {
        List<Customer> allCustomers = customerService.list();
        // 创建一个信号量，只允许最多200个线程同时访问数据库
        Semaphore dbSemaphore = new Semaphore(200);
        System.out.println("【批量刷新启动】准备为 " + allCustomers.size() + " 位客户并行更新画像数据...");

        // 【!!! 终极方案 !!!】
        // 不再使用 parallelStream，而是创建一个“为每个任务都分配一个新虚拟线程”的专用执行器
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            // 遍历所有客户，为每一个客户的刷新任务都提交到执行器中
            // 执行器会为这成百上千个任务，都创建一个极其轻量的虚拟线程去执行
            for (Customer customer : allCustomers) {
                executor.submit(() -> {
                    try {
                        dbSemaphore.acquire(); // 在访问数据库前，先获取一个“许可”
                        // 在虚拟线程中，调用我们功能完整的、带事务的单体刷新方法
                        self.refreshTagsForCustomer(customer.getId());
                    } catch (Exception e) {
                        System.err.println("【批量刷新错误】处理客户 " + customer.getId() + " 时发生异常: " + e.getMessage());
                    } finally {
                        dbSemaphore.release(); // 无论成功失败，一定要释放“许可”
                    }
                });
            }
            // try-with-resources 结构会自动关闭执行器，等待所有任务完成
        }

        System.out.println("【批量刷新完成】所有客户画像数据更新任务已结束。");
    }

    /**
     * 私有辅助方法：计算并保存一个客户的所有核心量化指标。
     * @param customer     客户实体
     * @param holdings     该客户的所有持仓记录
     * @param transactions 该客户的所有交易记录
     */
    private void calculateAndSaveProfile(Customer customer, List<CustomerHolding> holdings, List<FundTransaction> transactions) {
        // 尝试从数据库获取已有的profile记录，如果没有则创建一个新的
        CustomerProfile profile = customerProfileService.getById(customer.getId());
        if (profile == null) {
            profile = new CustomerProfile(customer.getId());
        }

        // --- 计算 M (Monetary): 总持仓市值 ---
        profile.setTotalMarketValue(
            holdings.stream().map(CustomerHolding::getMarketValue)
                    .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add)
        );

        // --- 计算 平均持仓天数 ---
        if (!holdings.isEmpty()) {
            // 【!!! 线程安全解惑 !!!】
            // 这两个变量是在 calculateAndSaveProfile 方法内部定义的局部变量。
            // 当 refreshAllTagsAtomically() 使用多线程调用此方法时，每个线程都会在自己的线程栈中创建一套全新的、
            // 独立的 totalDaysSum 和 validHoldingsCount。它们之间天然隔离，互不干扰。
            // 因此，这里使用普通的 long 和 int 是100%线程安全的，无需使用 AtomicLong。
            long totalDaysSum = 0;
            int validHoldingsCount = 0;

            // 使用经典的 for-each 循环，逻辑最清晰、最易读
            for (CustomerHolding holding : holdings) {
                // 为客户的每一笔持仓，找到其最早的申购记录，以确定持仓起始日
                FundTransaction firstPurchase = fundTransactionService.getOne(new QueryWrapper<FundTransaction>()
                        .eq("customer_id", customer.getId()).eq("fund_code", holding.getFundCode())
                        .eq("transaction_type", "申购").orderByAsc("transaction_time").last("LIMIT 1"));

                if (firstPurchase != null) {
                    // 计算从首次购买到今天的天数
                    long days = Period.between(firstPurchase.getTransactionTime().toLocalDate(), LocalDate.now()).getDays();
                    // 直接累加这些普通的局部变量
                    totalDaysSum += days;
                    validHoldingsCount++;
                }
            }

            // 计算平均值并存入profile对象
            if (validHoldingsCount > 0) {
                profile.setAvgHoldingDays((int) (totalDaysSum / validHoldingsCount));
            }
        }

        if (!transactions.isEmpty()) {
            // --- 计算 R (Recency): 最近交易距今天数 ---
            Optional<LocalDateTime> lastTxTimeOpt = transactions.stream()
                .map(FundTransaction::getTransactionTime).max(LocalDateTime::compareTo);
            if (lastTxTimeOpt.isPresent()) {
                profile.setRecencyDays((int) ChronoUnit.DAYS.between(lastTxTimeOpt.get(), LocalDateTime.now()));
            }

            // --- 计算 F (Frequency): 90天内交易次数 ---
            profile.setFrequency90d((int) transactions.stream()
                .filter(tx -> tx.getTransactionTime().isAfter(LocalDateTime.now().minusDays(90))).count());

            // --- 计算 F (定投行为) ---
            // 1. 筛选出过去一年内所有申购的月份
            Map<YearMonth, Boolean> monthlyPurchase = transactions.stream()
                .filter(tx -> "申购".equals(tx.getTransactionType()) && tx.getTransactionTime().isAfter(LocalDateTime.now().minusYears(1)))
                .collect(Collectors.toMap(tx -> YearMonth.from(tx.getTransactionTime()), v -> true, (a, b) -> a));

            // 2. 检查是否存在连续三个月都有申购记录
            boolean hasRegularInvestment = false;
            YearMonth currentMonth = YearMonth.now();
            for (int i = 0; i < 10; i++) { // 从当月开始，向前滑动窗口检查
                YearMonth start = currentMonth.minusMonths(i);
                if (monthlyPurchase.getOrDefault(start, false) &&
                        monthlyPurchase.getOrDefault(start.minusMonths(1), false) &&
                        monthlyPurchase.getOrDefault(start.minusMonths(2), false)) {
                    hasRegularInvestment = true;
                    break; // 一旦找到，即可停止检查
                }
            }
            profile.setHasRegularInvestment(hasRegularInvestment);
        }

        // 将填充好所有指标的profile对象，一次性保存或更新到数据库
        customerProfileService.saveOrUpdate(profile);
    }

    /**
     * 私有辅助方法：计算一个客户的所有“分类”性质的标签。
     * 这些标签通常是文本，不适合做数值筛选，但适合用于前端直接展示。
     * @param customer  客户实体
     * @param assessment 该客户的最新风险评估记录
     * @return 一个包含所有分类标签的关系对象列表
     */
    private List<CustomerTagRelation> calculateCategoricalTags(Customer customer, RiskAssessment assessment) {
        List<CustomerTagRelation> tags = new ArrayList<>();

        // 1. 人口属性标签
        if (customer.getGender() != null) tags.add(new CustomerTagRelation(customer.getId(), customer.getGender(), "性别"));
        if (customer.getOccupation() != null) tags.add(new CustomerTagRelation(customer.getId(), customer.getOccupation(), "职业"));
        if (customer.getBirthDate() != null) {
            tags.add(new CustomerTagRelation(customer.getId(), getAgeGroupTag(Period.between(customer.getBirthDate(), LocalDate.now()).getYears()), "年龄分代"));
        }

        // 2. 风险评估标签
        String statedRiskLevelName = (assessment != null && assessment.getRiskLevel() != null)
                ? assessment.getRiskLevel() : "未知";
        tags.add(new CustomerTagRelation(customer.getId(), statedRiskLevelName, "申报风险等级"));

        // 3. 未来可以再这里加入更多真正的“分类”标签，比如“渠道来源: 线上APP”等

        return tags;
    }

    /**
     * 私有辅助方法：根据年龄计算年龄分代标签
     */
    private String getAgeGroupTag(int age) {
        if (age >= 60) return "60后及以上";
        if (age >= 50) return "70后";
        if (age >= 40) return "80后";
        if (age >= 30) return "90后";
        if (age >= 20) return "00后";
        return "10后";
    }
}