package com.whu.hongjing.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.whu.hongjing.mapper.CustomerTagRelationMapper;
import com.whu.hongjing.pojo.entity.CustomerTagRelation;
import com.whu.hongjing.service.CustomerTagRelationService;
import org.springframework.stereotype.Service;
import com.whu.hongjing.pojo.vo.TagVO;

import java.util.Comparator;
import java.util.List;
import com.whu.hongjing.constants.TaggingConstants;

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

}