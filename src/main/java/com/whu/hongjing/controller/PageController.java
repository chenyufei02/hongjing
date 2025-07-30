package com.whu.hongjing.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.hongjing.constants.TaggingConstants;
import com.whu.hongjing.enums.RiskLevelEnum;
import com.whu.hongjing.pojo.dto.CustomerDTO;
import com.whu.hongjing.pojo.dto.CustomerUpdateDTO;
import com.whu.hongjing.pojo.entity.*;
import com.whu.hongjing.pojo.vo.*;
import com.whu.hongjing.service.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 专门负责返回页面视图的控制器
 */
@Controller
public class PageController {

    @Autowired
    private CustomerService customerService;
    @Autowired
    private CustomerHoldingService customerHoldingService;
    @Autowired
    private FundInfoService fundInfoService;
    @Autowired
    private CustomerTagRelationService customerTagRelationService;
    @Autowired
    private FundTransactionService fundTransactionService;
    @Autowired
    private RiskAssessmentService riskAssessmentService;
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 显示主页（工作台）
     */
    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("activeUri", "/");
        return "index";  // SpringBoot的视图解析器会根据这个返回的字符串，找到templates文件夹下名为index.html的文件，并将其内容渲染成最终的网页。
    }


// ===================================客户管理===================================
    /**
     * 显示客户管理列表页面
     */
    @GetMapping("/customer/list")
    public String customerList(Model model,
           @RequestParam(value = "page", defaultValue = "1") int pageNum,
           @RequestParam(value = "size", defaultValue = "10") int pageSize,
           @RequestParam(value = "id", required = false) Long customerId,
           @RequestParam(value = "name", required = false) String name,
           @RequestParam(value = "idNumber", required = false) String idNumber,
           @RequestParam(value = "tagName", required = false) String tagName)
    {
        // page对象的四个主要属性： 总记录数 总页数 当前页数 每页显示的记录数。 下面只设置了当前页数（pageNum）和每页显示的记录数(pageSize)
        // 每次点击的时候就会传入新的pageNum 读到新的指定页
        Page<Customer> customerPage = new Page<>(pageNum, pageSize);  // 初始的时候分别是默认显示第1页和10条数据 因此page里已经有了分页要求的参数

        // 根据条件查询客户，返回的是查询过后的完整的customerPage，在查询的方法里添加上了总记录数和总页数的两个属性。
        customerService.getCustomerPage(customerPage, customerId, name, idNumber, tagName);

        int startPage = 1, endPage = (int) customerPage.getPages();

        if (customerPage.getPages() > 5) {
            startPage = Math.max(1, (int)customerPage.getCurrent() - 2);
            endPage = Math.min((int)customerPage.getPages(), startPage + 4);
            // 输入了更大的页的时候也只能跳转到最后一页
            if (endPage > customerPage.getPages()) {
                endPage = (int) customerPage.getPages();
            }
            if (endPage - startPage < 4) {
                startPage = Math.max(1, endPage - 4);
            }
        }

        model.addAttribute("customerPage", customerPage);
        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);
        model.addAttribute("activeUri", "/customer/list");
        model.addAttribute("id", customerId);
        model.addAttribute("name", name);
        model.addAttribute("idNumber", idNumber);
        model.addAttribute("tagName", tagName);

        return "customer/list";
    }

    /**
     * 显示新增客户的表单页面
     */
    @GetMapping("/customer/add")
    public String showAddForm(Model model)
    {
        model.addAttribute("customerDTO", new CustomerDTO());
        model.addAttribute("activeUri", "/customer/list");
        return "customer/add";  // // SpringBoot的解析器后会先找到templates文件夹下customer包里add.html的文件，并将其渲染成新增客户的页面。
        // 当用户在新增客户的页面里点击提交按钮以后 前段的script脚本才会自动跳转到后端的/api/customer/add 里的POST请求 用户填好的DTO对象传入提交。
    }

    /**
     * 显示编辑客户的表单页面
     */
    @GetMapping("/customer/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model,
           @RequestParam(required = false) String name,
           @RequestParam(required = false) String idNumber,
           @RequestParam(required = false) String tagName)
    {
        Customer customer = customerService.getCustomerById(id);
        if (customer == null) {
            return "redirect:/customer/list";
        }
        CustomerUpdateDTO dto = new CustomerUpdateDTO();
        BeanUtils.copyProperties(customer, dto);
        model.addAttribute("customerUpdateDTO", dto);
        model.addAttribute("activeUri", "/customer/list");
        model.addAttribute("backUrl", buildBackUrl(name, idNumber, tagName, null));
        return "customer/edit";
    }

    // 客户管理里删除不用专门的表单页面，详情的表单页面由于内容较多放在了最后。


