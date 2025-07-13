package com.whu.hongjing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.hongjing.pojo.entity.Customer;
import com.whu.hongjing.pojo.vo.ProfitLossVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

// ... (已有 import)

@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {
    // ... (已有的其他方法)


    Page<ProfitLossVO> getProfitLossPage(
            Page<ProfitLossVO> page, @Param("customerId") Long customerId, @Param("customerName") String customerName,
            @Param("sortField") String sortField, @Param("sortOrder") String sortOrder);


//    /**
//     * 【【【 新增的方法声明 】】】
//     * @param tagNames 标签名称列表, 对应XML中的 "tagNames"
//     * @param tagCount 标签数量, 对应XML中的 "tagCount"
//     * @return 符合条件的客户ID列表
//     */
//    List<Long> findCustomerIdsByTags(
//            @Param("tagNames") List<String> tagNames, @Param("tagCount") int tagCount);


    ProfitLossVO getProfitLossVOByCustomerId(@Param("customerId") Long customerId);


}
