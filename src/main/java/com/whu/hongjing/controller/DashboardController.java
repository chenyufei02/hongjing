package com.whu.hongjing.controller;

import com.whu.hongjing.constants.TaggingConstants;
import com.whu.hongjing.pojo.vo.TagVO;
import com.whu.hongjing.service.CustomerTagRelationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.ui.Model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "可视化画像数据", description = "为前端仪表盘提供图表数据")
public class DashboardController {

    @Autowired
    private CustomerTagRelationService customerTagRelationService;

@GetMapping("/all-stats")
    @Operation(summary = "获取所有标签类别的统计数据")
    public Map<String, List<TagVO>> getAllStats() {
        // 1. 从服务层获取所有标签及其类别的扁平列表
        List<TagVO> allTags = customerTagRelationService.getTagStats();

        // 2. 将列表按tagCategory分组为临时的Map
        Map<String, List<TagVO>> groupedTags = allTags.stream()
                .collect(Collectors.groupingBy(TagVO::getTagCategory));

        // 3. 定义我们期望的“黄金排序规则”
        List<String> categoryOrder = List.of(
            TaggingConstants.CATEGORY_RISK_DECLARED,
            TaggingConstants.CATEGORY_RISK_ACTUAL,
            TaggingConstants.CATEGORY_RISK_DIAGNOSIS,
            TaggingConstants.CATEGORY_ASSET,
            TaggingConstants.CATEGORY_STYLE,
            TaggingConstants.CATEGORY_RECENCY,
            TaggingConstants.CATEGORY_FREQUENCY,
            TaggingConstants.CATEGORY_AGE,
            TaggingConstants.CATEGORY_GENDER,
            TaggingConstants.CATEGORY_OCCUPATION
        );

        // 4. 【核心改造】根据我们的规则，创建一个有序的LinkedHashMap
        Map<String, List<TagVO>> sortedGroupedTags = new LinkedHashMap<>();
        for (String category : categoryOrder) {
            if (groupedTags.containsKey(category)) {
                sortedGroupedTags.put(category, groupedTags.get(category));
            }
        }
        return sortedGroupedTags;
    }



    /**
     * 【新增】显示可视化画像仪表盘页面
     */
    @GetMapping("/visualization/dashboard")
    public String showDashboard(Model model) {
        model.addAttribute("activeUri", "/visualization/dashboard");
        return "visualization/dashboard";
    }
}