// ===================================基金管理===================================
    /**
     * 显示基金管理列表页面
     */
    @GetMapping("/fund/list")
    public String fundList(Model model,
           @RequestParam(value = "page", defaultValue = "1") int pageNum,
           @RequestParam(value = "size", defaultValue = "10") int pageSize,
           @RequestParam(required = false) String fundCode,
           @RequestParam(required = false) String fundName,
           @RequestParam(required = false) String fundType,
           @RequestParam(required = false) Integer riskScore)
    {
        Page<FundInfo> fundPage = new Page<>(pageNum, pageSize);

        // 核心查询方法
        fundInfoService.getFundInfoPage(fundPage, fundCode, fundName, fundType, riskScore);

        int startPage = 1, endPage = (int) fundPage.getPages();
        if (fundPage.getPages() > 5) {
            startPage = Math.max(1, (int)fundPage.getCurrent() - 2);
            endPage = Math.min((int)fundPage.getPages(), startPage + 4);
        }
        model.addAttribute("fundPage", fundPage);
        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);
        model.addAttribute("activeUri", "/fund/list");
        model.addAttribute("fundCode", fundCode);
        model.addAttribute("fundName", fundName);
        model.addAttribute("fundType", fundType);
        model.addAttribute("riskScore", riskScore);
        List<String> fundTypes = fundInfoService.list().stream().map(FundInfo::getFundType).distinct().sorted().collect(Collectors.toList());
        model.addAttribute("fundTypes", fundTypes);
        return "fund/list";
    }


// ===================================持仓管理===================================
    /**
     * 显示持仓管理列表页面
     */
    @GetMapping("/holding/list")
    public String holdingList(Model model,
            @RequestParam(value = "page", defaultValue = "1") int pageNum,
            @RequestParam(value = "size", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String fundCode,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder)
    {
        Page<CustomerHoldingVO> holdingPage = new Page<>(pageNum, pageSize);

        // 根据条件分页查询持仓页面
        customerHoldingService.getHoldingPage(holdingPage, customerName, fundCode, sortField, sortOrder);

        int startPage = 1, endPage = (int) holdingPage.getPages();
        if (holdingPage.getPages() > 5) {
            startPage = Math.max(1, (int)holdingPage.getCurrent() - 2);
            endPage = Math.min((int)holdingPage.getPages(), startPage + 4);
        }
        model.addAttribute("holdingPage", holdingPage);
        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);
        model.addAttribute("activeUri", "/holding/list");
        model.addAttribute("customerName", customerName);
        model.addAttribute("fundCode", fundCode);
        model.addAttribute("sortField", sortField);
        model.addAttribute("sortOrder", sortOrder);
        model.addAttribute("reversedSortOrder", "asc".equals(sortOrder) ? "desc" : "asc");
        return "holding/list";
    }

// ===================================交易管理===================================
    /**
     * 显示交易管理列表页面
     */
    @GetMapping("/transaction/list")
    public String transactionList(Model model,
            @RequestParam(value = "page", defaultValue = "1") int pageNum,
            @RequestParam(value = "size", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String fundCode,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder)
    {
        Page<FundTransactionVO> transactionPage = new Page<>(pageNum, pageSize);

        fundTransactionService.getTransactionPage(transactionPage, customerName, fundCode, transactionType, sortField, sortOrder);

        int startPage = 1, endPage = (int) transactionPage.getPages();

        if (transactionPage.getPages() > 5) {
            startPage = Math.max(1, (int)transactionPage.getCurrent() - 2);
            endPage = Math.min((int)transactionPage.getPages(), startPage + 4);
        }
        model.addAttribute("transactionPage", transactionPage);
        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);
        model.addAttribute("activeUri", "/transaction/list");
        model.addAttribute("customerName", customerName);
        model.addAttribute("fundCode", fundCode);
        model.addAttribute("transactionType", transactionType);
        model.addAttribute("sortField", sortField);
        model.addAttribute("sortOrder", sortOrder);
        model.addAttribute("reversedSortOrder", "asc".equals(sortOrder) ? "desc" : "asc");
        return "transaction/list";
    }


