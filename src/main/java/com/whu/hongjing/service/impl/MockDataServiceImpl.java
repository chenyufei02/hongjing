package com.whu.hongjing.service.impl;

import com.github.javafaker.Faker;
import com.whu.hongjing.pojo.dto.RiskAssessmentSubmitDTO;
import com.whu.hongjing.pojo.entity.Customer;
import com.whu.hongjing.pojo.entity.CustomerHolding;
import com.whu.hongjing.pojo.entity.FundInfo;
import com.whu.hongjing.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 模拟数据生成服务实现类 (最终重构版)
 * 职责：分离“创世”和“演绎”，提供可重复调用的数据模拟工具
 */
@Service
public class MockDataServiceImpl implements MockDataService {

    @Autowired
    private FundInfoService fundInfoService;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private RiskAssessmentService riskAssessmentService;
    @Autowired
    private FundTransactionService fundTransactionService;
    @Autowired
    private CustomerHoldingService customerHoldingService;
    @Autowired
    private TagRefreshService tagRefreshService;

    private static final String[] OCCUPATIONS = {
            "软件工程师", "项目经理", "产品经理", "数据分析师", "教师",
            "医生", "护士", "律师", "会计师", "设计师",
            "公务员", "销售经理", "市场专员", "运营专员", "自由职业者"
    };

    /**
     * 【工具I：创世】
     * 生成一批全新的模拟客户及其初始风险评估
     * @param customerCount 要生成的客户数量
     * @return 执行结果信息
     */
    @Override
    @Transactional
    public String createMockCustomers(int customerCount) {
        Faker faker = new Faker(Locale.CHINA);
        Random random = new Random();
        List<Customer> customersToSave = new ArrayList<>();

        for (int i = 0; i < customerCount; i++) {
            Customer customer = new Customer();
            customer.setName(faker.name().fullName());
            customer.setGender(random.nextBoolean() ? "男" : "女");
            customer.setIdType("身份证");
            customer.setIdNumber(faker.number().digits(18));
            customer.setBirthDate(faker.date().birthday(18, 65).toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
            customer.setNationality("中国");
            customer.setOccupation(OCCUPATIONS[random.nextInt(OCCUPATIONS.length)]);
            customer.setPhone(faker.phoneNumber().cellPhone());
            String address = faker.address().state() + faker.address().city() + faker.address().streetName() + faker.address().buildingNumber() + "号";
            customer.setAddress(address);
            customersToSave.add(customer);
        }
        customerService.saveBatch(customersToSave);

        // 为新客户创建初始风险评估
        for (Customer customer : customersToSave) {
            RiskAssessmentSubmitDTO assessmentDto = new RiskAssessmentSubmitDTO();
            assessmentDto.setCustomerId(customer.getId());
            assessmentDto.setScore(random.nextInt(101));
            assessmentDto.setAssessmentDate(LocalDate.now().minusDays(random.nextInt(365)));
            riskAssessmentService.createAssessment(assessmentDto);
        }

        return "【创世】任务完成！成功创建了 " + customersToSave.size() + " 位新客户及其初始风险评估。";
    }

    /**
     * 【工具II：演绎】(最终智能版)
     * 为所有现有客户模拟接下来一段时间的交易
     */
    @Override
    @Transactional
    public String simulateTradingDays(int days) {
        Random random = new Random();
        List<Customer> allCustomers = customerService.list();
        List<FundInfo> allFunds = fundInfoService.list();

        if (allCustomers.isEmpty() || allFunds.isEmpty()) {
            return "【演绎】任务中止：请先确保数据库中存在客户和基金数据。";
        }

        int totalTransactions = 0;
        for (Customer customer : allCustomers) {
            for (int i = 0; i < days; i++) {
                // 假设每个客户每天有 10% 的概率会进行一次交易
                if (random.nextInt(100) < 10) {

                    // --- 核心修改：引入智能决策逻辑 ---
                    List<CustomerHolding> currentHoldings = customerHoldingService.listByCustomerId(customer.getId());

                    // 决策1：是交易已有持仓，还是开拓新基金？
                    // 如果没有持仓，或70%的概率，则交易已有持仓
                    if (!currentHoldings.isEmpty() && random.nextInt(100) < 70) {
                        // --- 对已有持仓进行操作 ---
                        CustomerHolding targetHolding = currentHoldings.get(random.nextInt(currentHoldings.size()));

                        // 决策2：是追加申购还是赎回？(50/50概率)
                        if (random.nextBoolean()) {
                            // a. 追加申购
                            fundTransactionService.purchase(customer.getId(), targetHolding.getFundCode(), new BigDecimal(500 + random.nextInt(5000)));
                        } else {
                            // b. 赎回一部分
                            BigDecimal sharesToRedeem = targetHolding.getTotalShares().multiply(new BigDecimal(Math.random() * 0.5 + 0.1)).setScale(2, BigDecimal.ROUND_DOWN);
                            if (sharesToRedeem.compareTo(BigDecimal.ZERO) > 0) {
                                fundTransactionService.redeem(customer.getId(), targetHolding.getFundCode(), sharesToRedeem);
                            }
                        }
                    } else {
                        // --- 申购一只全新的基金 ---
                        FundInfo targetFund = allFunds.get(random.nextInt(allFunds.size()));
                        fundTransactionService.purchase(customer.getId(), targetFund.getFundCode(), new BigDecimal(1000 + random.nextInt(10000)));
                    }
                    totalTransactions++;
                }
            }
            // 在为这个客户模拟完所有交易后，为他统一刷新一次标签
            tagRefreshService.refreshTagsForCustomer(customer.getId());
        }

        return "【演绎】任务完成！在 " + days + " 天的模拟中，共为 " + allCustomers.size() + " 位客户生成了 " + totalTransactions + " 笔新交易，并已刷新所有相关客户的持仓与画像标签。";
    }
}