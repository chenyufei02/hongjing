package com.whu.hongjing.service.impl;

import com.github.javafaker.Faker;
import com.whu.hongjing.pojo.dto.RiskAssessmentSubmitDTO;
import com.whu.hongjing.pojo.entity.Customer;
import com.whu.hongjing.pojo.entity.CustomerHolding;
import com.whu.hongjing.pojo.entity.FundInfo;
import com.whu.hongjing.pojo.entity.FundTransaction;
import com.whu.hongjing.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    @Lazy
    private TagRefreshService tagRefreshService; // 保持注入

    // TODO 改造代码 直接线程池 + MP乐观锁模拟新增数据

    private static final String[] OCCUPATIONS = {
            "软件工程师", "项目经理", "产品经理", "数据分析师", "教师",
            "医生", "护士", "律师", "会计师", "设计师",
            "公务员", "销售经理", "市场专员", "运营专员", "自由职业者"
    };

    @Override
    @Transactional
    public String createMockCustomers(int customerCount) {
        // ... 此方法逻辑完全不变
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
            String address = faker.address().state() + faker.address().city() + faker.address().streetName() +
                    faker.address().buildingNumber() + "号";
            customer.setAddress(address);
            customersToSave.add(customer);
        }
        customerService.saveBatch(customersToSave);

        for (Customer customer : customersToSave) {
            RiskAssessmentSubmitDTO assessmentDto = new RiskAssessmentSubmitDTO();
            assessmentDto.setCustomerId(customer.getId());
            assessmentDto.setScore(random.nextInt(101));
            assessmentDto.setAssessmentDate(LocalDate.now().minusDays(random.nextInt(365)));
            riskAssessmentService.createAssessment(assessmentDto);
        }

        return "【创世】任务完成！成功创建了 " + customersToSave.size() + " 位新客户及其初始风险评估。";
    }

    @Override
    @Transactional
    public String simulateTradingDays(int days) {
        // --- 1. 数据准备 ---
        // ... 此部分逻辑完全不变
        System.out.println("【演绎】开始，准备加载初始数据...");
        Random random = new Random();
        List<Customer> allCustomers = customerService.list();
        Map<String, FundInfo> fundInfoMap = fundInfoService.list().stream()
                .collect(Collectors.toMap(FundInfo::getFundCode, Function.identity()));
        if (allCustomers.isEmpty() || fundInfoMap.isEmpty()) {
            return "【演绎】任务中止：请先确保数据库中存在客户和基金数据。";
        }
        List<FundInfo> allFunds = new ArrayList<>(fundInfoMap.values());
        Map<Long, Map<String, CustomerHolding>> holdingsByCustomer = customerHoldingService.list().stream()
                .collect(Collectors.groupingBy(CustomerHolding::getCustomerId,
                        Collectors.toMap(CustomerHolding::getFundCode, Function.identity())));
        List<FundTransaction> newTransactions = new ArrayList<>();

        // --- 2. 内存模拟 ---
        // ... 此部分逻辑完全不变 (非常长的 for 循环)
        System.out.println("【演绎】初始数据加载完毕，进入内存模拟阶段...");
        for (Customer customer : allCustomers) {
            Map<String, CustomerHolding> customerHoldings = holdingsByCustomer.getOrDefault(customer.getId(), new HashMap<>());
            for (int i = 0; i < days; i++) {
                 if (random.nextInt(100) >= 10) continue;
                 List<CustomerHolding> customerCurrentHoldings = new ArrayList<>(customerHoldings.values());
                 customerCurrentHoldings.removeIf(h -> h.getTotalShares() == null || h.getTotalShares().compareTo(BigDecimal.ZERO) <= 0);
                 FundInfo targetFund;
                 boolean isPurchase;
                 if (!customerCurrentHoldings.isEmpty() && random.nextInt(100) < 70) {
                     CustomerHolding targetHolding = customerCurrentHoldings.get(random.nextInt(customerCurrentHoldings.size()));
                     targetFund = fundInfoMap.get(targetHolding.getFundCode());
                     isPurchase = random.nextBoolean();
                 } else {
                     targetFund = allFunds.get(random.nextInt(allFunds.size()));
                     isPurchase = true;
                 }
                 if (targetFund == null) continue;
                 CustomerHolding holding = customerHoldings.get(targetFund.getFundCode());
                 if (targetFund.getNetValue() != null && targetFund.getNetValue().compareTo(BigDecimal.ZERO) <= 0) continue;
                 BigDecimal netValue = (targetFund.getNetValue() != null) ? targetFund.getNetValue() : new BigDecimal("1.0");
                 LocalDateTime transactionTime = LocalDateTime.now().minusDays(days - i).withHour(10).withMinute(0).withSecond(0);
                 if (isPurchase) {
                     BigDecimal purchaseAmount = new BigDecimal(500 + random.nextInt(5000));
                     BigDecimal purchaseShares = purchaseAmount.divide(netValue, 2, RoundingMode.DOWN);
                     newTransactions.add(createTransaction(customer.getId(), targetFund, "申购", purchaseAmount, purchaseShares, netValue, transactionTime));
                     if (holding == null) {
                         holding = new CustomerHolding();
                         holding.setCustomerId(customer.getId());
                         holding.setFundCode(targetFund.getFundCode());
                         holding.setTotalShares(purchaseShares);
                         holding.setAverageCost(netValue);
                     } else {
                         BigDecimal oldTotalCost = holding.getAverageCost().multiply(holding.getTotalShares());
                         BigDecimal newTotalCost = oldTotalCost.add(purchaseAmount);
                         BigDecimal newTotalShares = holding.getTotalShares().add(purchaseShares);
                         holding.setTotalShares(newTotalShares);
                         holding.setAverageCost(newTotalCost.divide(newTotalShares, 4, RoundingMode.HALF_UP));
                     }
                     holding.setLastUpdateDate(transactionTime);
                     customerHoldings.put(targetFund.getFundCode(), holding);
                 } else {
                     if (holding == null || holding.getTotalShares().compareTo(BigDecimal.ONE) < 0) continue;
                     BigDecimal redeemShares = holding.getTotalShares().multiply(BigDecimal.valueOf(Math.random() * 0.5 + 0.1)).setScale(2, RoundingMode.DOWN);
                     if (redeemShares.compareTo(BigDecimal.ZERO) <= 0) continue;
                     BigDecimal redeemAmount = redeemShares.multiply(netValue).setScale(2, RoundingMode.HALF_UP);
                     newTransactions.add(createTransaction(customer.getId(), targetFund, "赎回", redeemAmount, redeemShares, netValue, transactionTime));
                     holding.setTotalShares(holding.getTotalShares().subtract(redeemShares));
                     holding.setLastUpdateDate(transactionTime);
                     customerHoldings.put(targetFund.getFundCode(), holding);
                 }
            }
            holdingsByCustomer.put(customer.getId(), customerHoldings);
        }


        // --- 3. 批量写入 ---
        // ... 此部分逻辑完全不变
        System.out.println("【演绎】内存模拟完成，开始批量写入数据库...");
        if (!newTransactions.isEmpty()) {
            fundTransactionService.saveBatch(newTransactions, 2000);
            System.out.println("【演绎】成功批量插入 " + newTransactions.size() + " 条交易记录。");
        }
        List<CustomerHolding> allFinalHoldings = holdingsByCustomer.values().stream()
                .flatMap(map -> map.values().stream())
                .collect(Collectors.toList());
        if (!allFinalHoldings.isEmpty()) {
            customerHoldingService.saveOrUpdateBatch(allFinalHoldings, 2000);
            System.out.println("【演绎】成功批量更新/插入 " + allFinalHoldings.size() + " 条持仓记录。");
        }

        // =================================================================
        // =========== 【核心改造点】简化标签刷新逻辑 =======================
        // =================================================================
        System.out.println("【演绎】数据入库完成，开始调用画像刷新服务...");
        try {
            // 只需一行代码，即可触发强大的、带并行处理的、全量画像刷新！
            tagRefreshService.refreshAllTagsAtomically();
        } catch (Exception e) {
            System.err.println("【演绎】调用全量画像刷新服务时发生异常: " + e.getMessage());
            e.printStackTrace();
        }
        // =================================================================

        return "【演绎】任务完成！在 " + days + " 天的模拟中，共为 " + allCustomers.size() +
                " 位客户生成了 " + newTransactions.size() + " 笔新交易，并已触发全量画像刷新任务。";
    }

    private FundTransaction createTransaction(Long customerId, FundInfo fund, String type, BigDecimal amount, BigDecimal shares, BigDecimal price, LocalDateTime transactionTime) {
        // ... 此方法逻辑完全不变
        FundTransaction tx = new FundTransaction();
        tx.setCustomerId(customerId);
        tx.setFundCode(fund.getFundCode());
        tx.setTransactionType(type);
        tx.setTransactionAmount(amount);
        tx.setTransactionShares(shares);
        tx.setSharePrice(price);
        tx.setStatus("成功");
        tx.setTransactionTime(transactionTime);
        return tx;
    }
}