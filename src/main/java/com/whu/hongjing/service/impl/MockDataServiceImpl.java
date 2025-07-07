package com.whu.hongjing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import java.util.concurrent.ThreadLocalRandom;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * V3版本：交易模拟采用并行流（Parallel Stream）实现，代码更简洁、性能更强、100%利用多核CPU。
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
    @Lazy
    private TagRefreshService tagRefreshService;

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

    /**
     * 【最终改造】使用并行流（Parallel Stream）重构，确保模拟计算过程真正并发执行。
     */
    @Override
    @Transactional
    public String simulateTradingDays(int days) {
        // --- 1. 数据准备 ---
        System.out.println("【演绎】开始，准备加载初始数据...");
        List<Customer> allCustomers = customerService.list();
        Map<String, FundInfo> fundInfoMap = fundInfoService.list().stream()
                .collect(Collectors.toMap(FundInfo::getFundCode, Function.identity()));
        if (allCustomers.isEmpty() || fundInfoMap.isEmpty()) {
            return "【演绎】任务中止：请先确保数据库中存在客户和基金数据。";
        }
        List<FundInfo> allFunds = new ArrayList<>(fundInfoMap.values());

        // --- 2. 【核心改造】使用并行流并发模拟 ---
        System.out.println("【演绎】初始数据加载完毕，开始并行模拟所有客户的交易数据...");

        // a. 将客户列表转换为并行流，.map()中的逻辑会在多个线程中被同时执行
        List<Map<String, Object>> simulationResults = allCustomers.parallelStream().map(customer -> {
            // 这部分代码会在一个后台的ForkJoinPool线程中执行
            // System.out.println("正在线程 " + Thread.currentThread().getName() + " 上模拟客户 " + customer.getId());

            Random random = ThreadLocalRandom.current(); // 使用线程ID做种子，保证随机性
            List<FundTransaction> customerTransactions = new ArrayList<>();
            Map<String, CustomerHolding> customerHoldings = new HashMap<>();

            // 模拟逻辑与之前完全相同
            for (int i = 0; i < days; i++) {
                if (random.nextInt(100) >= 10) continue;

                List<CustomerHolding> currentHoldingsList = new ArrayList<>(customerHoldings.values());
                currentHoldingsList.removeIf(h -> h.getTotalShares() == null || h.getTotalShares().compareTo(BigDecimal.ZERO) <= 0);
                FundInfo targetFund;
                boolean isPurchase;
                if (!currentHoldingsList.isEmpty() && random.nextInt(100) < 70) {
                    CustomerHolding targetHolding = currentHoldingsList.get(random.nextInt(currentHoldingsList.size()));
                    targetFund = fundInfoMap.get(targetHolding.getFundCode());
                    isPurchase = random.nextInt(100) < 70;
                } else {
                    targetFund = allFunds.get(random.nextInt(allFunds.size()));
                    isPurchase = true;
                }
                if (targetFund == null || (targetFund.getNetValue() != null && targetFund.getNetValue().compareTo(BigDecimal.ZERO) <= 0))
                    continue;

                BigDecimal netValue = (targetFund.getNetValue() != null) ? targetFund.getNetValue() : new BigDecimal("1.0");
                LocalDateTime transactionTime = LocalDateTime.now().minusDays(days - i).withHour(10).withMinute(0).withSecond(0);
                CustomerHolding holding = customerHoldings.get(targetFund.getFundCode());

                if (isPurchase) {
                    BigDecimal purchaseAmount = new BigDecimal(500 + random.nextInt(200000));
                    BigDecimal purchaseShares = purchaseAmount.divide(netValue, 2, RoundingMode.DOWN);
                    customerTransactions.add(createTransaction(customer.getId(), targetFund, "申购", purchaseAmount, purchaseShares, netValue, transactionTime));
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
                } else {
                    if (holding == null || holding.getTotalShares().compareTo(BigDecimal.ONE) < 0) continue;
                    BigDecimal redeemShares = holding.getTotalShares().multiply(BigDecimal.valueOf(Math.random() * 0.5 + 0.1)).setScale(2, RoundingMode.DOWN);
                    if (redeemShares.compareTo(BigDecimal.ZERO) <= 0) continue;
                    BigDecimal redeemAmount = redeemShares.multiply(netValue).setScale(2, RoundingMode.HALF_UP);
                    customerTransactions.add(createTransaction(customer.getId(), targetFund, "赎回", redeemAmount, redeemShares, netValue, transactionTime));
                    holding.setTotalShares(holding.getTotalShares().subtract(redeemShares));
                }
                holding.setLastUpdateDate(transactionTime);
                customerHoldings.put(targetFund.getFundCode(), holding);
            }
            // b. 每个并行的map任务，返回一个包含计算结果的Map
            Map<String, Object> result = new HashMap<>();
            result.put("transactions", customerTransactions);
            result.put("holdings", new ArrayList<>(customerHoldings.values()));
            return result;
        }).toList(); // .tolist()会等待所有并行任务完成，并收集结果

        // --- 3. 统一收集并串行入库 ---
        System.out.println("【演绎】并行模拟计算完成，开始汇总结果...");

        List<FundTransaction> allNewTransactions = new ArrayList<>();
        List<CustomerHolding> allFinalHoldings = new ArrayList<>();

        // c. 遍历收集到的结果，汇总到主列表中
        for (Map<String, Object> result : simulationResults) {
            allNewTransactions.addAll((List<FundTransaction>) result.get("transactions"));
            allFinalHoldings.addAll((List<CustomerHolding>) result.get("holdings"));
        }

        System.out.println("【演绎】结果汇总完成，共生成 " + allNewTransactions.size() + " 条交易。开始串行批量写入数据库...");

        // d. 由主线程统一、串行地将最终结果批量写入数据库
        if (!allNewTransactions.isEmpty()) {
            fundTransactionService.saveBatch(allNewTransactions, 2000);
            System.out.println("【演绎】成功批量插入 " + allNewTransactions.size() + " 条交易记录。");
        }
        if (!allFinalHoldings.isEmpty()) {
            List<Long> customerIds = allCustomers.stream().map(Customer::getId).collect(Collectors.toList());
            customerHoldingService.remove(new QueryWrapper<CustomerHolding>().in("customer_id", customerIds));
            customerHoldingService.saveBatch(allFinalHoldings, 2000);
            System.out.println("【演绎】成功批量更新/插入 " + allFinalHoldings.size() + " 条持仓记录。");
        }

        // --- 4. 触发标签刷新 ---
        System.out.println("【演绎】数据入库完成，开始调用画像刷新服务...");
        try {
            tagRefreshService.refreshAllTagsAtomically(); // 标签刷新依然使用我们之前的并发实现
        } catch (Exception e) {
            System.err.println("【演绎】调用全量画像刷新服务时发生异常: " + e.getMessage());
            e.printStackTrace();
        }

        return "【演绎】任务完成！在 " + days + " 天的模拟中，共为 " + allCustomers.size() +
                " 位客户并行生成了 " + allNewTransactions.size() + " 笔新交易，并已触发全量画像刷新任务。";
    }

    private FundTransaction createTransaction(Long customerId, FundInfo fund, String type, BigDecimal amount, BigDecimal shares, BigDecimal price, LocalDateTime transactionTime) {
        // ... 此方法逻辑不变
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