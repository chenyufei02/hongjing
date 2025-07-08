package com.whu.hongjing.service.impl;

import com.github.javafaker.Faker;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
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
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * V5 最终版：交易模拟采用手动管理的线程池实现，并通过“计算/IO分离”模型解决事务传递问题，实现极致性能与稳定性。
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
    @Lazy
    private TagRefreshService tagRefreshService;
    @Autowired
    private MockDataWriterService mockDataWriterService;

    private static final String[] OCCUPATIONS = {
            "软件工程师", "项目经理", "产品经理", "数据分析师", "教师",
            "医生", "护士", "律师", "会计师", "设计师",
            "公务员", "销售经理", "市场专员", "运营专员", "自由职业者"
    };

    /**
     * 生成客户
     * @param customerCount
     * @return java.lang.String
     * @author yufei
     * @since 2025/7/8
     */
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
     * 总调度方法，自身不带事务。负责协调“并发计算”和“事务性写入”两个阶段。
     */
    @Override
    public String simulateTradingDays(int days) {
        // --- 1. 数据准备 ---
            // 加载客户数据
        System.out.println("【演绎】开始，准备加载初始数据...");
        List<Customer> allCustomers = customerService.list();
            // 加载基金数据
        Map<String, FundInfo> fundInfoMap = fundInfoService.list().stream()
                .collect(Collectors.toMap(FundInfo::getFundCode, Function.identity()));
        if (allCustomers.isEmpty() || fundInfoMap.isEmpty()) {
            return "【演绎】任务中止：请先确保数据库中存在客户和基金数据。";
        }
        List<FundInfo> allFunds = new ArrayList<>(fundInfoMap.values());


        // --- 2. 【核心改造】第一阶段：并发计算所有客户的模拟交易数据 ---
        int corePoolSize = Runtime.getRuntime().availableProcessors();
        ThreadFactory calcThreadFactory = new ThreadFactoryBuilder().setNameFormat("mock-data-thread-%d").build();
        ExecutorService calcExecutor = new ThreadPoolExecutor(
            corePoolSize,
            corePoolSize * 5,
            1200L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(20480),
            calcThreadFactory,
            new ThreadPoolExecutor.CallerRunsPolicy()
        );

        List<Future<Map<String, Object>>> futures = new ArrayList<>();  // 用来保存所有的更新后的持仓表和交易表
        System.out.println("【演绎-步骤1】向 " + corePoolSize*6 + " 个线程提交 " + allCustomers.size() + " 个客户的并行模拟任务...");

        for (Customer customer : allCustomers) {
            // 为每一个客户定义了一个返回其所有天数更新后的最新的Map 里面是其更新后的持仓表与交易表
            Callable<Map<String, Object>> task = () -> {
                // 这个任务在工作线程中执行，完全在内存中进行，不接触数据库
                Random random = ThreadLocalRandom.current();
                List<FundTransaction> customerTransactions = new ArrayList<>();  // （为每一个客户）创建一个保存交易数据的交易表
                Map<String, CustomerHolding> customerHoldings = new HashMap<>(); // （为每一个客户）创建一个保存客户持仓数据的MAP

                // ... 开始模拟数据生成 ...
                for (int i = 0; i < days; i++) {
                    // 每天有10%概率发生交易
                     if (random.nextInt(100) >= 10) continue;
                     // 获取当天的持仓数据 要根据customerHoldings.values()来创建这个持仓列表是因为之前的天数可能已经发生过了交易 已经有持仓数据了
                     List<CustomerHolding> currentHoldingsList = new ArrayList<>(customerHoldings.values());
                     currentHoldingsList.removeIf(h -> h.getTotalShares() == null || h.getTotalShares().compareTo(BigDecimal.ZERO) <= 0);

                     FundInfo targetFund;  // 目标交易的基金
                     boolean isPurchase;   // 记录是申购还是赎回操作

                    // 70%概率对已经持有的基金进行操作
                     if (!currentHoldingsList.isEmpty() && random.nextInt(100) < 70) {
                         CustomerHolding targetHolding = currentHoldingsList.get(random.nextInt(currentHoldingsList.size()));
                         targetFund = fundInfoMap.get(targetHolding.getFundCode());
                         // 70%是申购操作
                         isPurchase = random.nextInt(100) < 70;
                     // 30%概率在所有基金里随机操作（只能申购 因为新基金）
                     } else {
                         targetFund = allFunds.get(random.nextInt(allFunds.size()));
                         isPurchase = true;
                     }
                     if (targetFund == null || (targetFund.getNetValue() != null && targetFund.getNetValue().compareTo(BigDecimal.ZERO) <= 0)) continue;

                     // 获取基金净值
                     BigDecimal netValue = (targetFund.getNetValue() != null) ? targetFund.getNetValue() : new BigDecimal("1.0");
                     // 记录交易时间
                     LocalDateTime transactionTime = LocalDateTime.now().minusDays(days - i).withHour(10).withMinute(0).withSecond(0);
                     // 获取当前客户对当前要操作的基金的持仓情况holding 以便最后更新完了以后更新到（当前客户的持仓）总表customerHoldings里去
                     CustomerHolding holding = customerHoldings.get(targetFund.getFundCode());

                     // 开始进行申购操作
                     if (isPurchase) {
                         BigDecimal purchaseAmount = new BigDecimal(500 + random.nextInt(30000)); // 随机购买金额
                         BigDecimal purchaseShares = purchaseAmount.divide(netValue, 2, RoundingMode.DOWN); // 确认购买份额（模拟 采用前一天的收盘价）
                         // 保存交易据
                         customerTransactions.add(createTransaction(customer.getId(), targetFund, "申购", purchaseAmount, purchaseShares, netValue, transactionTime));
                         // 开始更新持仓数据（针对当前基金是第一次购买的新建持仓）
                         if (holding == null) {
                             holding = new CustomerHolding();
                             holding.setCustomerId(customer.getId());
                             holding.setFundCode(targetFund.getFundCode());
                             holding.setTotalShares(purchaseShares);
                             holding.setAverageCost(netValue);
                         // 开始更新持仓数据（针对当前基金已有持仓 累加老持仓数据）
                         } else {
                             BigDecimal oldTotalCost = holding.getAverageCost().multiply(holding.getTotalShares());
                             BigDecimal newTotalCost = oldTotalCost.add(purchaseAmount);
                             BigDecimal newTotalShares = holding.getTotalShares().add(purchaseShares);
                             holding.setTotalShares(newTotalShares);
                             holding.setAverageCost(newTotalCost.divide(newTotalShares, 4, RoundingMode.HALF_UP));
                         }
                     // 如果是赎回操作
                     } else {
                         if (holding == null || holding.getTotalShares().compareTo(BigDecimal.ONE) < 0) continue;
                         // 按0.1-0.6的随机比例赎回的份额
                         BigDecimal redeemShares = holding.getTotalShares().multiply(BigDecimal.valueOf(random.nextDouble() * 0.5 + 0.1)).setScale(2, RoundingMode.DOWN);
                         if (redeemShares.compareTo(BigDecimal.ZERO) <= 0) continue;
                         // 根据份额*净值（昨天的收盘价模拟）确定赎回的金额
                         BigDecimal redeemAmount = redeemShares.multiply(netValue).setScale(2, RoundingMode.HALF_UP);
                         // 保存交易数据
                         customerTransactions.add(createTransaction(customer.getId(), targetFund, "赎回", redeemAmount, redeemShares, netValue, transactionTime));
                         // 赎回一定是已有持仓的数据 且只影响总份额 不影响平均成本 因此只用修改总份额
                         holding.setTotalShares(holding.getTotalShares().subtract(redeemShares));
                     }
                     // 更新最近交易时间 和 当前客户对当前操作的基金的持仓数据（holding）
                     holding.setLastUpdateDate(transactionTime);
                     customerHoldings.put(targetFund.getFundCode(), holding);  // 将当前客户当天更新过的目标基金的持仓数据put进当前用户的总持仓表（key是基金code）
                        // （交易数据已经在申购和赎回方法内部提前添加到了当前用户的交易数据表）
                } // 到这里一天循环执行完毕

                Map<String, Object> result = new HashMap<>();
                result.put("customerId", customer.getId()); // 把customerId也放进结果
                result.put("transactions", customerTransactions);  // 当前用户所有天更新后的交易表
                result.put("holdings", new ArrayList<>(customerHoldings.values())); // 当前用户所有天更新过的持仓表
                return result;  // 当前用户的交易表和持仓表一起打包返回结果 这里一个result就是一个客户所有天更新后的全部数据
            };
            futures.add(calcExecutor.submit(task));  // 返回的是一个Future<Map<String, Object>>对象，也就是一个客户的更新后的数据，添加到总futures表格里
            // 此时针对当前用户的所有天的更新任务执行完毕， 等所有用户的任务都执行完毕以后线程并发也结束了，下面娥保存数据完全依靠单线程SaveBatch
        }
        calcExecutor.shutdown();

        // --- 3. 第二阶段：并发性写入 ---
        // a. 创建第二个独立的线程池，专门用于执行数据库IO操作
        // 创建一个列表来收集写入任务的Future，以便后续检查结果
        List<Future<Void>> writerFutures = new ArrayList<>();
        ThreadFactory writerThreadFactory = new ThreadFactoryBuilder().setNameFormat("mock-writer-thread-%d").build();
        ExecutorService writerExecutor = new ThreadPoolExecutor(
            corePoolSize,
            corePoolSize * 5,
            1200L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(20480),
            writerThreadFactory,
            new ThreadPoolExecutor.CallerRunsPolicy()
        );

        try {
            System.out.println("【演绎-步骤2】等待所有计算任务完成，并向写入线程池提交独立的写入任务...");

            // a. 汇总所有线程的计算结果
            for (Future<Map<String, Object>> future : futures) {

                // 一个客户的更新后的数据
                Map<String, Object> result = future.get();
                // 当前客户的ID
                Long customerId = (Long) result.get("customerId");
                // 从result Map中取出单独保存所有客户的所有交易数据的结果（去掉了MAP里的String "transactions"）
                 List<FundTransaction> transactions = (List<FundTransaction>) result.get("transactions");
                 // 从result Map中取出单独保存所有客户的所有持仓数据的结果（去掉了MAP里的String "holdings"）
                 List<CustomerHolding> holdings = (List<CustomerHolding>) result.get("holdings");

                // c. 为这个客户创建一个独立的写入任务(Runnable)，并提交给写入线程池
                Callable<Void> writeTask = () -> {
                    mockDataWriterService.saveCustomerDataInTransaction(customerId, transactions, holdings);
                    return null; // Callable需要一个返回值，成功时返回null即可
                };
                // 提交任务，并把返回的Future保存起来
                writerFutures.add(writerExecutor.submit(writeTask));
            }
        }catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            return "【演绎】任务失败：在等待并行计算结果时发生错误。 " + e.getMessage();
        } finally {
            // d. 确保两个线程池都被关闭
            if (!calcExecutor.isTerminated()) calcExecutor.shutdownNow();
        }

        // e. 等待所有写入任务完成
        writerExecutor.shutdown();
        int successCount = 0;
        try {
            for (Future<Void> future : writerFutures) {
                // future.get()会阻塞，直到任务完成。如果任务内部有异常，这里会重新抛出！
                future.get();
                successCount++;
            }
            System.out.println("【演绎-步骤3】所有 " + successCount + " 个写入任务均已成功完成！");
        } catch (InterruptedException | ExecutionException e) {
            // 只要有一个任务失败，我们就能在这里捕获到异常
            System.err.println("【演绎】严重错误：在执行并发写入时，至少有一个任务失败！");
            e.printStackTrace(); // 打印出根本原因
            Thread.currentThread().interrupt();
            return "【演绎】任务失败：并发写入数据库时发生错误，详情请查看控制台日志。";
        } finally {
             if (!writerExecutor.isTerminated()) writerExecutor.shutdownNow();
        }


        // --- 4. 触发独立的标签刷新任务 ---
        System.out.println("【演绎-步骤3】数据已成功提交，开始调用独立的画像刷新服务...");
        try {
            tagRefreshService.refreshAllTagsAtomically();
        } catch (Exception e) {
            System.err.println("【演绎】调用全量画像刷新服务时发生异常: " + e.getMessage());
            e.printStackTrace();
        }
        return "【演绎】任务完成！数据模拟和画像刷新均已触发。";
    }

    // createTransaction 辅助方法保持不变
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