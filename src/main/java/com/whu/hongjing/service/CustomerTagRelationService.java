package com.whu.hongjing.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.whu.hongjing.pojo.entity.CustomerTagRelation;
import com.whu.hongjing.pojo.vo.TagVO;
import java.util.List;

public interface CustomerTagRelationService extends IService<CustomerTagRelation> {

    /**
     * 【新增】统计每个标签的客户数量
     * @return 包含标签名和客户数的VO列表
     */
    List<TagVO> getTagStats();


}
