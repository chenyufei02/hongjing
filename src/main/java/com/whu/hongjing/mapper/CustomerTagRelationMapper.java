package com.whu.hongjing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.hongjing.pojo.entity.CustomerTagRelation;
import org.apache.ibatis.annotations.Mapper;
import com.whu.hongjing.pojo.vo.TagVO;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CustomerTagRelationMapper extends BaseMapper<CustomerTagRelation> {


    List<TagVO> selectTagStats();



    /**
     * 根据指定的客户ID列表，统计这些客户的标签分布情况
     * @param customerIds 客户ID列表
     * @return 包含标签名、类别和客户数的VO列表
     */
    List<TagVO> selectTagStatsByCustomerIds(@Param("customerIds") List<Long> customerIds);


    List<Long> findCustomerIdsByTags(@Param("tagNames") List<String> tagNames, @Param("tagCount") int tagCount);


}