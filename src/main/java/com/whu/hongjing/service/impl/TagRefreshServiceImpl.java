package com.whu.hongjing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.whu.hongjing.enums.RiskLevelEnum;
import com.whu.hongjing.pojo.entity.*;
import com.whu.hongjing.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

@Service
public class TagRefreshServiceImpl implements TagRefreshService {

    // 注入所有我们需要的数据源服务
    @Autowired
    private CustomerService customerService;
    @Autowired
    private FundInfoService fundInfoService;
    @Autowired
    private RiskAssessmentService riskAssessmentService;
    @Autowired
    private CustomerHoldingService customerHoldingService;
    @Autowired
    private CustomerTagRelationService customerTagRelationService;
    @Autowired
    private FundTransactionService fundTransactionService;

    /**
     * 用于单个客户的刷新场景，调用了下面为单个客户计算所有标签的方法，并用客户标签服务实现类保存到数据库。
     */
    @Override
    @Transactional
    public void refreshTagsForCustomer(Long customerId) {
        Customer customer = customerService.getCustomerById(customerId);
        if (customer == null) {
            System.err.println("客户不存在，无法刷新标签。ID: " + customerId);
            return;
        }  // 非空检验 空客户直接返回

        // 1. 调用纯计算方法得到新标签(先查询持仓)
        List<CustomerHolding> holdings = customerHoldingService.listByCustomerId(customerId);
        List<CustomerTagRelation> newTags = calculateTagsForCustomer(customer, holdings);
        System.out.println("为客户 " + customerId + " 计算出 " + newTags.size() + " 个新标签。");

        // 2. “先删后增”：原子化地更新客户的标签
        // a. 删除该客户所有的旧标签（这里只删除单个 下面的批量保存方法删除全表）
        QueryWrapper<CustomerTagRelation> deleteWrapper = new QueryWrapper<>();
        deleteWrapper.eq("customer_id", customerId);
        customerTagRelationService.remove(deleteWrapper);

        // b. 如果有新标签，则批量插入
        if (!newTags.isEmpty()) {
            customerTagRelationService.saveBatch(newTags);
        }

        System.out.println("客户 " + customerId + " 的标签已刷新完毕！");
    }

    /**
     * 【新实现】纯计算方法，只负责计算， 为某客户计算所有的标签，不写入数据库
     */
    @Override
    public List<CustomerTagRelation> calculateTagsForCustomer(Customer customer, List<CustomerHolding> holdings) {
        if (customer == null) {
            return new ArrayList<>();
        }

        // 1. 获取该客户的所有维度的基础数据 (这部分是只读的，并发安全)
        QueryWrapper<RiskAssessment> riskQuery = new QueryWrapper<>();
        riskQuery.eq("customer_id", customer.getId()).orderByDesc("assessment_date").last("LIMIT 1");
        RiskAssessment latestAssessment = riskAssessmentService.getOne(riskQuery);

        // 2. 初始化一个列表，用来存放所有新计算出的标签
        List<CustomerTagRelation> newTags = new ArrayList<>();

        // 3. === 在这里逐一调用各种标签的计算方法 ===
        calculateAndAddDemographicTags(newTags, customer);
        calculateAndAddRiskTags(newTags, customer, latestAssessment, holdings);
        calculateAndAddHoldingAnalysisTags(newTags, customer, holdings);
        calculateAndAddRfmTags(newTags, customer, holdings);

        return newTags;
    }

    /**
     * 【新实现】原子化批量写入方法（针对全表，会先清空全表，因此针对单客户更新标签时只能调用CustomerServiceImpl.saveBatch保存单个到数据库）
     */
    @Override
    @Transactional
    public void refreshAllTagsAtomically(List<CustomerTagRelation> allNewTags) {
        // 1. 为了效率，直接清空全表
        customerTagRelationService.remove(new QueryWrapper<>());
        System.out.println("【标签刷新】已清空 customer_tag_relation 表。");

        // 2. 一次性批量插入所有新计算出的标签
        if (allNewTags != null && !allNewTags.isEmpty()) {
            customerTagRelationService.saveBatch(allNewTags, 2000); // 每2000条一批次
            System.out.println("【标签刷新】已成功批量插入 " + allNewTags.size() + " 个新标签。");
        }
    }


