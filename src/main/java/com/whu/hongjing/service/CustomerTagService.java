package com.whu.hongjing.service;

import com.whu.hongjing.pojo.vo.CustomerTagVO;
import java.util.List;

/**
 * 客户标签计算服务
 */
public interface CustomerTagService {


    /**
     * 根据客户ID，计算并返回该客户的所有标签
     * @param customerId 客户ID
     * @return 标签列表
     */
    List<CustomerTagVO> getTagsByCustomerId(Long customerId);
}