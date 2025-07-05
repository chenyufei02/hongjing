package com.whu.hongjing.service;

import com.whu.hongjing.pojo.entity.Customer;
import com.whu.hongjing.pojo.entity.CustomerHolding;
import com.whu.hongjing.pojo.entity.CustomerTagRelation;

import java.util.List;

/**
 * 客户标签刷新服务
 * 负责统一计算和更新单个客户的所有画像标签
 */
public interface TagRefreshService {

    /**
     * 为指定客户刷新所有标签 (包含读、计算、写)
     * @param customerId 客户ID
     */
    void refreshTagsForCustomer(Long customerId);

    /**
     * 【新增】纯计算方法：根据客户实体，计算出他所有的标签，不涉及任何数据库写入
     * @param customer 客户实体对象
     * @return 该客户的所有新标签列表
     */
    List<CustomerTagRelation> calculateTagsForCustomer(Customer customer , List<CustomerHolding> holdings);

    /**
     * 【新增】原子化批量刷新：先清空所有标签，再批量插入所有新标签
     * @param allNewTags 包含所有客户的所有新标签的列表
     */
    void refreshAllTagsAtomically(List<CustomerTagRelation> allNewTags);
}