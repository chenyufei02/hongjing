package com.whu.hongjing.service.impl;

import com.github.javafaker.Faker;
import com.whu.hongjing.pojo.dto.RiskAssessmentSubmitDTO;
import com.whu.hongjing.pojo.entity.Customer;
import com.whu.hongjing.pojo.entity.CustomerHolding;
import com.whu.hongjing.pojo.entity.FundInfo;
import com.whu.hongjing.pojo.entity.FundTransaction;
import com.whu.hongjing.pojo.entity.CustomerTagRelation; // 导入
import com.whu.hongjing.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.*;
import java.util.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Function;
import java.util.stream.Collectors;

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
            String address = faker.address().state() + faker.address().city() + faker.address().streetName() +
                    faker.address().buildingNumber() + "号";
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
     * 【工具II：演绎】(最终版：智能决策 + 性能优化)
     */
    @Override
    @Transactional
    public String simulateTradingDays(int days) {
        System.out.println("【演绎】开始，准备加载初始数据...");
        // --- 1. 数据准备 ---
        Random random = new Random();

        // 【客户列表】mp的用法 只对已有的CS对象进行交易模拟 封装所有CS对象到列表 保存到内存
        // 后续获取客户就都从这里获取 不用再通过mp的.list()等方法操作数据库  提升性能
        List<Customer> allCustomers = customerService.list();

        // 【基金MAP】mp的用法 同上封装所有fundinfo对象到列表 保存到内存 后续获取客户就都从这里获取 不用再通过mp的.list()等方法操作数据库
        Map<String, FundInfo> fundInfoMap = fundInfoService.list().stream()
                .collect(Collectors.toMap(FundInfo::getFundCode, Function.identity())); // fundcode为key 对象本身为value的map

        // 不管是没有客户 还是没有基金数据 都无法直接生成后续的交易数据
        if (allCustomers.isEmpty() || fundInfoMap.isEmpty()) {
            return "【演绎】任务中止：请先确保数据库中存在客户和基金数据。";
        }

        // 这里获取基金信息的列表就直接通过内存里的MAP来进行了，不再去数据库用 fundInfoService.list()了 效率低
        List<FundInfo> allFunds = new ArrayList<>(fundInfoMap.values());  // 存了所有基金数据的列表

        // 【持仓MAP】key为客户id+基金id ， value为持仓数据本身。 同样一次性将某客户对某基金的持仓数据封装到了内存的MAP里 后续直接调用即可修改持仓数据。
        // 【核心优化】使用嵌套Map，实现O(1)复杂度的持仓查找
        Map<Long, Map<String, CustomerHolding>> holdingsByCustomer = customerHoldingService.list().stream()
                .collect(Collectors.groupingBy(CustomerHolding::getCustomerId,
                        Collectors.toMap(CustomerHolding::getFundCode, Function.identity())));


        // 【交易对象MAP】存交易对象的列表
        List<FundTransaction> newTransactions = new ArrayList<>();

        System.out.println("【演绎】初始数据加载完毕，进入内存模拟阶段...");
        // --- 2. 内存模拟 ---
        for (Customer customer : allCustomers) {
            // 【核心优化】直接获取当前客户的持仓Map，如果不存在则创建一个空的
            Map<String, CustomerHolding> customerHoldings = holdingsByCustomer.getOrDefault(customer.getId(), new HashMap<>());

            for (int i = 0; i < days; i++) {
                if (random.nextInt(100) >= 10) continue; // 每天10%概率交易

                //  优化 ：直接从客户自己的持仓Map中获取有效持仓列表
                // 获取当前客户在内存中的所有持仓（避免了与数据库交互），只有当持仓份额不为空且大于0的时候才会返回这条持仓数据 到 当前客户的持仓数据列表里。
                List<CustomerHolding> customerCurrentHoldings = new ArrayList<>(customerHoldings.values());
                customerCurrentHoldings.removeIf(h -> h.getTotalShares() == null || h.getTotalShares().compareTo(BigDecimal.ZERO) <= 0);

                FundInfo targetFund; // 目标操作的基金
                boolean isPurchase;  // 判断是申购还是赎回（false）

                // 【判断是赎回还是申购 获取目标基金的数据】
                // 【智能决策】先决定是申购操作还是赎回操作，并选出将操作的基金code（已有持仓70%从已有里选 30%和没有持仓的时候从所有总基金列表里选）
                if (!customerCurrentHoldings.isEmpty() && random.nextInt(100) < 70) {
                    // --- 决策A: 已经有持仓数据，且发生了70%的概率，对已有持仓进行操作 ---
                    CustomerHolding targetHolding = customerCurrentHoldings.get
                            (random.nextInt(customerCurrentHoldings.size()));  // 随机获取了一条持仓数据
                    targetFund = fundInfoMap.get(targetHolding.getFundCode()); // 获取当前持仓的基金的所有数据（包含净值在里面！）
                    isPurchase = random.nextBoolean(); // 50%申购, 50%赎回
                } else {
                    // --- 决策B: 没有持仓数据，或发生了30%的概率，对新的持仓进行操作：只能从所有的基金列表里进行申购 ---
                    targetFund = allFunds.get(random.nextInt(allFunds.size()));  // 通过funds的list随机获取一个目标的基金（funds的map不好随机）
                    isPurchase = true; // 必然是申购
                }
                if (targetFund == null) continue; // 健壮性检查 目标基金就是从列表里随机取的 一般不会为空 但还是加一个目标基金为空跳过此次交易

                // 【获取当前客户对当前基金的持仓数据】 直接从客户自己的持仓Map中获取单个基金的持仓
                CustomerHolding holding = customerHoldings.get(targetFund.getFundCode());

                // 【获取当前目标基金的净值】
                // 如果该基金有净值且已经跌破0 跳过此次交易操作
                if (targetFund.getNetValue() != null && targetFund.getNetValue().compareTo(BigDecimal.ZERO) <= 0) continue;
                BigDecimal netValue = (targetFund.getNetValue() != null) ? targetFund.getNetValue() : new BigDecimal("1.0");

                // 【获取交易时间】
                LocalDateTime transactionTime = LocalDateTime.now().minusDays(days - i).withHour(10).withMinute(0).withSecond(0);

                // 【根据申购或赎回 根据净值对持仓数据进行操作更新】
                // 如果是申购操作
                if (isPurchase) {
                    // --- a. 执行内存申购 --- 创建交易对象
                    BigDecimal purchaseAmount = new BigDecimal(500 + random.nextInt(5000));
                    BigDecimal purchaseShares = purchaseAmount.divide(netValue, 2, RoundingMode.DOWN);
                    FundTransaction tx = createTransaction(customer.getId(), targetFund,
                            "申购", purchaseAmount, purchaseShares, netValue, transactionTime);

                    // 知道了客户 基金 操作类型 金额 就可以计算出操作份额 从而发生新的交易记录
                    newTransactions.add(tx);
                    // 【更新持仓数据】
                    // 再看是初次申购还是继续申购（前面根据客户ID与基金code查出来的持仓记录是否为null），前者直接设置一条新的持仓属性，
                    if (holding == null) {
                        holding = new CustomerHolding();
                        holding.setCustomerId(customer.getId());
                        holding.setFundCode(targetFund.getFundCode());
                        holding.setTotalShares(purchaseShares);
                        holding.setAverageCost(netValue);
                    } else {
                        BigDecimal oldTotalCost = holding.getAverageCost().multiply(holding.getTotalShares());
                        BigDecimal newTotalCost = oldTotalCost.add(purchaseAmount); // 新持有成本
                        BigDecimal newTotalShares = holding.getTotalShares().add(purchaseShares); // 新份额

                        // 因为是对已有持仓对象进行操作，只改变了当前holding的 总份额 和 平均成本
                        holding.setTotalShares(newTotalShares);
                        holding.setAverageCost(newTotalCost.divide(newTotalShares, 4, RoundingMode.HALF_UP));
                    }
                    // 将更新后的持仓放回客户自己的Map（提取到申购与赎回的公共字段）

                    // 如果是赎回操作
                } else {
                    // --- b. 执行内存赎回 ---
                    // 没有持仓或份额已经<0 不能赎回 跳过此次交易
                    if (holding == null || holding.getTotalShares().compareTo(BigDecimal.ONE) < 0) continue;

                    // 赎回份额 = 原份额 * 随机赎回比例（设计在0.1-0.6之间 保留两位小数）
                    BigDecimal redeemShares = holding.getTotalShares().multiply(BigDecimal.valueOf(Math.random() * 0.5 + 0.1)).setScale(2, RoundingMode.DOWN);
                    if (redeemShares.compareTo(BigDecimal.ZERO) <= 0) continue;

                    // 赎回金额 = 赎回份额 * 当前基金净值
                    BigDecimal redeemAmount = redeemShares.multiply(netValue).setScale(2, RoundingMode.HALF_UP);
                    FundTransaction tx = createTransaction(customer.getId(), targetFund,
                            "赎回", redeemAmount, redeemShares, netValue, transactionTime);

                    // 发生新的交易记录
                    newTransactions.add(tx);
                    holding.setTotalShares(holding.getTotalShares().subtract(redeemShares));
                    // 赎回不影响平均持仓成本 因此不用更新averageCost
                    // 将更新后的持仓放回客户自己的Map（提取到申购与赎回的公共字段）
                }
                holding.setLastUpdateDate(transactionTime);
                customerHoldings.put(targetFund.getFundCode(), holding);
            }
            // 在处理完一个客户的所有天数后，将他的持仓状态明确更新回主Map
            holdingsByCustomer.put(customer.getId(), customerHoldings);
        }

        // --- 3. 批量写入 ---
        System.out.println("【演绎】内存模拟完成，开始批量写入数据库...");
        if (!newTransactions.isEmpty()) {
            fundTransactionService.saveBatch(newTransactions, 2000);
            System.out.println("【演绎】成功批量插入 " + newTransactions.size() + " 条交易记录。");
        }
        // 【核心优化】将嵌套Map平铺成List再进行批量更新
        List<CustomerHolding> allFinalHoldings = holdingsByCustomer.values().stream()
                .flatMap(map -> map.values().stream())
                .peek(h -> {
                    if (h.getAverageCost() == null) h.setAverageCost(BigDecimal.ZERO);
                    if (h.getLastUpdateDate() == null) h.setLastUpdateDate(LocalDateTime.now());
                })
                .collect(Collectors.toList());

        // 在写入数据库前，为所有持仓计算最新的市值
        System.out.println("【演绎】开始为 " + allFinalHoldings.size() + " 条最终持仓记录计算最新市值...");
        for (CustomerHolding holding : allFinalHoldings) {
            FundInfo fundInfo = fundInfoMap.get(holding.getFundCode());
            // 确保持有份额和基金净值都有效
            if (holding.getTotalShares() != null && fundInfo != null && fundInfo.getNetValue() != null) {
                BigDecimal marketValue = holding.getTotalShares()
                                                .multiply(fundInfo.getNetValue())
                                                .setScale(2, RoundingMode.HALF_UP);
                holding.setMarketValue(marketValue); // 设置计算出的市值
            } else {
                holding.setMarketValue(BigDecimal.ZERO); // 如果无法计算，则设置为0
            }

            // 顺便修复在平铺过程中可能丢失的默认值
            if (holding.getAverageCost() == null) holding.setAverageCost(BigDecimal.ZERO);
            if (holding.getLastUpdateDate() == null) holding.setLastUpdateDate(LocalDateTime.now());
        }
        System.out.println("【演绎】市值计算完成！");

        if (!allFinalHoldings.isEmpty()) {
            customerHoldingService.saveOrUpdateBatch(allFinalHoldings, 2000);
            System.out.println("【演绎】成功批量更新/插入 " + allFinalHoldings.size() + " 条持仓记录。");
        }

        // --- 4. 统一刷新标签 ---
        System.out.println("【演绎】数据入库完成，开始为所有客户并行计算标签...");

        // a. 最佳实践：使用ThreadPoolExecutor创建线程池
        int corePoolSize = Runtime.getRuntime().availableProcessors();
        ExecutorService executorService = new ThreadPoolExecutor(
                corePoolSize,
                corePoolSize * 2, // 允许一些额外的线程处理IO等待
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(allCustomers.size()), // 设置有界队列
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy() // 设置拒绝策略
        );

        // b. 并行计算：将每个客户的标签计算任务提交给线程池
        List<Future<List<CustomerTagRelation>>> futures = new ArrayList<>();
        for (Customer customer : allCustomers) {
            // 在这里把持仓数据从Map里取出来，传给任务
            // 从我们的大Map里获取当前客户的持仓子Map，如果不存在则返回一个空Map
            Map<String, CustomerHolding> customerHoldingsMap = holdingsByCustomer.getOrDefault(customer.getId(), Collections.emptyMap());
            // 将子Map的值（持仓对象）转为一个List
            List<CustomerHolding> customerHoldingsList = new ArrayList<>(customerHoldingsMap.values());

            Future<List<CustomerTagRelation>> future = executorService.submit(() ->
                    // 把持仓列表作为参数传进去
                    tagRefreshService.calculateTagsForCustomer(customer, customerHoldingsList)
            );
            futures.add(future);
        }

        // c. 收集所有并行计算的结果
        List<CustomerTagRelation> allNewTags = new ArrayList<>();
        for (Future<List<CustomerTagRelation>> future : futures) {
            try {
                allNewTags.addAll(future.get());
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("一个标签计算任务失败: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // d. 关闭线程池
        executorService.shutdown();
        System.out.println("【演绎】所有客户的标签已在内存中计算完毕，共 " + allNewTags.size() + " 个。");

        // e. 单线程、原子化地批量写入数据库，彻底解决死锁
        System.out.println("【演绎】开始将所有新标签原子化写入数据库...");
        try {
            tagRefreshService.refreshAllTagsAtomically(allNewTags);
        } catch (Exception e) {
            System.err.println("【演绎】标签原子化刷新失败: " + e.getMessage());
            e.printStackTrace();
        }

        return "【演绎】任务完成！在 " + days + " 天的模拟中，共为 " + allCustomers.size() +
                " 位客户生成了 " + newTransactions.size() + " 笔新交易，并已刷新所有相关客户的持仓与画像标签。";
    }

    /**
     * 创建交易记录对象的辅助方法，使代码更整洁
     */
    private FundTransaction createTransaction(Long customerId, FundInfo fund, String type, BigDecimal amount, BigDecimal shares, BigDecimal price, LocalDateTime transactionTime) {
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