// ===================================风险管理===================================
    /**
     * 显示风险管理列表页面
     */
    @GetMapping("/risk/list")
    public String riskList(Model model,
            @RequestParam(value = "page", defaultValue = "1") int pageNum,
            @RequestParam(value = "size", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String actualRiskLevel,
            @RequestParam(required = false) String riskDiagnosis,
            @RequestParam(required = false, defaultValue = "id") String sortField,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder)
    {
        Page<RiskAssessmentVO> assessmentPage = new Page<>(pageNum, pageSize);
        riskAssessmentService.getAssessmentPage(assessmentPage, customerName, riskLevel, actualRiskLevel, riskDiagnosis, sortField, sortOrder);
        int startPage = 1, endPage = (int) assessmentPage.getPages();
        if (assessmentPage.getPages() > 5) {
            startPage = Math.max(1, (int)assessmentPage.getCurrent() - 2);
            endPage = Math.min((int)assessmentPage.getPages(), startPage + 4);
        }
        model.addAttribute("riskDiagnoses", List.of(TaggingConstants.LABEL_DIAGNOSIS_OVERWEIGHT, TaggingConstants.LABEL_DIAGNOSIS_MATCH, TaggingConstants.LABEL_DIAGNOSIS_UNDERWEIGHT));
        model.addAttribute("riskLevels", Arrays.stream(RiskLevelEnum.values()).map(RiskLevelEnum::getLevelName).collect(Collectors.toList()));
        model.addAttribute("assessmentPage", assessmentPage);
        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);
        model.addAttribute("activeUri", "/risk/list");
        model.addAttribute("customerName", customerName);
        model.addAttribute("riskLevel", riskLevel);
        model.addAttribute("actualRiskLevel", actualRiskLevel);
        model.addAttribute("riskDiagnosis", riskDiagnosis);
        model.addAttribute("sortField", sortField);
        model.addAttribute("sortOrder", sortOrder);
        model.addAttribute("reversedSortOrder", "asc".equals(sortOrder) ? "desc" : "asc");
        return "risk/list";
    }


// ===================================标签管理===================================
    /**
     * 显示标签管理页面
     */
    @GetMapping("/tag/list")
    public String tagList(Model model) throws JsonProcessingException
    {
        List<TagVO> allTags = customerTagRelationService.getTagStats();
        List<String> categoryOrder = List.of(
            TaggingConstants.CATEGORY_AGE, TaggingConstants.CATEGORY_GENDER, TaggingConstants.CATEGORY_OCCUPATION,
            TaggingConstants.CATEGORY_STYLE, TaggingConstants.CATEGORY_ASSET, TaggingConstants.CATEGORY_RECENCY,
            TaggingConstants.CATEGORY_FREQUENCY, TaggingConstants.CATEGORY_RISK_DECLARED,
            TaggingConstants.CATEGORY_RISK_ACTUAL, TaggingConstants.CATEGORY_RISK_DIAGNOSIS);
        Map<String, List<TagVO>> groupedTags = categoryOrder.stream().collect(Collectors.toMap(
                Function.identity(),
                cat -> allTags.stream().filter(tag -> cat.equals(tag.getTagCategory())).collect(Collectors.toList()),
                (e1, e2) -> e1, LinkedHashMap::new));
        model.addAttribute("groupedTags", groupedTags);
        model.addAttribute("groupedTagsJson", objectMapper.writeValueAsString(groupedTags));
        model.addAttribute("activeUri", "/tag/list");
        return "tag/list";
    }


