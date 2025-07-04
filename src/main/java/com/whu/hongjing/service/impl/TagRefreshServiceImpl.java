package com.whu.hongjing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.whu.hongjing.pojo.entity.*;
import com.whu.hongjing.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private FundTransactionService fundTransactionService;

    @Autowired
    private CustomerTagRelationService customerTagRelationService; // <-- 我们还需要为新表创建一个Service

    @Override
    @Transactional
    public void refreshTagsForCustomer(Long customerId) {
        // 1. 获取该客户的所有维度的基础数据
        Customer customer = customerService.getCustomerById(customerId);
        if (customer == null) {
            System.out.println("客户不存在，无法刷新标签。ID: " + customerId);
            return;
        }

        // List<FundTransaction> transactions = fundTransactionService.list(...);

        // a. 获取客户最新的风险评估记录
        QueryWrapper<RiskAssessment> riskQuery = new QueryWrapper<>();
        riskQuery.eq("customer_id", customerId).orderByDesc("assessment_date").last("LIMIT 1");
        RiskAssessment latestAssessment = riskAssessmentService.getOne(riskQuery);

        // b. 获取客户的所有持仓记录
        List<CustomerHolding> holdings = customerHoldingService.listByCustomerId(customerId);

        // 2. 初始化一个列表，用来存放所有新计算出的标签
        List<CustomerTagRelation> newTags = new ArrayList<>();

        // 3. === 在这里，我们将逐一调用各种标签的计算方法 ===
        calculateAndAddDemographicTags(newTags, customer);  // e.g., 计算人口属性标签
        calculateAndAddRiskTags(newTags, customer, latestAssessment, holdings); // e.g., 计算风险诊断标签

        System.out.println("为客户 " + customerId + " 计算出 " + newTags.size() + " 个新标签。");

        // 4. “先删后增”：原子化地更新客户的标签
        // a. 删除该客户所有的旧标签
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
     * 计算并添加人口属性相关的标签
     * 使用更精细的tag_category，以便于后续的统计分析
     * @param tags 标签列表，计算出的新标签将被添加到此列表中
     * @param customer 客户实体对象
     */
    private void calculateAndAddDemographicTags(List<CustomerTagRelation> tags, Customer customer) {
        // === a. 添加性别标签 ===
        if (customer.getGender() != null && !customer.getGender().isEmpty()) {
            // 类别直接定义为“性别”，而不是笼统的“人口属性”
            tags.add(new CustomerTagRelation(customer.getId(), customer.getGender(), "性别"));
        }

        // === b. 添加职业标签 ===
        if (customer.getOccupation() != null && !customer.getOccupation().isEmpty()) {
            // 类别定义为“职业”
            tags.add(new CustomerTagRelation(customer.getId(), customer.getOccupation(), "职业"));
        }

        // === c. 添加年龄相关标签 ===
        if (customer.getBirthDate() != null) {
            int age = Period.between(customer.getBirthDate(), LocalDate.now()).getYears();
            // “具体岁数”可以有自己的类别
            tags.add(new CustomerTagRelation(customer.getId(), age + "岁", "年龄"));

            String ageGroupTag;
            if (age >= 60) ageGroupTag = "60后及以上";
            else if (age >= 50) ageGroupTag = "70后";
            else if (age >= 40) ageGroupTag = "80后";
            else if (age >= 30) ageGroupTag = "90后";
            else if (age >= 20) ageGroupTag = "00后";
            else ageGroupTag = "10后";
            // “年龄分代”是另一个独立的、非常有分析价值的类别
            tags.add(new CustomerTagRelation(customer.getId(), ageGroupTag, "年龄分代"));
        }
    }

    /**
     * 计算并添加风险诊断相关的标签 (最终实现版)
     */
    private void calculateAndAddRiskTags(List<CustomerTagRelation> tags, Customer customer,
                                         RiskAssessment assessment, List<CustomerHolding> holdings) {

        // --- 步骤1：生成“风险评估标签”（来自问卷） ---
        String statedRiskLevel = (assessment != null) ? assessment.getRiskLevel() : "未知";
        tags.add(new CustomerTagRelation(customer.getId(), statedRiskLevel, "风险评估等级"));

        if (assessment == null || holdings.isEmpty()) {
            // 如果没有问卷或没有持仓，无法进行后续诊断
            return;
        }

        // --- 步骤2：生成“风险偏好标签”（来自真实持仓） ---

        // a. 获取所有持仓基金的风险分
        List<String> fundCodes = holdings.stream().map(CustomerHolding::getFundCode).collect(Collectors.toList());
        Map<String, FundInfo> fundInfoMap = fundInfoService.listByIds(fundCodes).stream()
                .collect(Collectors.toMap(FundInfo::getFundCode, Function.identity()));

        // b. 计算加权平均风险分
        BigDecimal totalMarketValue = BigDecimal.ZERO;
        BigDecimal totalWeightedRisk = BigDecimal.ZERO;

        for (CustomerHolding holding : holdings) {
            FundInfo fundInfo = fundInfoMap.get(holding.getFundCode());
            // 确保市值和基金风险分存在
            if (holding.getMarketValue() != null && fundInfo != null && fundInfo.getRiskScore() != null) {
                totalMarketValue = totalMarketValue.add(holding.getMarketValue());
                totalWeightedRisk = totalWeightedRisk.add(
                    holding.getMarketValue().multiply(new BigDecimal(fundInfo.getRiskScore()))
                );
            }
        }

        // c. 将风险分数映射为风险等级
        String realRiskLevel = "未知";
        if (totalMarketValue.compareTo(BigDecimal.ZERO) > 0) {
            double avgRiskScore = totalWeightedRisk.divide(totalMarketValue, 2, RoundingMode.HALF_UP).doubleValue();
            realRiskLevel = mapScoreToLevel(avgRiskScore);
        }
        tags.add(new CustomerTagRelation(customer.getId(), realRiskLevel, "持仓风险偏好"));

        // --- 步骤3：生成“风险诊断标签”（对比以上两者） ---
        String diagnosticTag;
        if ("未知".equals(statedRiskLevel) || "未知".equals(realRiskLevel)) {
            diagnosticTag = "诊断信息不足";
        } else if (statedRiskLevel.equals(realRiskLevel)) {
            diagnosticTag = "风险匹配";
        } else if (isRiskier(statedRiskLevel, realRiskLevel)) {
            diagnosticTag = "行为保守";
        } else {
            diagnosticTag = "行为激进";
        }
        tags.add(new CustomerTagRelation(customer.getId(), diagnosticTag, "风险诊断"));
    }

    /**
     * 辅助方法：将风险分数映射到等级
     */
    private String mapScoreToLevel(double score) {
        if (score >= 4) return "激进型";
        if (score >= 3) return "成长型";
        if (score >= 2) return "稳健型";
        if (score >= 1) return "保守型";
        return "极保守型";
    }

    /**
     * 辅助方法：判断 riskLevel1 是否比 riskLevel2 风险更高
     * 这里用简单的文本比较，真实项目可以用分数
     */
    private boolean isRiskier(String riskLevel1, String riskLevel2) {
        // 假设风险等级: 激进型 > 成长型 > 稳健型 > 保守型
        if ("激进型".equals(riskLevel1)) return !"激进型".equals(riskLevel2);
        if ("成长型".equals(riskLevel1)) return "稳健型".equals(riskLevel2) || "保守型".equals(riskLevel2);
        if ("稳健型".equals(riskLevel1)) return "保守型".equals(riskLevel2);
        return false;
    }
}