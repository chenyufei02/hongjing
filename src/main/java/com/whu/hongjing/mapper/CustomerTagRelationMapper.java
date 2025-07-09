package com.whu.hongjing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.hongjing.pojo.entity.CustomerTagRelation;
import org.apache.ibatis.annotations.Mapper;
import com.whu.hongjing.pojo.vo.TagVO;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface CustomerTagRelationMapper extends BaseMapper<CustomerTagRelation> {

    /**
     * 【修改】自定义SQL，增加tag_category的查询，并移除排序
     */
    @Select("SELECT tag_name as tagName, tag_category as tagCategory, COUNT(DISTINCT customer_id) as customerCount " +
            "FROM customer_tag_relation " +
            "GROUP BY tag_name, tag_category") // 按标签名和类别一同分组
    List<TagVO> selectTagStats();


}