    // --- 以下是私有的、纯计算的辅助方法 ---

    /**
     * 计算人口属性标签
     * @param
     * @return void
     * @author yufei
     * @since 2025/7/6
     */
    private void calculateAndAddDemographicTags(List<CustomerTagRelation> tags, Customer customer) {
        if (customer.getGender() != null && !customer.getGender().isEmpty()) {
            tags.add(new CustomerTagRelation(customer.getId(), customer.getGender(), "性别"));
        }
        if (customer.getOccupation() != null && !customer.getOccupation().isEmpty()) {
            tags.add(new CustomerTagRelation(customer.getId(), customer.getOccupation(), "职业"));
        }
        if (customer.getBirthDate() != null) {
            int age = Period.between(customer.getBirthDate(), LocalDate.now()).getYears();
            tags.add(new CustomerTagRelation(customer.getId(), age + "岁", "年龄"));
            String ageGroupTag;
            if (age >= 55) ageGroupTag = "60后及以上";
            else if (age >= 45) ageGroupTag = "70后";
            else if (age >= 35) ageGroupTag = "80后";
            else if (age >= 25) ageGroupTag = "90后";
            else if (age >= 15) ageGroupTag = "00后";
            else ageGroupTag = "10后";
            tags.add(new CustomerTagRelation(customer.getId(), ageGroupTag, "年龄分代"));
        }
    }

    /**
     * 计算风险诊断标签
     * @param
     * @return void
     * @author yufei
     * @since 2025/7/6
     */
    private void calculateAndAddRiskTags(List<CustomerTagRelation> tags, Customer customer,
                                         RiskAssessment assessment, List<CustomerHolding> holdings) {
        String statedRiskLevelName = (assessment != null && assessment.getRiskLevel() != null)
                                     ? assessment.getRiskLevel() : "未知";

        // 新的标签名："风险评估:成长型", "风险评估:未知"
        tags.add(new CustomerTagRelation(customer.getId(), "申报风险:" + statedRiskLevelName, "风险偏好"));

        if (holdings.isEmpty()) {
            tags.add(new CustomerTagRelation(customer.getId(), "持仓风险:暂无持仓", "风险偏好"));
            tags.add(new CustomerTagRelation(customer.getId(), "风险诊断:信息不足", "风险诊断"));
            return;
        }

        // --- 让实盘风险的标签名更具体，并统一标签大类 ---
        String realRiskLevelName = calculateRealRiskLevel(holdings);
        // 新的标签名："持仓表现:成长型"
        tags.add(new CustomerTagRelation(customer.getId(), "持仓风险:" + realRiskLevelName, "风险偏好"));

        // --- 让诊断标签也更具体 ---
        String diagnosticTag;
        if ("未知".equals(statedRiskLevelName) || "未知".equals(realRiskLevelName) || "暂无持仓".equals(realRiskLevelName)) {
            diagnosticTag = "信息不足";
        } else if (statedRiskLevelName.equals(realRiskLevelName)) {
            diagnosticTag = "知行合一";
        } else if (isRiskier(statedRiskLevelName, realRiskLevelName)) {
            diagnosticTag = "行为保守";
        } else {
            diagnosticTag = "行为激进";
        }
        // 新的标签名："风险诊断:知行合一"
        tags.add(new CustomerTagRelation(customer.getId(), "风险诊断:" + diagnosticTag, "风险诊断"));

    }

