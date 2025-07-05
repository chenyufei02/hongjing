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
    private CustomerTagRelationService customerTagRelationService;

    /**
     * 用于单个客户的刷新场景
     */
    @Override
    @Transactional
    public void refreshTagsForCustomer(Long customerId) {
        Customer customer = customerService.getCustomerById(customerId);
        if (customer == null) {
            System.err.println("客户不存在，无法刷新标签。ID: " + customerId);
            return;
        }

        // 1. 调用纯计算方法得到新标签(先查询持仓)
        List<CustomerHolding> holdings = customerHoldingService.listByCustomerId(customerId);
        List<CustomerTagRelation> newTags = calculateTagsForCustomer(customer, holdings);
        System.out.println("为客户 " + customerId + " 计算出 " + newTags.size() + " 个新标签。");

        // 2. “先删后增”：原子化地更新客户的标签
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
     * 【新实现】纯计算方法，只负责计算，不写入数据库
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

        // 3. === 在这里，我们将逐一调用各种标签的计算方法 ===
        calculateAndAddDemographicTags(newTags, customer);
        calculateAndAddRiskTags(newTags, customer, latestAssessment, holdings);

        return newTags;
    }

    /**
     * 【新实现】原子化批量写入方法
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


    // --- 以下是私有的、纯计算的辅助方法，保持不变 ---

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

    private boolean isRiskier(String riskLevelName1, String riskLevelName2) {
        try {
            RiskLevelEnum level1 = findEnumByLevelName(riskLevelName1);
            RiskLevelEnum level2 = findEnumByLevelName(riskLevelName2);
            return level1.ordinal() > level2.ordinal();
        } catch (Exception e) {
            return false;
        }
    }

    private RiskLevelEnum findEnumByLevelName(String levelName) {
        for (RiskLevelEnum level : RiskLevelEnum.values()) {
            if (level.getLevelName().equals(levelName)) {
                return level;
            }
        }
        throw new IllegalArgumentException("未知的风险等级名称: " + levelName);
    }
}