// ===================================盈亏管理===================================
    /**
     * 显示客户盈亏管理页面
     */
    @GetMapping("/profitloss/list")
    public String profitLossList(Model model,
             @RequestParam(value = "page", defaultValue = "1") int pageNum,
             @RequestParam(value = "size", defaultValue = "10") int pageSize,
             @RequestParam(required = false) Long customerId,
             @RequestParam(required = false) String customerName,
             @RequestParam(required = false, defaultValue = "totalProfitLoss") String sortField,
             @RequestParam(required = false, defaultValue = "desc") String sortOrder)
    {
        Page<ProfitLossVO> page = new Page<>(pageNum, pageSize);
        // 按照指定的参数筛选以后排序 返回排序后的page页面
        customerService.getProfitLossPage(page, customerId, customerName, sortField, sortOrder);

        int startPage = 1, endPage = (int) page.getPages();
        if (page.getPages() > 5) {
            startPage = Math.max(1, (int)page.getCurrent() - 2);
            endPage = Math.min((int)page.getPages(), startPage + 4);
        }
        model.addAttribute("statsPage", page);
        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);
        model.addAttribute("activeUri", "/profitloss/list");
        model.addAttribute("customerId", customerId);
        model.addAttribute("customerName", customerName);
        model.addAttribute("sortField", sortField);
        model.addAttribute("sortOrder", sortOrder);
        model.addAttribute("reversedSortOrder", "asc".equals(sortOrder) ? "desc" : "asc");
        return "profitloss/list";
    }


// ===================================统计仪表盘===================================
    /**
     * 显示统计仪表盘页面
     */
    @GetMapping("/visualization/dashboard")
    public String showDashboard(Model model)
    {
        model.addAttribute("activeUri", "/visualization/dashboard");
        return "visualization/dashboard";
    }


// ===================================补充：客户管理【详情页】===================================
    /**
     * 显示客户详情页面，加载所有必要数据
 */
    @GetMapping("/customer/detail/{id}")
    public String showDetailView(
            @PathVariable Long id, Model model,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String idNumber,
            @RequestParam(required = false) String tagName,
            @RequestParam(required = false) String returnUrl)
    {

        // 1. 获取客户基础数据
        Customer customer = customerService.getCustomerById(id);
        if (customer == null) {
            return "redirect:/customer/list";
        }
        model.addAttribute("customer", customer);
        model.addAttribute("stats", customerService.getProfitLossVO(id));
        model.addAttribute("activeUri", "/customer/list");
        model.addAttribute("backUrl", buildBackUrl(name, idNumber, tagName, returnUrl));

        // 2. 获取并处理所有标签，用于分组展示
            // 这里的customerTagRelationService.list是MP提供的方法，将指定的查询条件构造器应用到指定的表里进行查询，结果组装成列表
        List<CustomerTagRelation> allTags = customerTagRelationService.list(new QueryWrapper<CustomerTagRelation>().eq("customer_id", id));

        model.addAttribute("basicProfileTags", filterTagsByCategory(allTags, List.of(TaggingConstants.CATEGORY_AGE, TaggingConstants.CATEGORY_GENDER, TaggingConstants.CATEGORY_OCCUPATION)));
        model.addAttribute("assetTags", filterTagsByCategory(allTags, List.of(TaggingConstants.CATEGORY_ASSET)));
        model.addAttribute("styleTags", filterTagsByCategory(allTags, List.of(TaggingConstants.CATEGORY_STYLE)));
        model.addAttribute("tradingHabitTags", sortAndFilterTags(allTags, List.of(TaggingConstants.CATEGORY_RECENCY, TaggingConstants.CATEGORY_FREQUENCY)));
        model.addAttribute("riskProfileTags", sortAndFilterTags(allTags, List.of(TaggingConstants.CATEGORY_RISK_DECLARED, TaggingConstants.CATEGORY_RISK_ACTUAL, TaggingConstants.CATEGORY_RISK_DIAGNOSIS)));

        // 3. 准备图表所需的数据
        prepareChartData(id, model);  // 投资分析图表
        prepareHistoricalData(id, model);  // 历史走势图表

        // 4. 获取Top 10持仓列表
        model.addAttribute("topHoldings", customerHoldingService.getTopNHoldings(id, 10));

        return "customer/detail";
    }





