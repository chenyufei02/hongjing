package com.whu.hongjing.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.hongjing.constants.TaggingConstants;
import com.whu.hongjing.pojo.dto.CustomerUpdateDTO;
import com.whu.hongjing.pojo.dto.FundInfoDTO;
import com.whu.hongjing.pojo.entity.Customer;
import com.whu.hongjing.pojo.entity.CustomerTagRelation;
import com.whu.hongjing.pojo.entity.FundInfo;
import com.whu.hongjing.pojo.vo.FundTransactionVO;
import com.whu.hongjing.service.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import com.whu.hongjing.pojo.dto.CustomerDTO;
import com.whu.hongjing.pojo.vo.ProfitLossVO;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import com.whu.hongjing.pojo.vo.CustomerHoldingVO;
import com.whu.hongjing.pojo.vo.RiskAssessmentVO;
import com.whu.hongjing.enums.RiskLevelEnum;
import java.util.Arrays;
import com.whu.hongjing.pojo.vo.TagVO;
import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * 专门负责返回页面视图的控制器
 */
@Controller
public class PageController {

    // 注入所有为页面准备数据所需要的Service
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
    // 注入 Spring Boot 默认配置好的 ObjectMapper
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 显示主页（工作台）
     */
    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("activeUri", "/");
        return "index";
    }

    /**
     * 显示客户列表页面 (将原来CustomerController里的页面逻辑搬到这里)
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

        Page<Customer> customerPage = new Page<>(pageNum, pageSize);
        customerService.getCustomerPage(customerPage, customerId, name, idNumber, tagName);

        int startPage = 1, endPage = (int) customerPage.getPages();
        if (customerPage.getPages() > 5) {
            startPage = Math.max(1, (int)customerPage.getCurrent() - 2);
            endPage = Math.min((int)customerPage.getPages(), startPage + 4);
            if (endPage == customerPage.getPages()) {
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
    public String showAddForm(Model model) {
        model.addAttribute("customerDTO", new CustomerDTO());
        model.addAttribute("activeUri", "/customer/list"); // 用于侧边栏高亮
        return "customer/add";
    }


    /**
     * 显示客户详情页面，加载基础信息、分组后的标签、盈亏统计，最终版Pro：支持高级分组与排序
     */
    @GetMapping("/customer/detail/{id}")
    public String showDetailView(
            @PathVariable Long id, Model model,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String idNumber,
            @RequestParam(required = false) String tagName,
            @RequestParam(required = false) String returnUrl)
    {
        Customer customer = customerService.getCustomerById(id);
        if (customer == null) {
            return "redirect:/customer/list";
        }

        // 1. 获取该客户的所有标签关系
        List<CustomerTagRelation> allTags = customerTagRelationService.list(
                new QueryWrapper<CustomerTagRelation>().eq("customer_id", id)
        );

        // 2. 【核心改造】按最终布局要求，准备5个独立的标签列表
        // a. 基础画像 (年龄/性别/职业)
        List<CustomerTagRelation> basicProfileTags = allTags.stream().filter(t ->
                t.getTagCategory().equals(TaggingConstants.CATEGORY_AGE) ||
                t.getTagCategory().equals(TaggingConstants.CATEGORY_GENDER) ||
                t.getTagCategory().equals(TaggingConstants.CATEGORY_OCCUPATION)
        ).collect(Collectors.toList());

        // b. 资产规模
        List<CustomerTagRelation> assetTags = allTags.stream().filter(t ->
                t.getTagCategory().equals(TaggingConstants.CATEGORY_ASSET)
        ).collect(Collectors.toList());

        // c. 投资风格
        List<CustomerTagRelation> styleTags = allTags.stream().filter(t ->
                t.getTagCategory().equals(TaggingConstants.CATEGORY_STYLE)
        ).collect(Collectors.toList());

        // d. 交易习惯 (近期R + 历史F) -> 内部排序
        List<String> tradingHabitOrder = List.of(TaggingConstants.CATEGORY_RECENCY, TaggingConstants.CATEGORY_FREQUENCY);
        List<CustomerTagRelation> tradingHabitTags = allTags.stream().filter(t ->
                tradingHabitOrder.contains(t.getTagCategory())
        ).sorted(Comparator.comparing(t -> tradingHabitOrder.indexOf(t.getTagCategory())))
         .collect(Collectors.toList());

        // e. 风险状况 (申报 + 实盘 + 诊断) -> 内部排序
        List<String> riskProfileOrder = List.of(TaggingConstants.CATEGORY_RISK_DECLARED, TaggingConstants.CATEGORY_RISK_ACTUAL, TaggingConstants.CATEGORY_RISK_DIAGNOSIS);
        List<CustomerTagRelation> riskProfileTags = allTags.stream().filter(t ->
                riskProfileOrder.contains(t.getTagCategory())
        ).sorted(Comparator.comparing(t -> riskProfileOrder.indexOf(t.getTagCategory())))
         .collect(Collectors.toList());

        // 3. 获取客户的盈亏与资产统计数据 (逻辑不变)
        ProfitLossVO profitLossVO = customerService.getProfitLossVO(id);

        // 4. 将所有数据放入 Model
        model.addAttribute("customer", customer);
        model.addAttribute("stats", profitLossVO);
        model.addAttribute("basicProfileTags", basicProfileTags);
        model.addAttribute("assetTags", assetTags);
        model.addAttribute("styleTags", styleTags);
        model.addAttribute("tradingHabitTags", tradingHabitTags);
        model.addAttribute("riskProfileTags", riskProfileTags);

        model.addAttribute("activeUri", "/customer/list");
        model.addAttribute("backUrl", buildBackUrl(name, idNumber, tagName, returnUrl));

        return "customer/detail";
    }


    /**
     * 【私有辅助方法】用于从其他链接跳转到客户列表查看详情时，可以顺利返回其他链接。用于构建带查询参数的返回URL。
     */
    private String buildBackUrl(String name, String idNumber, String tagName, String returnUrl) {
        // 1. 优先判断是否有从其他页面（如持仓、交易页）传来的完整返回地址
        if (StringUtils.hasText(returnUrl)) {
            // 如果有，直接使用，这是最高优先级
            return returnUrl;
        }

        // 2. 如果没有returnUrl，则说明是从客户管理页来的，我们构建返回到客户管理页的链接
        StringBuilder url = new StringBuilder("/customer/list?from=details"); // from参数只是为了让URL不为空
        if (StringUtils.hasText(name)) {
            url.append("&name=").append(name);
        }
        if (StringUtils.hasText(idNumber)) {
            url.append("&idNumber=").append(idNumber);
        }
        if (StringUtils.hasText(tagName)) {
            url.append("&tagName=").append(tagName);
        }
        return url.toString();
    }



    /**
     * 显示编辑客户的表单页面，并保留查询参数用于返回
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

        CustomerUpdateDTO customerUpdateDTO = new CustomerUpdateDTO();
        BeanUtils.copyProperties(customer, customerUpdateDTO);

        model.addAttribute("customerUpdateDTO", customerUpdateDTO);
        model.addAttribute("activeUri", "/customer/list");
        // 【关键】将查询参数拼接到返回URL中
        model.addAttribute("backUrl", buildBackUrl(name, idNumber, tagName));
        return "customer/edit";
    }



    /**
     * 显示基金信息列表页面, 增加分页和查询功能
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
        // 调用我们新创建的服务方法
        fundInfoService.getFundInfoPage(fundPage, fundCode, fundName, fundType, riskScore);

        // 分页导航栏的计算逻辑 (与客户管理保持一致)
        int startPage = 1, endPage = (int) fundPage.getPages();
        if (fundPage.getPages() > 5) {
            startPage = Math.max(1, (int)fundPage.getCurrent() - 2);
            endPage = Math.min((int)fundPage.getPages(), startPage + 4);
            if (endPage == fundPage.getPages()) {
                startPage = Math.max(1, endPage - 4);
            }
        }

        model.addAttribute("fundPage", fundPage);
        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);
        model.addAttribute("activeUri", "/fund/list");
        // 将查询参数传回给前端，用于表单回显和分页链接
        model.addAttribute("fundCode", fundCode);
        model.addAttribute("fundName", fundName);
        model.addAttribute("fundType", fundType);
        model.addAttribute("riskScore", riskScore);

        // 从所有基金中提取出不重复的基金类型，用于查询下拉框
        List<String> fundTypes = fundInfoService.list().stream()
                .map(FundInfo::getFundType)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        model.addAttribute("fundTypes", fundTypes);


        return "fund/list";
    }

    /**
     * 显示新增基金的表单页面
     */
    @GetMapping("/fund/add")
    public String showAddFundForm(Model model)
    {
        model.addAttribute("fundInfoDTO", new FundInfoDTO());
        model.addAttribute("activeUri", "/fund/list"); // 保持侧边栏在"基金管理"高亮
        return "fund/add";
    }

    // 后续所有其他模块的列表、新增、编辑页面，我们都在这里继续添加方法...

    /**
     * 显示客户持仓列表页面
     */
    @GetMapping("/holding/list")
    public String holdingList(
            Model model,
            @RequestParam(value = "page", defaultValue = "1") int pageNum,
            @RequestParam(value = "size", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String fundCode,
            @RequestParam(required = false, defaultValue = "lastUpdateDate") String sortField,
            // 持仓数据倒序显示最近更新过的
            @RequestParam(required = false, defaultValue = "desc") String sortOrder)
    {
        Page<CustomerHoldingVO> holdingPage = new Page<>(pageNum, pageSize);
        // 调用我们新创建的服务方法
        customerHoldingService.getHoldingPage(holdingPage, customerName,
                fundCode, sortField, sortOrder);

        // 分页导航栏的计算逻辑
        int startPage = 1, endPage = (int) holdingPage.getPages();
        if (holdingPage.getPages() > 5) {
            startPage = Math.max(1, (int)holdingPage.getCurrent() - 2);
            endPage = Math.min((int)holdingPage.getPages(), startPage + 4);
            if (endPage == holdingPage.getPages()) {
                startPage = Math.max(1, endPage - 4);
            }
        }

        model.addAttribute("holdingPage", holdingPage);
        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);
        model.addAttribute("activeUri", "/holding/list");
        // 将查询参数传回给前端，用于表单回显和分页
        model.addAttribute("customerName", customerName);
        model.addAttribute("fundCode", fundCode);
        // 【关键】将当前的排序状态传回给前端
        model.addAttribute("sortField", sortField);
        model.addAttribute("sortOrder", sortOrder);
        // 【关键】传递一个反转后的排序顺序，方便前端生成链接
        model.addAttribute("reversedSortOrder", "asc".equals(sortOrder) ? "desc" : "asc");


        return "holding/list";
    }


    /**
     * 显示交易流水列表页面
     */
    @GetMapping("/transaction/list")
    public String transactionList(
            Model model,
            @RequestParam(value = "page", defaultValue = "1") int pageNum,
            @RequestParam(value = "size", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String fundCode,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false, defaultValue = "transactionTime") String sortField,
            // 交易数据倒序显示最近发生的交易
            @RequestParam(required = false, defaultValue = "desc") String sortOrder)
    {

        Page<FundTransactionVO> transactionPage = new Page<>(pageNum, pageSize);
        // 调用我们新创建的服务方法
        fundTransactionService.getTransactionPage(transactionPage, customerName, fundCode, transactionType, sortField, sortOrder);

        // 分页导航栏的计算逻辑
        int startPage = 1, endPage = (int) transactionPage.getPages();
        if (transactionPage.getPages() > 5) {
            startPage = Math.max(1, (int)transactionPage.getCurrent() - 2);
            endPage = Math.min((int)transactionPage.getPages(), startPage + 4);
            if (endPage == transactionPage.getPages()) {
                startPage = Math.max(1, endPage - 4);
            }
        }

        model.addAttribute("transactionPage", transactionPage);
        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);
        model.addAttribute("activeUri", "/transaction/list");
        // 将查询参数传回给前端，用于表单回显和分页
        model.addAttribute("customerName", customerName);
        model.addAttribute("fundCode", fundCode);
        model.addAttribute("transactionType", transactionType);
        // 【关键】将当前的排序状态传回给前端
        model.addAttribute("sortField", sortField);
        model.addAttribute("sortOrder", sortOrder);
        // 【关键】传递一个反转后的排序顺序，方便前端生成链接
        model.addAttribute("reversedSortOrder", "asc".equals(sortOrder) ? "desc" : "asc");


        return "transaction/list";
    }


    /**
     * 【最终形态】显示风险评估列表页面，支持多维度查询
     */
    @GetMapping("/risk/list")
    public String riskList(
            Model model,
            @RequestParam(value = "page", defaultValue = "1") int pageNum,
            @RequestParam(value = "size", defaultValue = "10") int pageSize,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String riskLevel,
            // --- 【【【 新增的请求参数 】】】 ---
            @RequestParam(required = false) String actualRiskLevel,
            @RequestParam(required = false) String riskDiagnosis,
            @RequestParam(required = false, defaultValue = "customerId") String sortField,
            @RequestParam(required = false, defaultValue = "asc") String sortOrder)
    {
        Page<RiskAssessmentVO> assessmentPage = new Page<>(pageNum, pageSize);
        // 同时传递排序参数
        riskAssessmentService.getAssessmentPage(assessmentPage, customerName, riskLevel,
                actualRiskLevel, riskDiagnosis, sortField, sortOrder);

        // 分页导航栏的计算逻辑
        int startPage = 1, endPage = (int) assessmentPage.getPages();
        if (assessmentPage.getPages() > 5) {
            startPage = Math.max(1, (int)assessmentPage.getCurrent() - 2);
            endPage = Math.min((int)assessmentPage.getPages(), startPage + 4);
            if (endPage == assessmentPage.getPages()) {
                startPage = Math.max(1, endPage - 4);
            }
        }

        // --- 【【【 新增的数据准备 】】】 ---
        // a. 准备“风险诊断”下拉框的选项列表
        List<String> riskDiagnoses = List.of(
            TaggingConstants.LABEL_DIAGNOSIS_OVERWEIGHT,
            TaggingConstants.LABEL_DIAGNOSIS_MATCH,
            TaggingConstants.LABEL_DIAGNOSIS_UNDERWEIGHT
        );
        model.addAttribute("riskDiagnoses", riskDiagnoses);

        // b. 准备“申报/实盘风险”下拉框的选项列表 (逻辑不变)
        List<String> riskLevels = Arrays.stream(RiskLevelEnum.values())
                .map(RiskLevelEnum::getLevelName)
                .collect(Collectors.toList());
        model.addAttribute("riskLevels", riskLevels);

        // --- 将所有查询参数传回给前端，用于表单回显 ---
        model.addAttribute("assessmentPage", assessmentPage);
        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);
        model.addAttribute("activeUri", "/risk/list");
        model.addAttribute("customerName", customerName);
        model.addAttribute("riskLevel", riskLevel);
        model.addAttribute("actualRiskLevel", actualRiskLevel); // <-- 新增
        model.addAttribute("riskDiagnosis", riskDiagnosis);   // <-- 新增
        model.addAttribute("sortField", sortField);
        model.addAttribute("sortOrder", sortOrder);
        model.addAttribute("reversedSortOrder", "asc".equals(sortOrder) ? "desc" : "asc");

        return "risk/list";
    }


    /**
     * 【升级版】显示标签管理页面, 按类别分组展示
     */
    @GetMapping("/tag/list")
    public String tagList(Model model)
    {
        // 1. 调用服务，获取所有标签的统计数据
        List<TagVO> allTags = customerTagRelationService.getTagStats();

        // 2. 定义我们期望的“黄金排序规则”，确保页面下拉框顺序稳定
        List<String> categoryOrder = List.of(
            TaggingConstants.CATEGORY_AGE,
            TaggingConstants.CATEGORY_GENDER,
            TaggingConstants.CATEGORY_OCCUPATION,
            TaggingConstants.CATEGORY_STYLE,
            TaggingConstants.CATEGORY_ASSET,
            TaggingConstants.CATEGORY_RECENCY,
            TaggingConstants.CATEGORY_FREQUENCY,
            TaggingConstants.CATEGORY_RISK_DECLARED,
            TaggingConstants.CATEGORY_RISK_ACTUAL,
            TaggingConstants.CATEGORY_RISK_DIAGNOSIS
        );

        // 3. 将标签列表按预定义的类别顺序分组
        Map<String, List<TagVO>> groupedTags = categoryOrder.stream()
            .collect(Collectors.toMap(
                cat -> cat,
                cat -> allTags.stream()
                                   .filter(tag -> cat.equals(tag.getTagCategory()))
                                   .collect(Collectors.toList()),
                (e1, e2) -> e1,
                LinkedHashMap::new // 使用LinkedHashMap来保持插入顺序
            ));

        // --- 【【【 新增的核心改造逻辑 】】】 ---
        // a. 将 groupedTags 对象手动序列化为JSON字符串
        String groupedTagsJson = "{}"; // 提供一个安全的默认值
        try {
            groupedTagsJson = objectMapper.writeValueAsString(groupedTags);
        } catch (JsonProcessingException e) {
            // 在生产环境中，这里应该使用日志框架记录错误
            e.printStackTrace();
        }

        // b. 将原始Map对象（供下方th:each使用）和新的JSON字符串（供JS使用）都放入Model
        model.addAttribute("groupedTags", groupedTags);
        model.addAttribute("groupedTagsJson", groupedTagsJson); // <-- 新增
        model.addAttribute("activeUri", "/tag/list");

        return "tag/list";
    }


    /**
     * 【修正版】显示可视化画像仪表盘页面
     */
    @GetMapping("/visualization/dashboard")
    public String showDashboard(Model model) {
        model.addAttribute("activeUri", "/visualization/dashboard");
        // 【【【 关键修正 】】】
        // 确保这里返回的是正确的页面路径
        return "visualization/dashboard";
    }


    /**
     * 【最终性能版】显示客户盈亏排行榜页面（支持分页、搜索、多字段排序）
     */
    @GetMapping("/profitloss/list")
    public String profitLossList(Model model,
                                 @RequestParam(value = "page", defaultValue = "1") int pageNum,
                                 @RequestParam(value = "size", defaultValue = "10") int pageSize,
                                 // 确保这里能接收 customerName
                                 @RequestParam(required = false) String customerName,
                                 @RequestParam(required = false, defaultValue = "customerId") String sortField,
                                 @RequestParam(required = false, defaultValue = "asc") String sortOrder) {

        Page<ProfitLossVO> page = new Page<>(pageNum, pageSize);

        List<String> allowedSortFields = List.of("customerId", "totalMarketValue", "totalProfitLoss", "profitLossRate");
        String dbSortField = null;
        if (allowedSortFields.contains(sortField)) {
            dbSortField = sortField;
        }

        // 确保 customerName 被传递到服务层
        customerService.getProfitLossPage(page, customerName, dbSortField, sortOrder);

        int startPage = 1, endPage = (int) page.getPages();
        if (page.getPages() > 5) {
            startPage = Math.max(1, (int)page.getCurrent() - 2);
            endPage = Math.min((int)page.getPages(), startPage + 4);
            if (endPage == page.getPages()) {
                startPage = Math.max(1, endPage - 4);
            }
        }

        model.addAttribute("statsPage", page);
        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);
        model.addAttribute("activeUri", "/profitloss/list");
        // 确保 customerName 被传回前端，用于搜索框回显
        model.addAttribute("customerName", customerName);
        model.addAttribute("sortField", sortField);
        model.addAttribute("sortOrder", sortOrder);
        model.addAttribute("reversedSortOrder", "asc".equals(sortOrder) ? "desc" : "asc");

        return "profitloss/list";
    }


    /**
     * 【私有辅助方法】 用于在本身的链接里面查看详情了以后能够顺利带着查询后的参数和结果返回本身的链接
     */
    private String buildBackUrl(String name, String idNumber, String tagName) {
        StringBuilder url = new StringBuilder("/customer/list?from=details"); // from参数只是为了让URL不为空
        if (name != null && !name.isEmpty()) {
            url.append("&name=").append(name);
        }
        if (idNumber != null && !idNumber.isEmpty()) {
            url.append("&idNumber=").append(idNumber);
        }
        if (tagName != null && !tagName.isEmpty()) {
            url.append("&tagName=").append(tagName);
        }
        return url.toString();
    }
}