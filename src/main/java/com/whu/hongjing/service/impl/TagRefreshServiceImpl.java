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
    private FundTransactionService fundTransactionService;

    @Autowired
    private CustomerTagRelationService customerTagRelationService;

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
     /**
     * 计算并添加风险诊断相关的标签 (最终优雅版)
     */
    private void calculateAndAddRiskTags(List<CustomerTagRelation> tags, Customer customer,
                                         RiskAssessment assessment, List<CustomerHolding> holdings) {

        // --- 步骤1：生成“风险评估等级”标签（来自问卷） ---
        String statedRiskLevelName = (assessment != null && assessment.getRiskLevel() != null)
                                     ? assessment.getRiskLevel() : "未知";
        tags.add(new CustomerTagRelation(customer.getId(), statedRiskLevelName, "风险评估等级"));

        if (assessment == null || holdings.isEmpty()) {
            tags.add(new CustomerTagRelation(customer.getId(), "诊断信息不足", "风险诊断"));
            return;
        }

        // --- 步骤2：生成“持仓风险偏好”标签（来自真实持仓） ---
        String realRiskLevelName = calculateRealRiskLevel(holdings);
        tags.add(new CustomerTagRelation(customer.getId(), realRiskLevelName, "持仓风险偏好"));

        // --- 步骤3：生成最终的“风险诊断”标签（对比以上两者） ---
        String diagnosticTag;
        if ("未知".equals(statedRiskLevelName) || "未知".equals(realRiskLevelName)) {
            diagnosticTag = "诊断信息不足";
        } else if (statedRiskLevelName.equals(realRiskLevelName)) {
            diagnosticTag = "风险匹配";
        } else if (isRiskier(statedRiskLevelName, realRiskLevelName)) {
            diagnosticTag = "行为保守";
        } else {
            diagnosticTag = "行为激进";
        }
        tags.add(new CustomerTagRelation(customer.getId(), diagnosticTag, "风险诊断"));
    }

    /**
     * 【新】核心计算方法：根据持仓，计算出实盘风险等级的中文名称
     */
    private String calculateRealRiskLevel(List<CustomerHolding> holdings) {
        // a. 获取所有持仓基金的详细信息
        List<String> fundCodes = holdings.stream().map(CustomerHolding::getFundCode).collect(Collectors.toList());
        Map<String, FundInfo> fundInfoMap = fundInfoService.listByIds(fundCodes).stream()
                .collect(Collectors.toMap(FundInfo::getFundCode, Function.identity()));

        // b. 计算加权平均风险分
        BigDecimal totalMarketValue = BigDecimal.ZERO;  // 持有的某一支基金的总市值
        // 用当前市值来计算风险而不是用持有成本来计算，更能在客户资产随市值波动一段时间后根据 客户真实的的资产分布比例来计算真实风险
        // 而用持有成本计算的话不管市值怎么波动客户资产分布比例怎么波动 风险永远都不会变 不符合实际
        BigDecimal totalWeightedRisk = BigDecimal.ZERO; // 总风险分数 = 基金总市值*基金风险

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
            return "未知"; // 市值为0，无法计算
        }

        // c. 计算风险平均分  = 总风险分数 / 总市值 （平均每块钱承担了多少风险）
        double avgRiskScore = totalWeightedRisk.divide(totalMarketValue, 2, RoundingMode.HALF_UP).doubleValue();

        // d. V V V 核心修改：调用枚举类来获取等级 V V V
        RiskLevelEnum realRiskEnum = RiskLevelEnum.getByScore((int) Math.round(avgRiskScore));
        return realRiskEnum.getLevelName(); // 从枚举实例中获取中文名
    }

    /**
     * 【优化】风险比较器：现在直接比较枚举的顺序，更健壮
     */
    private boolean isRiskier(String riskLevelName1, String riskLevelName2) {
        try {
            RiskLevelEnum level1 = findEnumByLevelName(riskLevelName1);
            RiskLevelEnum level2 = findEnumByLevelName(riskLevelName2);
            // ordinal() 返回枚举的声明顺序 (0, 1, 2...)
            // 我们的枚举是按风险从低到高声明的，所以序号越大风险越高
            return level1.ordinal() > level2.ordinal();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 【优化】中文逆向查询器：返回枚举实例，而不是字符串
     */
    private RiskLevelEnum findEnumByLevelName(String levelName) {
        for (RiskLevelEnum level : RiskLevelEnum.values()) {
            if (level.getLevelName().equals(levelName)) {
                return level;
            }
        }
        throw new IllegalArgumentException("未知的风险等级名称: " + levelName);
    }
}