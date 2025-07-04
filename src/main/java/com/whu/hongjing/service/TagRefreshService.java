package com.whu.hongjing.service;

/**
 * 客户标签刷新服务
 * 负责统一计算和更新单个客户的所有画像标签
 */
public interface TagRefreshService {

    /**
     * 为指定客户刷新所有标签
     * @param customerId 客户ID
     */
    void refreshTagsForCustomer(Long customerId);
}