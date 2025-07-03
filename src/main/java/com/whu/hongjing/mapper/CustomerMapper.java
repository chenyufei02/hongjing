package com.whu.hongjing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.hongjing.pojo.entity.Customer;
import org.apache.ibatis.annotations.Mapper;

// 你要么保留 @Mapper，要么只保留启动类的 @MapperScan
@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {
    // 这里可以添加自定义SQL方法
}
