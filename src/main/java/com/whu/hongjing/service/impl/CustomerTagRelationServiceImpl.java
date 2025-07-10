package com.whu.hongjing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whu.hongjing.mapper.CustomerTagRelationMapper;
import com.whu.hongjing.pojo.entity.CustomerTagRelation;
import com.whu.hongjing.service.CustomerTagRelationService;
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


        /**
     * 【【【 新增的、支持多标签组合查询的核心方法 】】】
     */
    @Override
    public Set<Long> findCustomerIdsByTags(Map<String, String> tagsMap) {
        // 过滤掉value为空的查询条件
        Map<String, String> activeFilters = tagsMap.entrySet().stream()
                .filter(entry -> StringUtils.hasText(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (activeFilters.isEmpty()) {
            return Collections.emptySet();
        }

        // 构建一个动态的查询
        QueryWrapper<CustomerTagRelation> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("customer_id");

        // 使用OR条件来拼接所有有效的查询
        queryWrapper.and(wrapper -> {
            for (Map.Entry<String, String> filter : activeFilters.entrySet()) {
                wrapper.or(orWrapper -> orWrapper
                        .eq("tag_category", filter.getKey())
                        .eq("tag_name", filter.getValue()));
            }
        });

        // 核心逻辑：分组后，HAVING子句确保客户拥有所有我们正在筛选的标签
        queryWrapper.groupBy("customer_id")
                .having("COUNT(DISTINCT tag_category) = {0}", activeFilters.size());

        List<CustomerTagRelation> relations = this.list(queryWrapper);

        return relations.stream()
                .map(CustomerTagRelation::getCustomerId)
                .collect(Collectors.toSet());
    }

}