// =================================== 私有辅助方法 (Private Helper Methods) ===================================

    /**
     * 为客户详情页准备所有图表所需的数据，并将其添加到Spring MVC的Model中。
     * @return void
     * @author yufei
     * @since 2025/7/29
     */
    private void prepareChartData(Long customerId, Model model) {
        // 1. 获取客户当前的所有持仓记录。
        List<CustomerHolding> holdings = customerHoldingService.listByCustomerId(customerId);
        // 如果客户没有任何持仓，则设置空图表数据并直接返回，避免后续计算。
        if (holdings == null || holdings.isEmpty()) {
            setEmptyChartData(model);
            return;
        }

        // 2. 计算客户的总资产市值（M）。
        BigDecimal totalMarketValue = holdings.stream().map(CustomerHolding::getMarketValue).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        // 如果总市值为0或负数，同样视为无效数据，直接返回。
        if (totalMarketValue.compareTo(BigDecimal.ZERO) <= 0) {
            setEmptyChartData(model);
            return;
        }

        // 3. 准备关联数据，用于后续高效计算。
        // a. 提取所有持仓中不重复的基金代码。
        List<String> fundCodes = holdings.stream().map(CustomerHolding::getFundCode).distinct().collect(Collectors.toList());
        // b. 一次性从数据库中查询出所有相关的基金信息，并存入Map，避免在循环中重复查询（N+1问题）。
        Map<String, FundInfo> fundInfoMap = fundInfoService.listByIds(fundCodes).stream().collect(Collectors.toMap(FundInfo::getFundCode, Function.identity()));

        // 4. 计算“资产类别分布图”所需的数据。
        // 按基金类型(fundType)对持仓进行分组，并对每个分组的市值(marketValue)进行求和。
        Map<String, BigDecimal> assetAllocationData = holdings.stream()
                .filter(h -> fundInfoMap.get(h.getFundCode()) != null && h.getMarketValue() != null && fundInfoMap.get(h.getFundCode()).getFundType() != null)
                .collect(Collectors.groupingBy(
                    // 分组依据：从fundInfoMap中获取基金类型
                    h -> fundInfoMap.get(h.getFundCode()).getFundType(),
                    // 聚合操作：对组内元素的marketValue求和
                    Collectors.reducing(BigDecimal.ZERO, CustomerHolding::getMarketValue, BigDecimal::add)));
        // 5. 计算“多维风险洞察雷达图”所需的数据。
        // 调用一个独立的辅助方法来封装更复杂的风险指标计算逻辑。
        Map<String, BigDecimal> riskInsightData = calculateRiskInsightData(customerId, holdings, fundInfoMap, totalMarketValue);

        // 为图表定义统一的颜色映射
        Map<String, String> colorMap = new LinkedHashMap<>();
        colorMap.put("股票型", "#FF6384");
        colorMap.put("指数型", "#FF9F40");
        colorMap.put("混合型", "#FFCE56");
        colorMap.put("债券型", "#4BC0C0");
        colorMap.put("货币型", "#9966FF");
        colorMap.put("FOF", "#36A2EB");
        colorMap.put("QDII", "#C9CBCF");
        colorMap.put("Reits", "#8D6E63");
        // 为其他所有未明确定义的类型提供一个备用颜色列表
        List<String> fallbackColors = List.of("#E7E9ED", "#77DD77", "#FDFD96", "#836953", "#FFB347");
        int fallbackIndex = 0;
        for (String type : assetAllocationData.keySet()) {
            if (!colorMap.containsKey(type)) {
                colorMap.put(type, fallbackColors.get(fallbackIndex % fallbackColors.size()));
                fallbackIndex++;
            }
        }

        try {
            model.addAttribute("assetAllocationJson", objectMapper.writeValueAsString(assetAllocationData));
            model.addAttribute("riskInsightJson", objectMapper.writeValueAsString(riskInsightData));
            model.addAttribute("colorMapJson", objectMapper.writeValueAsString(colorMap));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            setEmptyChartData(model);
        }
    }

    /**
     * 【 为历史走势双曲线图和资金流图准备数据 】
     */
    private void prepareHistoricalData(Long customerId, Model model) {
        List<FundTransaction> transactions = fundTransactionService.list(
            new QueryWrapper<FundTransaction>().eq("customer_id", customerId).orderByAsc("transaction_time")
        );

        if (transactions == null || transactions.isEmpty()) {
            model.addAttribute("historicalDataJson", "{}");
            model.addAttribute("monthlyFlowJson", "{}");
            return;
        }

        // --- 计算1：双曲线图（资产总额 vs 累计净投入） ---
        Map<String, Map<String, BigDecimal>> historicalData = new LinkedHashMap<>();  // 存放最后每一天的计算结果，string为date MAP存储每天的累计净投入与资产的entry（string标识”assets“或"investment"）

        BigDecimal cumulativeInvestment = BigDecimal.ZERO;  // 初始化累计净投入值
        Map<String, BigDecimal> currentShares = new HashMap<>();  // 存储所有基金的已有持有份额

        for (FundTransaction tx : transactions) {
            String date = tx.getTransactionTime().toLocalDate().toString();
            String fundCode = tx.getFundCode();

            // 1： 更新累计净投入 (现金流口径)
            if ("申购".equals(tx.getTransactionType())) {
                cumulativeInvestment = cumulativeInvestment.add(tx.getTransactionAmount());
            } else {
                cumulativeInvestment = cumulativeInvestment.subtract(tx.getTransactionAmount());
            }

            // 2： 更新资产总额-更新份额
            BigDecimal shares = currentShares.getOrDefault(fundCode, BigDecimal.ZERO); // 获取当前基金的持有份额
            if ("申购".equals(tx.getTransactionType())) {
                currentShares.put(fundCode, shares.add(tx.getTransactionShares()));   // 将交易的份额添加至当前基金的总持有份额
            } else {
                currentShares.put(fundCode, shares.subtract(tx.getTransactionShares()));
            }

            // 2： 更新资产总额-更新总市值 ， 使用【所有】基金的【当前】份额 和 【当前这笔交易的】的成交价净值来估算总市值
                // （因为没有存储每只基金每一天的数据表格，对其他当天未发生过交易的基金，其当天的数值无法确定，只能用一个模拟的估值）
            BigDecimal totalMarketValue = BigDecimal.ZERO;
            for(Map.Entry<String, BigDecimal> entry : currentShares.entrySet()) {
                // 这里用当天交易的基金净值，去估算所有持仓的市值，是简化方案的核心：对所有基金的持仓份额entry 都用当前交易的成交价净值tx.getSharePrice()相乘模拟市值波动
                totalMarketValue = totalMarketValue.add(
                    entry.getValue().multiply(tx.getSharePrice() != null ? tx.getSharePrice() : BigDecimal.ONE)
                );
            }

            Map<String, BigDecimal> dailyData = new HashMap<>();
            dailyData.put("assets", totalMarketValue.setScale(2, RoundingMode.HALF_UP));
            dailyData.put("investment", cumulativeInvestment.setScale(2, RoundingMode.HALF_UP));
            historicalData.put(date, dailyData);
        }

        // --- 计算2：月度资金净流入/流出 ---
        Map<String, BigDecimal> monthlyFlowData = transactions.stream()
            .collect(Collectors.groupingBy(
                tx -> tx.getTransactionTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")),
                Collectors.mapping(
                    tx -> "申购".equals(tx.getTransactionType()) ? tx.getTransactionAmount() : tx.getTransactionAmount().negate(),
                    Collectors.reducing(BigDecimal.ZERO, BigDecimal::add)
                )
            ));

        // 按月份排序
        Map<String, BigDecimal> sortedMonthlyFlow = new LinkedHashMap<>();
        monthlyFlowData.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEachOrdered(e -> sortedMonthlyFlow.put(e.getKey(), e.getValue()));

        // --- 将数据转换为JSON ---
        try {
            model.addAttribute("historicalDataJson", objectMapper.writeValueAsString(historicalData));
            model.addAttribute("monthlyFlowJson", objectMapper.writeValueAsString(sortedMonthlyFlow));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            model.addAttribute("historicalDataJson", "{}");
            model.addAttribute("monthlyFlowJson", "{}");
        }
    }



    /**
     * 【 封装风险洞察雷达图的数据计算过程，统一风险指向性 】
     */
    private Map<String, BigDecimal> calculateRiskInsightData(Long customerId, List<CustomerHolding> holdings, Map<String, FundInfo> fundInfoMap, BigDecimal totalMarketValue) {
        // --- 基础数据计算 (不变) ---
        BigDecimal highRiskValue = filterAndSum(holdings, fundInfoMap, List.of("股票型", "指数型"));
        BigDecimal midHighRiskValue = filterAndSum(holdings, fundInfoMap, List.of("混合型"));
        BigDecimal lowRiskValue = filterAndSum(holdings, fundInfoMap, List.of("货币型"));
        BigDecimal topHoldingValue = holdings.stream().map(CustomerHolding::getMarketValue).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal allInvestableValue = highRiskValue.add(midHighRiskValue).add(filterAndSum(holdings, fundInfoMap, List.of("债券型")));

        // --- 核心逻辑 ---
        Map<String, BigDecimal> riskInsightData = new LinkedHashMap<>();
        BigDecimal hundred = new BigDecimal(100);

        // 维度1：风险暴露度
        riskInsightData.put("风险暴露度", highRiskValue.add(midHighRiskValue).divide(totalMarketValue, 4, RoundingMode.HALF_UP).multiply(hundred));
        // 维度2：投资进攻性
        riskInsightData.put("投资进攻性", allInvestableValue.compareTo(BigDecimal.ZERO) > 0 ? highRiskValue.divide(allInvestableValue, 4, RoundingMode.HALF_UP).multiply(hundred) : BigDecimal.ZERO);
        // 维度3：持仓集中度
        riskInsightData.put("持仓集中度", topHoldingValue.divide(totalMarketValue, 4, RoundingMode.HALF_UP).multiply(hundred));

        // 维度4：“知行不匹配度”
        String diagnosisTag = customerTagRelationService.list(new QueryWrapper<CustomerTagRelation>().eq("customer_id", customerId).eq("tag_category", TaggingConstants.CATEGORY_RISK_DIAGNOSIS))
                .stream().map(CustomerTagRelation::getTagName).findFirst().orElse("");
        riskInsightData.put("行为激进程度", calculateAggressivenessScore(diagnosisTag));
        // 维度5：“流动性风险”
        // 计算(100 - 流动性储备百分比)
        BigDecimal liquidityReserve = lowRiskValue.divide(totalMarketValue, 4, RoundingMode.HALF_UP).multiply(hundred);
        riskInsightData.put("流动性风险", hundred.subtract(liquidityReserve));

        return riskInsightData;
    }

    // 根据一组标签名字列表里的任意多个标签名，将数据筛选成只保留这些标签名的数据
    private List<CustomerTagRelation> filterTagsByCategory(List<CustomerTagRelation> allTags, List<String> categories) {
        // 只有包含在传进来的目标标签名列表里的标签数据，才能通过筛选装进列表返回。
        return allTags.stream().filter(t -> categories.contains(t.getTagCategory())).collect(Collectors.toList());
    }

    private List<CustomerTagRelation> sortAndFilterTags(List<CustomerTagRelation> allTags, List<String> orderedCategories) {
        return allTags.stream().filter(t -> orderedCategories.contains(t.getTagCategory()))
                .sorted(Comparator.comparing(t -> orderedCategories.indexOf(t.getTagCategory()))).collect(Collectors.toList());
    }

    private void setEmptyChartData(Model model) {
        model.addAttribute("assetAllocationJson", "{}");
        model.addAttribute("riskInsightJson", "{}");
        model.addAttribute("colorMapJson", "{}");
    }

    /**
     * 计算客户的行为激进程度分数
     * @param diagnosisTag 客户的风险诊断标签
     * @return 0 (保守/未知), 50 (匹配), 100 (激进)
     */
    private BigDecimal calculateAggressivenessScore(String diagnosisTag) {
        // 行为激进，得100分
        if (TaggingConstants.LABEL_DIAGNOSIS_OVERWEIGHT.equals(diagnosisTag)) {
            return new BigDecimal(100);
        }
        // 知行合一，得50分
        if (TaggingConstants.LABEL_DIAGNOSIS_MATCH.equals(diagnosisTag)) {
            return new BigDecimal(50);
        }
        // 行为保守或未知，都视为激进程度为0
        return BigDecimal.ZERO;
    }

    private BigDecimal filterAndSum(List<CustomerHolding> holdings, Map<String, FundInfo> fundInfoMap, List<String> types) {
        return holdings.stream()
            .filter(h -> {
                FundInfo info = fundInfoMap.get(h.getFundCode());
                if (info == null || info.getFundType() == null || h.getMarketValue() == null) {
                    return false;
                }
                // 核心修正：判断基金类型字符串是否“包含”我们定义的任一关键词
                return types.stream().anyMatch(typeKeyword -> info.getFundType().contains(typeKeyword));
            })
            .map(CustomerHolding::getMarketValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String buildBackUrl(String name, String idNumber, String tagName, String returnUrl) {
        if (StringUtils.hasText(returnUrl)) return returnUrl;
        StringBuilder url = new StringBuilder("/customer/list?from=details");
        if (StringUtils.hasText(name)) url.append("&name=").append(name);
        if (StringUtils.hasText(idNumber)) url.append("&idNumber=").append(idNumber);
        if (StringUtils.hasText(tagName)) url.append("&tagName=").append(tagName);
        return url.toString();
    }



}