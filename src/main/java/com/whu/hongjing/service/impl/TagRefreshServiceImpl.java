package com.whu.hongjing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.whu.hongjing.pojo.entity.Customer;
import com.whu.hongjing.pojo.entity.CustomerHolding;
import com.whu.hongjing.pojo.entity.CustomerTagRelation;
import com.whu.hongjing.pojo.entity.FundTransaction;
import com.whu.hongjing.pojo.entity.RiskAssessment;
import com.whu.hongjing.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class TagRefreshServiceImpl implements TagRefreshService {

    // 注入所有我们需要的数据源服务
    @Autowired
    private CustomerService customerService;

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

        // (未来我们在这里获取风险、持仓、交易等数据)
        // List<RiskAssessment> assessments = riskAssessmentService.list(...);
        // List<CustomerHolding> holdings = customerHoldingService.listByCustomerId(customerId);
        // List<FundTransaction> transactions = fundTransactionService.list(...);

        // 2. 初始化一个列表，用来存放所有新计算出的标签
        List<CustomerTagRelation> newTags = new ArrayList<>();

        // 3. === 在这里，我们将逐一调用各种标签的计算方法 ===
        //    calculateAndAddDemographicTags(newTags, customer);  // e.g., 计算人口属性标签
        //    calculateAndAddRiskTags(newTags, customer, assessments); // e.g., 计算风险诊断标签
        //    ...等等

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
}