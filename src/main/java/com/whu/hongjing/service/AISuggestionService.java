package com.whu.hongjing.service;

import com.whu.hongjing.pojo.entity.Customer;

public interface AISuggestionService {

    /**
     * 为指定客户生成营销建议
     * @param customer 要分析的客户对象
     * @return AI生成的营销话术建议
     */
    String getMarketingSuggestion(Customer customer);
}