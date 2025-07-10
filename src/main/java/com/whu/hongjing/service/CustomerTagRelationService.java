package com.whu.hongjing.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.whu.hongjing.pojo.entity.CustomerTagRelation;
import com.whu.hongjing.pojo.vo.TagVO;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface CustomerTagRelationService extends IService<CustomerTagRelation> {

    /**
     * 【新增】统计每个标签的客户数量
     * @return 包含标签名和客户数的VO列表
     */
    List<TagVO> getTagStats();

     /**
     * 【新增】根据一个或多个标签（按类别）找出同时拥有这些标签的客户ID集合
     * @param tagsMap key为标签类别，value为标签名。只会处理value不为空的条目。
     * @return 符合条件的客户ID集合
     */
    Set<Long> findCustomerIdsByTags(Map<String, String> tagsMap);


}
