package com.whu.hongjing.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.hongjing.constants.TaggingConstants;
import com.whu.hongjing.pojo.entity.Customer;
import com.whu.hongjing.pojo.vo.DashboardFilteredResultVO;
import com.whu.hongjing.pojo.vo.TagVO;
import com.whu.hongjing.service.CustomerService;
import com.whu.hongjing.service.CustomerTagRelationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "可视化画像数据", description = "为前端仪表盘提供图表数据")
public class DashboardController {

    @Autowired
    private CustomerTagRelationService customerTagRelationService;

    @Autowired
    private CustomerService customerService;

    @GetMapping("/all-stats")
    @Operation(summary = "获取所有标签类别的统计数据（用于页面初始化）")
    public Map<String, List<TagVO>> getAllStats() {
        List<TagVO> allTags = customerTagRelationService.getTagStats();
        Map<String, List<TagVO>> groupedTags = allTags.stream().collect(Collectors.groupingBy(TagVO::getTagCategory));
        List<String> categoryOrder = List.of(
            TaggingConstants.CATEGORY_AGE, TaggingConstants.CATEGORY_GENDER, TaggingConstants.CATEGORY_OCCUPATION,
            TaggingConstants.CATEGORY_STYLE, TaggingConstants.CATEGORY_ASSET, TaggingConstants.CATEGORY_RECENCY,
            TaggingConstants.CATEGORY_FREQUENCY, TaggingConstants.CATEGORY_RISK_DECLARED,
            TaggingConstants.CATEGORY_RISK_ACTUAL, TaggingConstants.CATEGORY_RISK_DIAGNOSIS
        );
        Map<String, List<TagVO>> sortedGroupedTags = new LinkedHashMap<>();
        for (String category : categoryOrder) {
            sortedGroupedTags.put(category, groupedTags.getOrDefault(category, Collections.emptyList()));
        }
        return sortedGroupedTags;
    }

    @PostMapping("/filtered-stats")
    @Operation(summary = "【重量级】根据筛选条件，获取图表数据和客户列表第一页")
    public DashboardFilteredResultVO getFilteredStats(@RequestBody Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        Map<String, String> filters = (Map<String, String>) payload.get("filters");

        DashboardFilteredResultVO result = new DashboardFilteredResultVO();

        // 1. 获取图表数据
        List<TagVO> filteredTags = customerTagRelationService.getFilteredTagStats(filters);
        Map<String, List<TagVO>> groupedChartData = filteredTags.stream().collect(Collectors.groupingBy(TagVO::getTagCategory));
        result.setChartData(groupedChartData);

        // 2. 获取客户列表分页数据
        Page<Customer> customerPage = getFilteredCustomerPage(filters, 1, 10);
        result.setCustomerPage(customerPage);

        return result;
    }

    /**
     * 【【【 新增的、轻量级API，专门用于客户列表分页查询 】】】
     */
    @PostMapping("/filtered-customers")
    @Operation(summary = "【轻量级】根据筛选条件，仅获取客户列表的指定页")
    public Page<Customer> getFilteredCustomers(@RequestBody Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        Map<String, String> filters = (Map<String, String>) payload.get("filters");
        int pageNum = (int) payload.getOrDefault("page", 1);
        int pageSize = (int) payload.getOrDefault("size", 10);

        return getFilteredCustomerPage(filters, pageNum, pageSize);
    }

    /**
     * 私有辅助方法，封装通用的“按标签筛选客户并分页”的逻辑
     */
    private Page<Customer> getFilteredCustomerPage(Map<String, String> filters, int pageNum, int pageSize) {
        List<String> activeFilterTags = filters.values().stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());

        Page<Customer> customerPage = new Page<>(pageNum, pageSize);
        // 调用我们之前已经写好的、强大的客户分页查询方法
        customerService.getCustomerPage(customerPage, null, null, null, String.join(",", activeFilterTags));
        return customerPage;
    }
}