    /**
     * 计算实盘风险标签
     * @param
     * @return java.lang.String
     * @author yufei
     * @since 2025/7/6
     */
    private String calculateRealRiskLevel(List<CustomerHolding> holdings) {
        List<String> fundCodes = holdings.stream().map(CustomerHolding::getFundCode).distinct().collect(Collectors.toList());
        if (fundCodes.isEmpty()) {
            return "未知";
        }
        Map<String, FundInfo> fundInfoMap = fundInfoService.listByIds(fundCodes).stream()
                .collect(Collectors.toMap(FundInfo::getFundCode, Function.identity()));

        BigDecimal totalMarketValue = BigDecimal.ZERO;
        BigDecimal totalWeightedRisk = BigDecimal.ZERO;

        for (CustomerHolding holding : holdings) {
            FundInfo fundInfo = fundInfoMap.get(holding.getFundCode());
            if (holding.getMarketValue() != null && fundInfo != null && fundInfo.getRiskScore() != null) {
                totalMarketValue = totalMarketValue.add(holding.getMarketValue());
                totalWeightedRisk = totalWeightedRisk.add(
                    holding.getMarketValue().multiply(new BigDecimal(fundInfo.getRiskScore()))
                );
            }
        }

        if (totalMarketValue.compareTo(BigDecimal.ZERO) <= 0) {
            return "未知";
        }

        double avgRiskScore = totalWeightedRisk.divide(totalMarketValue, 2, RoundingMode.HALF_UP).doubleValue();
        RiskLevelEnum realRiskEnum = RiskLevelEnum.getByScore((int) Math.round(avgRiskScore * 20)); // 分数范围转换
        return realRiskEnum.getLevelName();
    }

