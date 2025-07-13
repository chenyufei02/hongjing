package com.whu.hongjing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whu.hongjing.mapper.CustomerTagRelationMapper;
import com.whu.hongjing.pojo.entity.CustomerTagRelation;
import com.whu.hongjing.service.CustomerService;
import com.whu.hongjing.service.CustomerTagRelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.whu.hongjing.pojo.vo.TagVO;

import java.util.*;
import java.util.stream.Collectors;

import com.whu.hongjing.constants.TaggingConstants;
import org.springframework.util.StringUtils;

@Service
public class CustomerTagRelationServiceImpl extends ServiceImpl<CustomerTagRelationMapper, CustomerTagRelation> implements CustomerTagRelationService {


    @Override
    public List<TagVO> getTagStats() {
        // 步骤1：从数据库获取未排序的统计数据
        List<TagVO> unsortedTags = baseMapper.selectTagStats();

        // 步骤2：定义我们期望的“黄金排序规则”
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

        // 步骤3：根据我们的规则，对标签进行排序
        unsortedTags.sort(Comparator.comparing(tag -> {
            int index = categoryOrder.indexOf(tag.getTagCategory());
            return index == -1 ? Integer.MAX_VALUE : index; // 将未找到的类别排在最后
        }));
        return unsortedTags;
    }


    @Override
    public List<TagVO> getFilteredTagStats(Map<String, String> filters) {
        // 1. 从前端传来的Map中，提取出所有非空的标签名，形成一个List
        List<String> activeFilterTags = filters.values().stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());

        // 2. 如果没有任何有效的筛选条件，则返回全量统计数据
        if (activeFilterTags.isEmpty()) {
            return baseMapper.selectTagStats();
        }

        // 3. 【核心修改】调用我们唯一的、标准的findCustomerIdsByTags方法
        List<Long> customerIds = this.findCustomerIdsByTags(activeFilterTags);

        // 4. 如果没有客户符合所有筛选条件，返回空列表
        if (customerIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 5. 调用Mapper方法，只统计这些客户的标签分布
        return baseMapper.selectTagStatsByCustomerIds(customerIds);
    }


    // 【【【 3. 在这里，新增 findCustomerIdsByTags 方法的实现 】】】
    public List<Long> findCustomerIdsByTags(List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return Collections.emptyList();
        }
        // 直接调用本类的Mapper方法
        return baseMapper.findCustomerIdsByTags(tagNames, tagNames.size());
    }
}