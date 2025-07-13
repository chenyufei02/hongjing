// 文件: src/main/java/com/whu/hongjing/controller/AIController.java
package com.whu.hongjing.controller;

import com.whu.hongjing.pojo.entity.Customer;
import com.whu.hongjing.pojo.entity.CustomerHolding;
import com.whu.hongjing.pojo.entity.CustomerTagRelation;
import com.whu.hongjing.pojo.entity.FundInfo;
import com.whu.hongjing.pojo.vo.ApiResponseVO;
import com.whu.hongjing.pojo.vo.ProfitLossVO;
import com.whu.hongjing.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI助手", description = "提供基于大模型的智能分析与建议")
public class AIController {

    @Autowired private CustomerService customerService;
    @Autowired private AISuggestionService aiSuggestionService;
    @Autowired private CustomerTagRelationService customerTagRelationService;
    @Autowired private CustomerHoldingService customerHoldingService;
    @Autowired private FundInfoService fundInfoService;

    @PostMapping("/suggestion/{customerId}")
    @Operation(summary = "为指定客户生成营销建议")
    public ApiResponseVO<String> generateSuggestion(@PathVariable Long customerId) {
        Customer customer = customerService.getById(customerId);
        if (customer == null) {
            return ApiResponseVO.error("找不到该客户。");
        }

        try {
            // 1. 获取AI建议所需的所有原始数据
            ProfitLossVO profitLossVO = customerService.getProfitLossVO(customerId);
            List<CustomerTagRelation> tags = customerTagRelationService.lambdaQuery().eq(CustomerTagRelation::getCustomerId, customerId).list();
            List<CustomerHolding> holdings = customerHoldingService.listByCustomerId(customerId);
            Map<String, FundInfo> fundInfoMap = fundInfoService.list().stream().collect(Collectors.toMap(FundInfo::getFundCode, Function.identity()));

            // 2. 计算图表数据 (复用PageController中的逻辑)
            Map<String, BigDecimal> assetAllocationData = calculateAssetAllocation(holdings, fundInfoMap);


            // 3. 调用AI服务
            String suggestion = aiSuggestionService.getMarketingSuggestion( profitLossVO, tags, assetAllocationData);
            return ApiResponseVO.success("建议生成成功", suggestion);

        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponseVO.error("生成建议时发生后端错误：" + e.getMessage());
        }
    }

    // --- 以下为计算图表数据的辅助方法 (从PageController迁移并简化) ---

    private Map<String, BigDecimal> calculateAssetAllocation(List<CustomerHolding> holdings, Map<String, FundInfo> fundInfoMap) {
        if (holdings == null || holdings.isEmpty()) {
            return Map.of();
        }
        return holdings.stream()
                .filter(h -> fundInfoMap.get(h.getFundCode()) != null && h.getMarketValue() != null && fundInfoMap.get(h.getFundCode()).getFundType() != null)
                .collect(Collectors.groupingBy(h -> fundInfoMap.get(h.getFundCode()).getFundType(),
                        Collectors.reducing(BigDecimal.ZERO, CustomerHolding::getMarketValue, BigDecimal::add)));
    }

}