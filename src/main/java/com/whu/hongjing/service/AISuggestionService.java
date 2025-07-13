package com.whu.hongjing.service;

import com.whu.hongjing.pojo.entity.CustomerTagRelation;
import com.whu.hongjing.pojo.vo.ProfitLossVO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface AISuggestionService {

    /**
     * 【V2版】为指定客户生成营销建议
     * @param profitLossVO 客户的盈亏统计
     * @param tags 客户的所有标签
     * @param assetAllocationData 资产类别分布图数据
     * @return AI生成的营销话术建议
     */
    String getMarketingSuggestion(ProfitLossVO profitLossVO,
                                  List<CustomerTagRelation> tags,
                                  Map<String, BigDecimal> assetAllocationData);
}