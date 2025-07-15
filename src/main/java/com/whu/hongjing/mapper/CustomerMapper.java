package com.whu.hongjing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.whu.hongjing.pojo.entity.Customer;
import com.whu.hongjing.pojo.vo.ProfitLossVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {


    Page<ProfitLossVO> getProfitLossPage(
            Page<ProfitLossVO> page, @Param("customerId") Long customerId, @Param("customerName") String customerName,
            @Param("sortField") String sortField, @Param("sortOrder") String sortOrder);


    ProfitLossVO getProfitLossVOByCustomerId(@Param("customerId") Long customerId);


}
