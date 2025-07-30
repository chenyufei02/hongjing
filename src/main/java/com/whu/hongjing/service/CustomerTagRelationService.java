package com.whu.hongjing.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.whu.hongjing.pojo.entity.CustomerTagRelation;
import com.whu.hongjing.pojo.vo.TagVO;
import java.util.List;
import java.util.Map;

public interface CustomerTagRelationService extends IService<CustomerTagRelation> {

    /**
     * 统计每个标签的客户数量
     * @return 包含标签名和客户数的VO列表
     */
    List<TagVO> getTagStats();




    List<Long> findCustomerIdsByTags(List<String> tagNames);


    /**
     * 根据筛选条件，获取过滤后的标签统计数据
     * @param filters 一个Map，key为标签类别，value为标签名
     * @return 过滤后的标签统计VO列表
     */
    List<TagVO> getFilteredTagStats(Map<String, String> filters);



}