    /**
     * 辅助进行评估与实盘的风险比较
     * @param
     * @return boolean
     * @author yufei
     * @since 2025/7/6
     */
    private boolean isRiskier(String riskLevelName1, String riskLevelName2) {
        try {
            RiskLevelEnum level1 = findEnumByLevelName(riskLevelName1);
            RiskLevelEnum level2 = findEnumByLevelName(riskLevelName2);
            return level1.ordinal() > level2.ordinal();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 返回风险等级的枚举类
     * @param
     * @return com.whu.hongjing.enums.RiskLevelEnum
     * @author yufei
     * @since 2025/7/6
     */
    private RiskLevelEnum findEnumByLevelName(String levelName) {
        for (RiskLevelEnum level : RiskLevelEnum.values()) {
            if (level.getLevelName().equals(levelName)) {
                return level;
            }
        }
        throw new IllegalArgumentException("未知的风险等级名称: " + levelName);
    }

    /**
     * 计算持仓分析相关的标签 (集中度, 持仓周期)
     */
    private void calculateAndAddHoldingAnalysisTags(List<CustomerTagRelation> tags, Customer customer, List<CustomerHolding> holdings) {
        // 如果客户没有持仓，直接打上“暂无持仓”标签并返回
        if (holdings == null || holdings.isEmpty()) {
            tags.add(new CustomerTagRelation(customer.getId(), "暂无持仓", "持仓集中度"));
            tags.add(new CustomerTagRelation(customer.getId(), "暂无持仓", "持仓周期"));
            return;
        }

        // --- 计算持仓集中度 ---
        BigDecimal totalMarketValue = holdings.stream()
                .map(CustomerHolding::getMarketValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalMarketValue.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal maxHoldingValue = holdings.stream()
                    .map(CustomerHolding::getMarketValue)
                    .filter(Objects::nonNull)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            double concentrationRatio = maxHoldingValue.divide(totalMarketValue, 4, RoundingMode.HALF_UP).doubleValue();
            String concentrationTag;
            if (concentrationRatio > 0.5) {
                concentrationTag = "持仓高度集中";
            } else if (concentrationRatio > 0.2) {
                concentrationTag = "持仓中度分散";
            } else {
                concentrationTag = "持仓高度分散";
            }
            tags.add(new CustomerTagRelation(customer.getId(), concentrationTag, "持仓集中度"));
        } else {
            tags.add(new CustomerTagRelation(customer.getId(), "市值信息不足", "持仓集中度"));
        }

        // --- 计算平均持仓周期 ---
        BigDecimal totalWeightedDays = BigDecimal.ZERO;
        int validHoldingsCount = 0;

        for (CustomerHolding holding : holdings) {
            // 为每一笔持仓，查询其最早的申购记录
            QueryWrapper<FundTransaction> txQuery = new QueryWrapper<>();
            txQuery.eq("customer_id", customer.getId())
                   .eq("fund_code", holding.getFundCode())
                   .eq("transaction_type", "申购")
                   .orderByAsc("transaction_time")
                   .last("LIMIT 1");
            FundTransaction firstPurchase = fundTransactionService.getOne(txQuery);

            if (firstPurchase != null) {
                long holdingDays = Period.between(firstPurchase.getTransactionTime().toLocalDate(), LocalDate.now()).getDays();
                BigDecimal marketValue = holding.getMarketValue() == null ? BigDecimal.ZERO : holding.getMarketValue();
                totalWeightedDays = totalWeightedDays.add(new BigDecimal(holdingDays).multiply(marketValue));
                validHoldingsCount++;
            }
        }

        if (validHoldingsCount > 0 && totalMarketValue.compareTo(BigDecimal.ZERO) > 0) {
            double avgHoldingDays = totalWeightedDays.divide(totalMarketValue, 0, RoundingMode.HALF_UP).doubleValue();
            String periodTag;
            if (avgHoldingDays > 180) {
                periodTag = "长期持有型";
            } else {
                periodTag = "短期交易型";
            }
            tags.add(new CustomerTagRelation(customer.getId(), periodTag, "持仓周期"));
        } else {
             tags.add(new CustomerTagRelation(customer.getId(), "持仓周期未知", "持仓周期"));
        }
    }

    /**
     * RFM标签计算总入口
     */
    private void calculateAndAddRfmTags(List<CustomerTagRelation> tags, Customer customer, List<CustomerHolding> holdings) {
        // 1. 根据已有的“持仓周期”标签，判断客户是一级分类中的哪一类
        String holdingPeriodTag = tags.stream()
                .filter(tag -> "持仓周期".equals(tag.getTagCategory()))
                .map(CustomerTagRelation::getTagName)
                .findFirst()
                .orElse("未知");

        // 2. 获取该客户的所有交易记录，这是RFM分析的基础
        List<FundTransaction> transactions = fundTransactionService.list(
                new QueryWrapper<FundTransaction>().eq("customer_id", customer.getId())
        );

        String rfmTag;
        // 3. 根据一级分类，进入不同的RFM模型进行计算
        if ("长期持有型".equals(holdingPeriodTag)) {
            rfmTag = calculateLongTermRfm(holdings, transactions);
        } else { // 包括“短期交易型”、“暂无持仓”等都归入交易型模型判断
            rfmTag = calculateTransactionalRfm(holdings, transactions);
        }

        // 4. 将最终计算出的客户分层标签加入列表
        tags.add(new CustomerTagRelation(customer.getId(), rfmTag, "客户价值分层"));
    }

    /**
     * 为“交易型客户”计算RFM分层
     */
    private String calculateTransactionalRfm(List<CustomerHolding> holdings, List<FundTransaction> transactions) {
        if (transactions.isEmpty()) return "暂无交易客户";

        // --- 计算 R (Recency) ---
        long daysSinceLastTx = transactions.stream()
                .map(FundTransaction::getTransactionTime)
                .max(LocalDateTime::compareTo)
                .map(lastTxTime -> ChronoUnit.DAYS.between(lastTxTime, LocalDateTime.now()))
                .orElse(Long.MAX_VALUE);

        int rScore = (daysSinceLastTx <= 30) ? 3 : (daysSinceLastTx <= 90) ? 2 : 1;

        // --- 计算 F (Frequency) ---
        LocalDateTime ninetyDaysAgo = LocalDateTime.now().minusDays(90);
        long txCountIn90Days = transactions.stream()
                .filter(tx -> tx.getTransactionTime().isAfter(ninetyDaysAgo))
                .count();

        int fScore = (txCountIn90Days > 10) ? 3 : (txCountIn90Days >= 3) ? 2 : 1;

        // --- 计算 M (Monetary) ---
        BigDecimal totalMarketValue = holdings.stream()
                .map(CustomerHolding::getMarketValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int mScore = (totalMarketValue.compareTo(new BigDecimal("500000")) > 0) ? 3
                   : (totalMarketValue.compareTo(new BigDecimal("100000")) > 0) ? 2 : 1;

        // --- 应用分层规则 ---
        if (rScore == 3 && fScore == 3) return "核心价值客户";
        if (rScore == 3 && fScore == 2) return "潜力增长客户";
        if (rScore == 3 && fScore == 1) return "新进活跃客户";
        if (rScore == 2 && fScore > 1) return "需要唤醒客户";
        if (rScore == 1) return "休眠流失预警";

        return "一般客户";
    }

    /**
     * 为“长持型客户”计算RFM分层
     */
    private String calculateLongTermRfm(List<CustomerHolding> holdings, List<FundTransaction> transactions) {
        if (transactions.isEmpty()) return "暂无交易客户";

        // --- 计算 M (Monetary) - 采用你的建议，直接使用资产规模 ---
        BigDecimal totalMarketValue = holdings.stream()
                .map(CustomerHolding::getMarketValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int mScore = (totalMarketValue.compareTo(new BigDecimal("500000")) > 0) ? 3
                   : (totalMarketValue.compareTo(new BigDecimal("100000")) > 0) ? 2 : 1;

        // --- 计算 R (Recency) - 最近净投入月份 ---
        Map<YearMonth, BigDecimal> monthlyNetInvestment = transactions.stream()
                .collect(Collectors.groupingBy(tx -> YearMonth.from(tx.getTransactionTime()),
                        Collectors.reducing(BigDecimal.ZERO,
                                tx -> "申购".equals(tx.getTransactionType()) ? tx.getTransactionAmount() : tx.getTransactionAmount().negate(),
                                BigDecimal::add)
                ));

        long monthsSinceLastNetInvestment = monthlyNetInvestment.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) > 0)
                .map(Map.Entry::getKey)
                .max(YearMonth::compareTo)
                .map(lastYm -> ChronoUnit.MONTHS.between(lastYm, YearMonth.now()))
                .orElse(Long.MAX_VALUE);

        int rScore = (monthsSinceLastNetInvestment <= 3) ? 3 : (monthsSinceLastNetInvestment <= 6) ? 2 : 1;

        // --- 计算 F (Frequency) - 定投行为 ---
        Map<YearMonth, Boolean> monthlyPurchase = transactions.stream()
                .filter(tx -> "申购".equals(tx.getTransactionType()) && tx.getTransactionTime().isAfter(LocalDateTime.now().minusYears(1)))
                .collect(Collectors.toMap(tx -> YearMonth.from(tx.getTransactionTime()), v -> true, (a, b) -> a));

        boolean hasRegularInvestment = false;
        YearMonth currentMonth = YearMonth.now();
        for (int i = 0; i < 10; i++) { // 检查过去12个月（最多检查10个起始点）
            YearMonth start = currentMonth.minusMonths(i);
            if (monthlyPurchase.getOrDefault(start, false) &&
                monthlyPurchase.getOrDefault(start.minusMonths(1), false) &&
                monthlyPurchase.getOrDefault(start.minusMonths(2), false)) {
                hasRegularInvestment = true;
                break;
            }
        }

        int fScore = hasRegularInvestment ? 3 : 1; // 定投只分“有”或“无”

        // --- 应用分层规则 ---
        if (mScore == 3) return "核心价值客户";
        if (mScore == 2 && rScore == 3) return "忠诚成长客户";
        if (mScore == 2 && rScore < 3) return "稳定持有客户";
        if (mScore == 1 && fScore == 3) return "潜力定投客户";
        if (mScore == 1 && rScore == 1) return "流失预警客户";

        return "一般客户";
    }

}